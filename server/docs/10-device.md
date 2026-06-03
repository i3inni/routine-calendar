# 10 — device 도메인 (APNs 토큰 관리)

> [← 09 summary 도메인](09-summary.md) · [목차](README.md) · 다음: [11 push 도메인 →](11-push-apns.md)

대상 파일: `device/DeviceToken.java`, `Platform.java`, `DeviceTokenRepository.java`, `DeviceTokenService.java`, `DeviceTokenController.java`, `DeviceTokenDtos.java`

APNs(애플 푸시) 발송 대상 토큰을 관리한다. 실제 발송은 [11 push](11-push-apns.md)가 담당하고, 여기선 "어디로 보낼지"를 저장.

---

## `DeviceToken.java`

```java
@Entity @Table(name = "device_tokens")
public class DeviceToken {
    @ManyToOne(LAZY) user;
    @Column(nullable=false, unique=true, length=255) private String token;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=10) private Platform platform;
    @UpdateTimestamp updatedAt;
    public DeviceToken(User user, String token, Platform platform) { ... }
    public void reassign(User user, Platform platform) { this.user = user; this.platform = platform; }
}
```
- `token`은 **unique** — 한 기기 토큰은 한 행. 한 유저가 여러 기기 가능(다대일).
- `@Enumerated(EnumType.STRING)`: platform을 문자열로 저장([07 friend](07-friend.md)에서 STRING 이유 설명).
- **`reassign`**: **같은 토큰이 다른 계정에 재등록**될 때(기기 양도/재로그인) 소유자만 갈아끼움. 새 행 만들지 않음. 상태 변경이라 dirty checking으로 UPDATE.

### `Platform.java`
```java
public enum Platform { IOS }
```
- 지금은 iOS만. 안드로이드 추가 대비 enum으로 둠(확장성).

---

## `DeviceTokenRepository.java`

```java
    Optional<DeviceToken> findByToken(String token);  // upsert용
    List<DeviceToken> findByUser(User user);           // 발송 시 유저의 모든 기기
```
- `findByToken`: upsert 분기(있으면 reassign).
- `findByUser`: 한 유저의 모든 기기로 발송할 때([11 PushService](11-push-apns.md)). DB의 `idx_device_user` 인덱스로 가속.

---

## `DeviceTokenService.java`

```java
    @Transactional
    public void register(Long meId, String token, Platform platform) {
        User me = userRepository.findById(meId).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        Platform p = platform != null ? platform : Platform.IOS;
        deviceTokenRepository.findByToken(token).ifPresentOrElse(
                existing -> existing.reassign(me, p),          // 있으면 소유자 갱신(자동 UPDATE)
                () -> deviceTokenRepository.save(new DeviceToken(me, token, p))  // 없으면 새로 INSERT
        );
    }
```
- **upsert**: `ifPresentOrElse(있을때, 없을때)`. 같은 토큰이면 reassign, 없으면 insert.
  - 같은 토큰이 기기 재로그인으로 다른 계정에 붙는 상황을 자연스럽게 처리(중복 행 방지).
- platform 생략 시 IOS 기본.

---

## `DeviceTokenController.java`

```java
    @PostMapping("/me/device-token")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void register(@AuthenticationPrincipal Long meId, @Valid @RequestBody RegisterRequest request) {
        deviceTokenService.register(meId, request.token(), request.platform());
    }
```
- iOS `AppDelegate`가 APNs 토큰을 받으면 이 엔드포인트로 등록. 204.

### `DeviceTokenDtos.java`
```java
    public record RegisterRequest(@NotBlank String token, Platform platform) {}
```
- token 필수(`@NotBlank`), platform은 옵셔널.

---

> 다음: [11 push 도메인 →](11-push-apns.md)
