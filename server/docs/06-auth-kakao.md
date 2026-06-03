# 06 — auth 도메인 (카카오 로그인 + JWT)

> [← 05 user 도메인](05-user.md) · [목차](README.md) · 다음: [07 friend 도메인 →](07-friend.md)

대상 파일: `auth/AuthDtos.java`, `KakaoUserResponse.java`, `KakaoApiClient.java`, `AuthService.java`, `AuthController.java`

모바일 카카오 로그인은 웹의 OAuth 리다이렉트가 아니라 **토큰 교환** 방식:
**앱이 카카오 SDK로 액세스토큰 획득 → 서버로 전송 → 서버가 카카오에 그 토큰으로 내 정보 조회(검증 겸) → 우리 JWT 발급.**

---

## `AuthDtos.java` — 요청/응답 묶음

```java
public final class AuthDtos {
    private AuthDtos() {}
    public record KakaoLoginRequest(@NotBlank String kakaoAccessToken) {}
    public record RefreshRequest(@NotBlank String refreshToken) {}
    public record DevLoginRequest(Long kakaoId, String nickname) {}
    public record AuthResponse(String accessToken, String refreshToken, UserResponse user) {}
}
```
- 관련 DTO를 한 파일에 중첩 record로 묶음(`AuthDtos.KakaoLoginRequest`).
- `final` 클래스 + `private` 생성자: 인스턴스화 못 하는 **순수 묶음 네임스페이스**.
- **`@NotBlank`**(Bean Validation): null/빈문자/공백만 있으면 검증 실패. 컨트롤러의 `@Valid`와 짝.
- `AuthResponse`: 로그인 결과 = access + refresh + 내 정보.

---

## `KakaoUserResponse.java` — 카카오 응답 매핑

```java
@JsonIgnoreProperties(ignoreUnknown = true)
public record KakaoUserResponse(Long id, @JsonProperty("kakao_account") KakaoAccount kakaoAccount) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record KakaoAccount(Profile profile) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Profile(String nickname, @JsonProperty("profile_image_url") String profileImageUrl) {}

    public String nickname() {
        return kakaoAccount != null && kakaoAccount.profile() != null ? kakaoAccount.profile().nickname() : null;
    }
    public String profileImageUrl() { /* 동일 패턴 */ }
}
```
- 카카오 `GET /v2/user/me` 응답 JSON을 자바 객체로 역직렬화하기 위한 매핑.
- **`@JsonIgnoreProperties(ignoreUnknown = true)`**(Jackson): 응답에 우리가 정의 안 한 필드가 있어도 **무시**(에러 X). 카카오 응답은 거대하니 필요한 것만.
- **`@JsonProperty("kakao_account")`**: JSON 스네이크케이스 키를 자바 카멜케이스 필드에 매핑.
- 중첩 구조(`kakao_account.profile.nickname`)를 그대로 중첩 record로 표현하고, 편의 메서드 `nickname()`/`profileImageUrl()`로 깊은 곳을 **null 안전하게** 평탄화.

---

## `KakaoApiClient.java` — 카카오 호출 + 토큰 검증

```java
@Component
public class KakaoApiClient {
    private final RestClient restClient;
    private final String userInfoUri;
    public KakaoApiClient(KakaoProperties props) {
        this.restClient = RestClient.create();
        this.userInfoUri = props.userInfoUri();
    }
```
- **`RestClient`**: 스프링 6.1+의 동기 HTTP 클라이언트(구식 `RestTemplate`의 현대적 후계). 외부 API 호출용.
- URI는 설정(`app.kakao.user-info-uri`)에서 주입.

```java
    public KakaoUserResponse fetchUser(String kakaoAccessToken) {
        return restClient.get()
                .uri(userInfoUri)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + kakaoAccessToken)
                .retrieve()
                .onStatus(status -> status.value() == 401, (req, res) -> {
                    throw new BusinessException(ErrorCode.INVALID_KAKAO_TOKEN);
                })
                .body(KakaoUserResponse.class);
    }
```
- 클라이언트가 준 **카카오 액세스토큰을 그대로 헤더에 실어** 카카오에 내 정보 요청.
- **이 호출 자체가 토큰 검증**: 토큰이 틀리면 카카오가 401 → `onStatus`로 잡아 `INVALID_KAKAO_TOKEN`으로 변환. (서버가 카카오 시크릿을 들고 별도 검증할 필요 없음)
- `.body(KakaoUserResponse.class)`: 응답 JSON을 그 타입으로 역직렬화해 반환.

---

## `AuthService.java` — 로그인/갱신 핵심 로직

생성자 주입: `KakaoApiClient`, `UserService`, `UserRepository`, `JwtTokenProvider`, `AuthProperties`.

