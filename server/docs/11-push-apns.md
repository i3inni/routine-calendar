# 11 — push 도메인 (APNs 푸시)

> [← 10 device 도메인](10-device.md) · [목차](README.md) · 다음: [12 DB 스키마 →](12-database-schema.md)

대상 파일: `push/ApnsProperties.java`, `PushEventListener.java`, `PushService.java`, `ApnsClient.java`

**설계의 백미**: "콕/친구요청 발생 → 상대에게 푸시"를 **도메인 이벤트 + AFTER_COMMIT + @Async** 로 본 트랜잭션과 분리한다. 면접에서 강하게 어필할 수 있는 부분.

---

## `ApnsProperties.java`

```java
@ConfigurationProperties(prefix = "app.apns")
public record ApnsProperties(boolean enabled, boolean useSandbox, String teamId, String keyId, String bundleId, String privateKey) {}
```
- APNs 설정 묶음([02 설정 레이어](02-config-layer.md) 패턴). `enabled=false`면 발송 안 하고 로그만.

---

## `PushEventListener.java` — 이벤트 → 푸시 (분리의 핵심)

```java
@Component
public class PushEventListener {
    @Async
    @TransactionalEventListener
    public void onPoke(PokeEvent event) {
        pushService.sendToUser(event.toUserId(), event.fromNickname() + "님이 콕 찔렀어요", "함께 루틴 해요");
    }
    @Async
    @TransactionalEventListener
    public void onFriendRequested(FriendRequestedEvent event) {
        pushService.sendToUser(event.toUserId(), "새 친구 요청", event.fromNickname() + "님이 친구 요청을 보냈어요");
    }
}
```

### `@TransactionalEventListener` (기본 단계 = AFTER_COMMIT)
- `publishEvent`를 호출한 트랜잭션이 **성공적으로 커밋된 뒤에만** 이 리스너가 실행된다.
- 효과: 친구 요청 저장이 롤백되면 푸시도 안 나감 → **"DB엔 없는데 푸시만 가는" 모순 방지**.
- 비교: 일반 `@EventListener`는 커밋 전 동기 실행이라 이런 보장이 없음.

### `@Async`
- 별도 스레드풀에서 실행([02 AsyncConfig](02-config-layer.md)의 `@EnableAsync` 필요) → APNs 네트워크 호출이 **요청 응답을 막지 않음**. 푸시가 느리거나 실패해도 사용자는 이미 빠른 응답을 받음.

### 두 효과의 조합
> **"커밋된 사실에 대해서만, 응답과 무관하게 백그라운드로"** 알림. 트랜잭션·응답성·정합성을 모두 챙김.

이벤트 발행처: [07 friend](07-friend.md)의 `sendRequest`, [08 poke](08-poke.md)의 `poke`.

---

## `PushService.java` — 유저의 모든 기기로 발송 + 토큰 정리

```java
    @Transactional
    public void sendToUser(Long userId, String title, String body) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) return;
        List<DeviceToken> tokens = deviceTokenRepository.findByUser(user);
        if (tokens.isEmpty()) { log.info("등록된 기기 없음 — userId={}", userId); return; }
        for (DeviceToken dt : tokens) {
            SendResult result = apnsClient.send(dt.getToken(), title, body);
            if (result == SendResult.UNREGISTERED) deviceTokenRepository.delete(dt);
        }
    }
```
- 유저의 **모든 기기 토큰**으로 발송(멀티 디바이스, [10 device](10-device.md)의 `findByUser`).
- **UNREGISTERED(410/만료) 토큰은 즉시 삭제** → 죽은 토큰을 계속 들고 있지 않게 자가 정리.
- `@Transactional`: 토큰 삭제가 DB 작업이라 필요. 이 메서드는 비동기 스레드에서 **새 트랜잭션**으로 실행(원래 요청 트랜잭션은 이미 커밋됨).

---

## `ApnsClient.java` — 저수준 APNs HTTP/2 클라이언트

```java
@Slf4j @Component
public class ApnsClient {
    public enum SendResult { SUCCESS, UNREGISTERED, FAILED }
    private static final Duration JWT_REFRESH = Duration.ofMinutes(50);
    private volatile String cachedJwt;
    private volatile Instant jwtIssuedAt;
    private volatile PrivateKey privateKey;
    public ApnsClient(ApnsProperties props, ObjectMapper objectMapper) {
        this.httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_2).connectTimeout(Duration.ofSeconds(10)).build();
    }
```
- **`java.net.http.HttpClient`**: JDK 내장 HTTP 클라이언트. **APNs는 HTTP/2 필수**라 버전 명시.
- **`volatile`**: 여러 스레드(@Async 풀)가 캐시 필드를 공유하므로 **메모리 가시성** 보장(한 스레드가 쓴 값을 다른 스레드가 즉시 봄).
- `ObjectMapper`: Jackson JSON 직렬화기(스프링이 빈 제공). 페이로드 생성에 사용.

