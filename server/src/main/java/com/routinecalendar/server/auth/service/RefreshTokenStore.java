package com.routinecalendar.server.auth.service;

import com.routinecalendar.server.config.JwtProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Base64;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 유효한 refresh 토큰을 Redis에 보관한다(화이트리스트).
 *
 * <p>왜 필요한가: JWT는 무상태라 서명만 맞으면 만료 전까지 계속 통한다.
 * 즉 로그아웃/계정삭제를 해도 이미 발급된 토큰을 막을 방법이 없다.
 * "서버가 기억하는 토큰만 인정"하게 만들어 무효화 지점을 되찾는다.
 *
 * <p>키 설계: {@code refresh:{userId}} — 사용자당 1개. 한 계정 한 세션 정책.
 * 새로 발급하면 덮어쓰므로 이전 토큰은 그 즉시 무효(= 회전).
 * 다기기를 지원하려면 키에 deviceId를 붙여 확장한다.
 *
 * <p>값은 토큰 원문이 아니라 SHA-256 해시로 저장한다.
 * Redis 덤프가 유출돼도 그것만으로는 로그인할 수 없게 하기 위함(비밀번호를 해시로 저장하는 것과 같은 이유).
 *
 * <p>TTL은 토큰 만료와 동일하게 준다. 만료된 키를 따로 청소하는 배치가 필요 없다.
 */
@Component
public class RefreshTokenStore {

    private static final String KEY_PREFIX = "refresh:";

    private final StringRedisTemplate redis;
    private final Duration ttl;

    public RefreshTokenStore(StringRedisTemplate redis, JwtProperties jwtProperties) {
        this.redis = redis;
        this.ttl = Duration.ofSeconds(jwtProperties.refreshTokenValidity());
    }

    /** 이 사용자의 유효한 refresh 토큰을 이것 하나로 지정한다(기존 것은 무효화). */
    public void save(Long userId, String refreshToken) {
        redis.opsForValue().set(key(userId), hash(refreshToken), ttl);
    }

    /** 서버가 기억하는 토큰과 같은지 확인한다. 기록이 없으면(로그아웃/만료) false. */
    public boolean matches(Long userId, String refreshToken) {
        String stored = redis.opsForValue().get(key(userId));
        return stored != null && MessageDigest.isEqual(
                stored.getBytes(StandardCharsets.UTF_8),
                hash(refreshToken).getBytes(StandardCharsets.UTF_8));
    }

    /** 로그아웃/계정삭제. 이후 이 사용자의 모든 refresh 토큰은 통하지 않는다. */
    public void delete(Long userId) {
        redis.delete(key(userId));
    }

    private String key(Long userId) {
        return KEY_PREFIX + userId;
    }

    private String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 미지원", e); // 표준 JVM에선 발생하지 않음
        }
    }
}