### (1) 카카오 로그인
```java
    public AuthResponse kakaoLogin(KakaoLoginRequest request) {
        KakaoUserResponse kakaoUser = kakaoApiClient.fetchUser(request.kakaoAccessToken());
        User user = userService.getOrCreateByKakao(kakaoUser.id(), kakaoUser.nickname(), kakaoUser.profileImageUrl());
        return issueTokens(user);
    }
```
- 흐름: 카카오로 사용자 확인 → 우리 DB에 조회/생성([05 UserService](05-user.md)) → 우리 JWT 발급. 세 단계가 명확.
- 이 메서드엔 `@Transactional`이 없지만 `getOrCreateByKakao`에 걸려 있어 가입 저장은 트랜잭션 보장.

### (2) 자동 로그인 / 토큰 갱신 (refresh rotation)
```java
    public AuthResponse refresh(RefreshRequest request) {
        Long userId;
        try {
            userId = tokenProvider.parseRefreshToken(request.refreshToken());
        } catch (JwtException e) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));
        return issueTokens(user);
    }
```
- refresh 토큰 검증 → userId 복원 → **access+refresh를 새로 발급(회전)**.
- **토큰 회전(rotation)**: 갱신할 때마다 refresh도 새로 줌 → 활성 사용자는 계속 유효한 refresh를 보유해 **사실상 만료 없이 자동 로그인** 유지.
- jjwt의 `JwtException`(서명오류/만료/형식)을 **우리 에러코드로 번역**해 일관된 401 응답.
- **한계(면접)**: 무상태 JWT라 서버가 특정 refresh를 강제 폐기(로그아웃/기기해제) 불가 → 추후 refresh를 DB/Redis 저장 or `user.token_version` 컬럼으로 보강.

### (3) 개발용 로그인
```java
    public AuthResponse devLogin(DevLoginRequest request) {
        if (!authProperties.devLoginEnabled())
            throw new BusinessException(ErrorCode.DEV_LOGIN_DISABLED);
        Long kakaoId = request.kakaoId() != null
                ? request.kakaoId()
                : -Math.abs(new SecureRandom().nextLong() % 1_000_000_000L); // 음수 = 가짜 카카오id
        String nickname = request.nickname() != null ? request.nickname() : "테스터";
        User user = userService.getOrCreateByKakao(kakaoId, nickname, null);
        return issueTokens(user);
    }
```
- 카카오 없이 JWT 발급 → 친구/푸시 기능을 카카오 연동 전에 테스트하기 위함.
- **`dev-login-enabled=false`면 즉시 차단**(운영에서 비활성). 보안 가드.
- 가짜 kakaoId를 **음수**로 만들어 실제 카카오ID(양수)와 절대 충돌하지 않게(영리한 디테일).
- kakaoId를 주면 같은 가짜 유저로 재로그인 가능(테스트 시 동일인 유지).

### 공통 토큰 발급
```java
    private AuthResponse issueTokens(User user) {
        String access = tokenProvider.createAccessToken(user.getId());
        String refresh = tokenProvider.createRefreshToken(user.getId());
        return new AuthResponse(access, refresh, UserResponse.from(user));
    }
```
- 세 로그인 경로가 공유하는 헬퍼. 중복 제거.

---

## `AuthController.java`

```java
@RestController
@RequestMapping("/auth")
public class AuthController {
    @PostMapping("/kakao")
    public AuthResponse kakaoLogin(@Valid @RequestBody KakaoLoginRequest request) { return authService.kakaoLogin(request); }
    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshRequest request) { return authService.refresh(request); }
    @PostMapping("/dev-login")
    public AuthResponse devLogin(@RequestBody DevLoginRequest request) { return authService.devLogin(request); }
}
```
- **`@RequestMapping("/auth")`**: 클래스 레벨 공통 경로. 메서드의 `/kakao`와 합쳐 `POST /auth/kakao`.
- **`@PostMapping`**: HTTP POST(상태 변경·민감정보 전송이라 GET 아님).
- **`@RequestBody`**: 요청 본문 JSON을 DTO로 역직렬화(Jackson).
- **`@Valid`**: 그 DTO에 붙은 검증(`@NotBlank`)을 실행. 실패하면 `MethodArgumentNotValidException` → [글로벌 핸들러](04-error-handling.md)가 400.
  - `devLogin`엔 `@Valid`가 없다(필드가 다 옵셔널이라 검증할 게 없음).
- 컨트롤러는 **서비스 호출만** — 전형적인 얇은 컨트롤러.

### 인증 엔드포인트 요약

| 메서드 | 경로 | 설명 |
|---|---|---|
| POST | `/auth/kakao` | `{ kakaoAccessToken }` → 로그인, 토큰 발급 |
| POST | `/auth/refresh` | `{ refreshToken }` → 자동 로그인(토큰 회전) |
| POST | `/auth/dev-login` | 카카오 없이 로그인(개발용) |

---

> 다음: [07 friend 도메인 →](07-friend.md)
