package com.routinecalendar.server.security;

import com.routinecalendar.server.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

/**
 * 우리 서버가 발급/검증하는 JWT. (카카오 토큰과는 별개)
 * access: 짧게(1h), refresh: 길게(30d). type 클레임으로 둘을 구분한다.
 */
@Component
public class JwtTokenProvider {

    private static final String CLAIM_TYPE = "type";
    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_REFRESH = "refresh";

    private final SecretKey key;
    private final long accessValidity;
    private final long refreshValidity;

    public JwtTokenProvider(JwtProperties props) {
        this.key = Keys.hmacShaKeyFor(props.secret().getBytes(StandardCharsets.UTF_8));
        this.accessValidity = props.accessTokenValidity();
        this.refreshValidity = props.refreshTokenValidity();
    }

    public String createAccessToken(Long userId) {
        return create(userId, TYPE_ACCESS, accessValidity);
    }

    public String createRefreshToken(Long userId) {
        return create(userId, TYPE_REFRESH, refreshValidity);
    }

    private String create(Long userId, String type, long validitySeconds) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(CLAIM_TYPE, type)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(validitySeconds)))
                .signWith(key)
                .compact();
    }

    /** access 토큰을 검증하고 userId를 돌려준다. 유효하지 않으면 예외. */
    public Long parseAccessToken(String token) {
        Claims claims = parse(token);
        if (!TYPE_ACCESS.equals(claims.get(CLAIM_TYPE, String.class))) {
            throw new JwtException("access 토큰이 아닙니다");
        }
        return Long.valueOf(claims.getSubject());
    }

    /** refresh 토큰을 검증하고 userId를 돌려준다. 유효하지 않으면 예외. */
    public Long parseRefreshToken(String token) {
        Claims claims = parse(token);
        if (!TYPE_REFRESH.equals(claims.get(CLAIM_TYPE, String.class))) {
            throw new JwtException("refresh 토큰이 아닙니다");
        }
        return Long.valueOf(claims.getSubject());
    }

    private Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
