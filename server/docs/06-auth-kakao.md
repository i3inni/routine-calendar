# 06 — auth 도메인 (카카오/애플 로그인 + JWT)

> [← 05 user 도메인](05-user.md) · 다음: [07 friend 도메인 →](07-friend.md)

대상 파일 (`auth/`는 `controller/`·`service/`·`dto/`로 분리됨):
`dto/AuthDtos.java`, `dto/KakaoUserResponse.java`, `dto/KakaoFriendsResponse.java`, `service/KakaoApiClient.java`, `service/AppleTokenVerifier.java`, `service/AuthService.java`, `controller/AuthController.java`

**소셜 로그인 두 가지를 지원**하며 둘 다 모바일용 **토큰 교환** 방식이다(웹 OAuth 리다이렉트 아님).
- **카카오**: 앱이 카카오 SDK로 액세스토큰 획득 → 서버가 카카오 API로 내 정보 조회(검증 겸) → 우리 JWT 발급.
- **애플**: 앱이 받은 신원토큰(JWT) → 서버가 **애플 공개키(JWKS)로 직접 서명 검증** → 우리 JWT 발급.

> 신원은 `kakao_id`(양수) 또는 `apple_id`(문자열 sub) 중 하나로 식별. 카카오/애플 유저가 공존한다.

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

```java
    // 카카오 친구찾기(07): 내 카톡 친구 중 앱 가입자 매칭용 — kakaoId → 카톡 표시이름 맵
    public Map<Long, String> fetchFriends(String kakaoAccessToken) {
        KakaoFriendsResponse res = restClient.get().uri(FRIENDS_URI)
                .header(AUTHORIZATION, "Bearer " + kakaoAccessToken)
                .retrieve()
                .onStatus(s -> s.value() == 401, ... INVALID_KAKAO_TOKEN)
                .onStatus(s -> s.value() == 403, ... KAKAO_FRIENDS_CONSENT_REQUIRED)  // friends 미동의
                .body(KakaoFriendsResponse.class);
        // elements[].id → profile_nickname 맵으로
    }
```
- 카카오 `GET /v1/api/talk/friends`. 각 친구의 `id`(회원번호)와 `profile_nickname`(카톡 표시 이름)을 받아 맵으로.
- **403 → `KAKAO_FRIENDS_CONSENT_REQUIRED`**: 토큰에 friends 동의가 없을 때. `friends`는 **검수 전엔 팀원만 동의 가능**한 고급 권한이라, 검수 통과 전 일반 사용자는 이 403을 받는다.
- `KakaoFriendsResponse`: `elements[]`(id, profile_nickname)만 매핑한 DTO(`@JsonIgnoreProperties`). 이 결과를 친구 도메인의 `KakaoFriendMatcher`가 받아 앱 가입자와 매칭([07 카카오 친구찾기](07-friend.md)).

---

## `AppleTokenVerifier.java` — 애플 신원토큰 검증 ⭐

애플 로그인은 카카오와 달리 **외부 API 호출 없이 서버가 직접 JWT를 검증**한다. 애플이 발급한 신원토큰(identity token)은
애플의 비밀키로 서명된 JWT라, 애플의 **공개키(JWKS)** 로 서명을 확인하면 위조 여부를 알 수 있다.

```java
public String verifyAndGetSub(String identityToken) {
    Claims claims = Jwts.parser()
            .keyLocator(header -> resolveKey((String) header.get("kid")))  // kid로 맞는 공개키 선택
            .build()
            .parseSignedClaims(identityToken)
            .getPayload();
    if (!ISSUER.equals(claims.getIssuer())) throw ...;                      // iss == appleid.apple.com
    if (!claims.getAudience().contains(props.clientId())) throw ...;        // aud == 앱 번들ID
    return claims.getSubject();                                             // sub = 안정적 사용자 식별자
}
```
- **검증 4종**: ① 서명(공개키) ② `iss`(애플) ③ `aud`(우리 앱 번들ID=`app.apple.client-id`) ④ 만료(jjwt가 자동). 모두 통과해야 `sub` 반환.
- **`sub`**: 애플이 (우리 앱, 같은 사용자)에 대해 항상 같은 값을 주는 식별자 → `apple_id`로 저장.
- 이름/이메일은 애플이 **최초 로그인 때만** 주므로, 그때 앱이 보낸 이름을 저장한다(이후엔 없음).

### JWKS 처리 + 캐시 (동시성)
```java
private PublicKey resolveKey(String kid) {
    PublicKey key = keyCache.get(kid);
    if (key == null || keyCacheAt.isBefore(Instant.now().minus(KEY_TTL))) {  // 미스/만료 시 갱신
        refreshKeys();
        key = keyCache.get(kid);
    }
    return key;
}
private synchronized void refreshKeys() {                 // 동시 갱신 직렬화
    // GET https://appleid.apple.com/auth/keys → keys[] (각 kid, n, e)
    // n(modulus)/e(exponent) → BigInteger → RSAPublicKey 직접 구성(외부 라이브러리 없이)
    keyCache = map;  keyCacheAt = Instant.now();
}
```
- **왜 JWKS?**: 애플은 서명 키를 **회전(rotation)** 한다. 토큰 헤더의 `kid`로 맞는 키를 골라야 하므로, 키 목록을 받아 캐시.
- **동시성**: `private volatile Map<String, PublicKey> keyCache`. 여러 요청 스레드가 **읽으므로 `volatile`로 가시성** 보장. 갱신(`refreshKeys`)은 `synchronized`로 한 번만(중복 네트워크 호출 방지). TTL(6h) + kid 미스 시 갱신.
- **RSA 키 직접 구성**: JWK의 `n`(modulus), `e`(exponent)를 Base64url 디코드 → `BigInteger` → `RSAPublicKey`. 별도 JWK 라이브러리 없이 표준 JDK만으로.

---

## `AuthService.java` — 로그인/갱신 핵심 로직

생성자 주입: `KakaoApiClient`, **`AppleTokenVerifier`**, `UserService`, `UserRepository`, `JwtTokenProvider`, `AuthProperties`.

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

### (1-b) 애플 로그인
```java
    public AuthResponse appleLogin(AppleLoginRequest request) {
        String appleSub = appleTokenVerifier.verifyAndGetSub(request.identityToken());
        User user = userService.getOrCreateByApple(appleSub, request.name());
        return issueTokens(user);
    }
```
- 흐름이 카카오와 대칭: **검증(JWKS) → 조회/생성 → JWT 발급**. 검증만 외부 호출 없이 자체 처리란 점이 다름.
- `getOrCreateByApple(sub, name)`: `apple_id`로 조회, 없으면 생성. 이름은 최초 1회만 들어오므로 그때 저장([05](05-user.md)).

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
    @PostMapping("/apple")
    public AuthResponse appleLogin(@Valid @RequestBody AppleLoginRequest request) { return authService.appleLogin(request); }
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
| POST | `/auth/kakao` | `{ kakaoAccessToken }` → 카카오 검증 후 로그인 |
| POST | `/auth/apple` | `{ identityToken, name? }` → 애플 JWKS 검증 후 로그인 |
| POST | `/auth/refresh` | `{ refreshToken }` → 자동 로그인(토큰 회전) |
| POST | `/auth/dev-login` | 카카오 없이 로그인(개발용) |

> **AuthDtos에 추가**: `AppleLoginRequest(@NotBlank String identityToken, String name)`.
> **AppleProperties**(`app.apple.client-id` = 앱 번들ID)는 [02 설정 레이어](02-config-layer.md) 참고.

---

> 다음: [07 friend 도메인 →](07-friend.md)
