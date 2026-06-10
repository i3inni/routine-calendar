package com.routinecalendar.server.push.service;
import com.routinecalendar.server.config.ApnsProperties;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * APNs 저수준 클라이언트. 토큰 기반(.p8) 인증으로 HTTP/2 푸시를 보낸다.
 * 키가 없으면(enabled=false) 실제 전송 대신 로그만 남겨, 키 없이도 흐름을 검증할 수 있다.
 */
@Slf4j
@Component
public class ApnsClient {

    public enum SendResult { SUCCESS, UNREGISTERED, FAILED }

    private static final Duration JWT_REFRESH = Duration.ofMinutes(50);

    private final ApnsProperties props;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    private volatile String cachedJwt;
    private volatile Instant jwtIssuedAt;
    private volatile PrivateKey privateKey;

    public ApnsClient(ApnsProperties props, ObjectMapper objectMapper) {
        this.props = props;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public SendResult send(String deviceToken, String title, String body) {
        if (!props.enabled()) {
            log.info("[APNs 비활성] '{}' / '{}' → token {}…", title, body, preview(deviceToken));
            return SendResult.SUCCESS;
        }
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "aps", Map.of(
                            "alert", Map.of("title", title, "body", body),
                            "sound", "default"
                    )
            ));

            // 1차: 설정된 환경으로 발송
            boolean sandbox = props.useSandbox();
            HttpResponse<String> res = postToApns(sandbox, deviceToken, payload);
            if (res.statusCode() == 200) {
                log.info("[APNs] 발송 성공(200) env={} token={}…", envName(sandbox), preview(deviceToken));
                return SendResult.SUCCESS;
            }

            // 환경 불일치(sandbox↔production)면 반대 환경으로 1회 재시도.
            // TestFlight/개발 빌드가 섞여 토큰 환경이 달라도 자동으로 맞춰 보낸다.
            if (isEnvironmentMismatch(res)) {
                log.info("[APNs] 환경 불일치({}) → {} 로 재시도 token={}…",
                        res.body().trim(), envName(!sandbox), preview(deviceToken));
                res = postToApns(!sandbox, deviceToken, payload);
                if (res.statusCode() == 200) {
                    log.info("[APNs] 발송 성공(200, 재시도) env={} token={}…", envName(!sandbox), preview(deviceToken));
                    return SendResult.SUCCESS;
                }
            }

            // 앱 삭제로 죽은 토큰만 폐기. (환경/토픽 문제는 토큰 유지)
            if (res.statusCode() == 410 || res.body().contains("Unregistered")) {
                log.info("APNs 폐기 토큰(앱 삭제 추정) {}…: {}", preview(deviceToken), res.body());
                return SendResult.UNREGISTERED;
            }
            log.warn("APNs 실패(토큰 유지) status={} body={}", res.statusCode(), res.body());
            return SendResult.FAILED;
        } catch (Exception e) {
            log.warn("APNs 전송 예외", e);
            return SendResult.FAILED;
        }
    }

    /** 지정한 환경(sandbox/production)으로 1회 발송. */
    private HttpResponse<String> postToApns(boolean sandbox, String deviceToken, String payload) throws Exception {
        String base = sandbox ? "https://api.sandbox.push.apple.com" : "https://api.push.apple.com";
        log.info("[APNs] 발송 시도 env={} topic={} token={}…", envName(sandbox), props.bundleId(), preview(deviceToken));
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(base + "/3/device/" + deviceToken))
                .header("authorization", "bearer " + providerToken())
                .header("apns-topic", props.bundleId())
                .header("apns-push-type", "alert")
                .header("apns-priority", "10")
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    /** sandbox↔production 토큰 환경 불일치 응답인지. (반대 환경 재시도 트리거) */
    private boolean isEnvironmentMismatch(HttpResponse<String> res) {
        String b = res.body();
        return b != null && (b.contains("BadEnvironmentKeyInToken") || b.contains("BadDeviceToken"));
    }

    private static String envName(boolean sandbox) {
        return sandbox ? "sandbox" : "production";
    }

    /** 프로바이더 인증 JWT(ES256). 최대 1시간 유효 → 50분마다 갱신 후 캐시. */
    private synchronized String providerToken() throws Exception {
        if (cachedJwt == null || jwtIssuedAt.isBefore(Instant.now().minus(JWT_REFRESH))) {
            cachedJwt = Jwts.builder()
                    .header().keyId(props.keyId()).and()
                    .issuer(props.teamId())
                    .issuedAt(Date.from(Instant.now()))
                    .signWith(loadPrivateKey(), Jwts.SIG.ES256)
                    .compact();
            jwtIssuedAt = Instant.now();
        }
        return cachedJwt;
    }

    private synchronized PrivateKey loadPrivateKey() throws Exception {
        if (privateKey == null) {
            String pem = props.privateKey()
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] der = Base64.getDecoder().decode(pem);
            privateKey = KeyFactory.getInstance("EC")
                    .generatePrivate(new PKCS8EncodedKeySpec(der));
        }
        return privateKey;
    }

    private String preview(String token) {
        return token.length() <= 8 ? token : token.substring(0, 8);
    }
}
