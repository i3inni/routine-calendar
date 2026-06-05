package com.routinecalendar.server.auth.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.routinecalendar.server.common.error.BusinessException;
import com.routinecalendar.server.common.error.ErrorCode;
import com.routinecalendar.server.config.AppleProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 애플이 발급한 신원토큰(identity token, JWT)을 검증한다.
 * - 애플 공개키(JWKS)를 받아 토큰 서명을 검증 (kid로 키 선택)
 * - iss == https://appleid.apple.com, aud == 우리 앱 번들ID, 만료 확인
 * - 통과 시 sub(애플의 안정적 사용자 식별자)를 돌려준다.
 */
@Slf4j
@Component
public class AppleTokenVerifier {

    private static final String JWKS_URL = "https://appleid.apple.com/auth/keys";
    private static final String ISSUER = "https://appleid.apple.com";
    private static final Duration KEY_TTL = Duration.ofHours(6);

    private final RestClient restClient = RestClient.create();
    private final ObjectMapper objectMapper;
    private final AppleProperties props;

    private volatile Map<String, PublicKey> keyCache = new HashMap<>();
    private volatile Instant keyCacheAt = Instant.EPOCH;

    public AppleTokenVerifier(ObjectMapper objectMapper, AppleProperties props) {
        this.objectMapper = objectMapper;
        this.props = props;
    }

    /** 신원토큰을 검증하고 애플 sub를 반환. 실패하면 INVALID_APPLE_TOKEN. */
    public String verifyAndGetSub(String identityToken) {
        try {
            Claims claims = Jwts.parser()
                    .keyLocator(header -> resolveKey((String) header.get("kid")))
                    .build()
                    .parseSignedClaims(identityToken)
                    .getPayload();

            if (!ISSUER.equals(claims.getIssuer())) {
                throw new IllegalStateException("iss 불일치: " + claims.getIssuer());
            }
            if (claims.getAudience() == null || !claims.getAudience().contains(props.clientId())) {
                throw new IllegalStateException("aud 불일치: " + claims.getAudience());
            }
            return claims.getSubject();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("애플 토큰 검증 실패", e);
            throw new BusinessException(ErrorCode.INVALID_APPLE_TOKEN);
        }
    }

    /** kid에 맞는 애플 공개키. 캐시에 없거나 만료면 JWKS를 새로 받는다. */
    private PublicKey resolveKey(String kid) {
        PublicKey key = keyCache.get(kid);
        if (key == null || keyCacheAt.isBefore(Instant.now().minus(KEY_TTL))) {
            refreshKeys();
            key = keyCache.get(kid);
        }
        if (key == null) {
            throw new BusinessException(ErrorCode.INVALID_APPLE_TOKEN);
        }
        return key;
    }

    private synchronized void refreshKeys() {
        try {
            String json = restClient.get().uri(JWKS_URL).retrieve().body(String.class);
            JsonNode keys = objectMapper.readTree(json).get("keys");
            Map<String, PublicKey> map = new HashMap<>();
            for (JsonNode jwk : keys) {
                String kid = jwk.get("kid").asText();
                BigInteger n = new BigInteger(1, Base64.getUrlDecoder().decode(jwk.get("n").asText()));
                BigInteger e = new BigInteger(1, Base64.getUrlDecoder().decode(jwk.get("e").asText()));
                PublicKey pk = KeyFactory.getInstance("RSA")
                        .generatePublic(new RSAPublicKeySpec(n, e));
                map.put(kid, pk);
            }
            keyCache = map;
            keyCacheAt = Instant.now();
        } catch (Exception e) {
            log.warn("애플 JWKS 갱신 실패", e);
        }
    }
}