```java
    public SendResult send(String deviceToken, String title, String body) {
        if (!props.enabled()) {
            log.info("[APNs 비활성] '{}' / '{}' → token {}…", title, body, preview(deviceToken));
            return SendResult.SUCCESS;
        }
```
- **enabled=false면 실제 발송 없이 로그만** 찍고 성공 반환 → 키 없이 전체 흐름(이벤트→리스너→발송)을 검증 가능. (개발 단계 핵심 장치)

```java
        String payload = objectMapper.writeValueAsString(Map.of(
                "aps", Map.of("alert", Map.of("title", title, "body", body), "sound", "default")));
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + "/3/device/" + deviceToken))
                .header("authorization", "bearer " + providerToken())
                .header("apns-topic", props.bundleId())
                .header("apns-push-type", "alert")
                .header("apns-priority", "10")
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> res = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
```
- APNs 표준 페이로드: `{"aps":{"alert":{"title","body"},"sound":"default"}}`.
- 엔드포인트 `/3/device/{토큰}`, 헤더 `apns-topic`=앱 번들ID, `apns-push-type=alert`, `apns-priority=10`(즉시).
- 인증은 **프로바이더 JWT**(아래).

```java
        if (res.statusCode() == 200) return SendResult.SUCCESS;
        if (res.statusCode() == 410 || res.body().contains("Unregistered") || res.body().contains("BadDeviceToken")) {
            log.info("APNs 만료 토큰 {}…: {}", preview(deviceToken), res.body());
            return SendResult.UNREGISTERED;   // → PushService가 토큰 삭제
        }
        log.warn("APNs 실패 status={} body={}", res.statusCode(), res.body());
        return SendResult.FAILED;
    } catch (Exception e) { log.warn("APNs 전송 예외", e); return SendResult.FAILED; }
```
- 200=성공, 410/Unregistered/BadDeviceToken=죽은 토큰(삭제 대상), 그 외=실패.
- **푸시 실패가 예외로 위로 전파되지 않게** catch로 흡수(비동기라 응답엔 영향 없지만 안전).

```java
    private String baseUrl() { return props.useSandbox() ? "https://api.sandbox.push.apple.com" : "https://api.push.apple.com"; }
```
- 개발 빌드(sandbox)와 운영 빌드의 APNs 서버가 다름 → 설정으로 전환.

```java
    private synchronized String providerToken() throws Exception {
        if (cachedJwt == null || jwtIssuedAt.isBefore(Instant.now().minus(JWT_REFRESH))) {
            cachedJwt = Jwts.builder()
                    .header().keyId(props.keyId()).and()    // 헤더 kid = 키 ID
                    .issuer(props.teamId())                  // iss = 팀 ID
                    .issuedAt(Date.from(Instant.now()))
                    .signWith(loadPrivateKey(), Jwts.SIG.ES256)  // ES256 서명(.p8 EC 키)
                    .compact();
            jwtIssuedAt = Instant.now();
        }
        return cachedJwt;
    }
```
- **APNs 토큰 기반 인증**: `.p8` 키로 서명한 JWT를 인증 헤더로 사용.
- 우리 서비스 JWT([03](03-security-jwt.md))는 HS256(대칭키)였지만, APNs JWT는 **ES256(타원곡선 비대칭키)** 알고리즘.
- APNs JWT는 최대 1시간 유효 → **50분마다만 새로 만들고 캐시**(`cachedJwt`). 매 발송마다 서명하면 낭비.
- `synchronized`: 여러 스레드가 동시에 캐시 갱신하지 않도록 직렬화.

```java
    private synchronized PrivateKey loadPrivateKey() throws Exception {
        if (privateKey == null) {
            String pem = props.privateKey()
                    .replace("-----BEGIN PRIVATE KEY-----", "").replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] der = Base64.getDecoder().decode(pem);
            privateKey = KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(der));
        }
        return privateKey;
    }
    private String preview(String token) { return token.length() <= 8 ? token : token.substring(0, 8); }
```
- `.p8` PEM 텍스트에서 헤더/푸터/공백 제거 → Base64 디코드 → **PKCS#8 EC 개인키 객체**로 로드. 한 번만 파싱해 캐시.
- `preview`: 로그에 토큰 전체를 안 찍고 앞 8자만(민감정보 노출 최소화).

---

> 다음: [12 DB 스키마 →](12-database-schema.md)
