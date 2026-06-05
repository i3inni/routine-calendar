# FEATURES — 기능별 구현·개념 상세

백엔드의 각 기능을 **① 동작/메소드 ② 사용한 어노테이션 개념 ③ 구현 개념 ④ 동시성 처리** 관점에서 정리한 문서.
포트폴리오 리뷰·면접 대비·복습용. (코드 한 줄씩 보는 정독은 [`docs/`](docs/README.md) 참고)

## 목차
- [0. 공통 기반](#0-공통-기반)
- [1. 인증 (auth)](#1-인증-auth)
- [2. 친구 (friend)](#2-친구-friend)
- [3. 콕 찌르기 (poke)](#3-콕-찌르기-poke)
- [4. 오늘 요약 (summary)](#4-오늘-요약-summary)
- [5. 푸시 (push / device)](#5-푸시-push--device)
- [6. 계정 삭제 (유예)](#6-계정-삭제-유예)
- [7. 동시성 처리 총정리](#7-동시성-처리-총정리)

---

## 0. 공통 기반

### 계층형 패키지 + DI
- **package-by-feature + 계층 분리**: `auth/controller`, `auth/service`, `auth/dto` … 도메인으로 묶고 안을 계층으로 나눔.
- **생성자 주입(constructor injection)** 만 사용 → 필드 `final` 불변, 테스트 시 목 주입 용이, 순환참조를 기동 시점에 발견.

### 타입 세이프 설정 — `@ConfigurationProperties`
```java
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(String secret, long accessTokenValidity, long refreshTokenValidity) {}
```
- `application.yml`의 `app.jwt.*`를 record로 바인딩. **relaxed binding**(`access-token-validity` → `accessTokenValidity`).
- 문자열 키(`@Value`) 흩뿌리지 않고 한 객체로 관리. 모든 Properties는 `config/`에 통일.

### 글로벌 예외 처리
```java
@RestControllerAdvice
class GlobalExceptionHandler {
  @ExceptionHandler(BusinessException.class) … // ErrorCode → 상태/코드/메시지
  @ExceptionHandler(MethodArgumentNotValidException.class) … // @Valid 실패 → 400
  @ExceptionHandler(Exception.class) … // 최후 안전망, 스택트레이스는 로그만
}
```
- **개념**: 도메인 위반을 `throw new BusinessException(ErrorCode.X)` 한 줄로, 응답 변환은 한 곳(`@RestControllerAdvice`)에서. 일관된 `{code, message}`.
- `BusinessException extends RuntimeException` → **언체크 예외라 트랜잭션 롤백 기본 적용**.

### Stateless 보안 — Spring Security
```java
http.sessionManagement(sm -> sm.sessionCreationPolicy(STATELESS))
    .authorizeHttpRequests(a -> a.requestMatchers("/auth/**", …).permitAll().anyRequest().authenticated())
    .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(UNAUTHORIZED)))
    .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
```
- **STATELESS**: 세션 없이 매 요청 토큰으로만 인증 → 수평 확장 유리.
- 인증 실패 시 302 리다이렉트 대신 **401만** 반환(API 서버에 적합).

---

## 1. 인증 (auth)

모바일이라 OAuth 리다이렉트가 아닌 **토큰 교환** 방식. 앱이 받은 소셜 토큰을 서버가 검증하고 **자체 JWT**(access+refresh)를 발급.

### 1-1. JWT 발급/검증 — `JwtTokenProvider`
| 메소드 | 기능 |
|---|---|
| `createAccessToken(userId)` / `createRefreshToken(userId)` | `sub=userId`, `type` 클레임, 만료시각 넣고 HS256 서명 |
| `parseAccessToken(token)` | 서명·만료 검증 + **`type==access` 확인** 후 userId 반환 |
- **어노테이션**: `@Component`. 생성자에서 `Keys.hmacShaKeyFor(secret)`로 대칭키 생성.
- **구현 개념**: access(짧게)/refresh(길게)를 `type` 클레임으로 구분 → **refresh로 API 호출하는 것을 차단**.

### 1-2. 인증 필터 — `JwtAuthenticationFilter`
```java
public class JwtAuthenticationFilter extends OncePerRequestFilter {
  protected void doFilterInternal(...) {
    String token = resolveToken(request);              // "Authorization: Bearer ..."
    Long userId = tokenProvider.parseAccessToken(token);
    var auth = new UsernamePasswordAuthenticationToken(userId, null, List.of());
    SecurityContextHolder.getContext().setAuthentication(auth);  // principal = userId
  }
}
```
- **`OncePerRequestFilter`**: 요청당 1회 실행 보장.
- **구현 개념**: principal에 **userId**를 심어 컨트롤러에서 `@AuthenticationPrincipal Long userId`로 바로 사용 → 클라이언트가 자기 id를 위조 불가.

### 1-3. 카카오 로그인 — `KakaoApiClient`
```java
restClient.get().uri(userInfoUri).header(AUTHORIZATION, "Bearer " + kakaoAccessToken)
  .retrieve().onStatus(s -> s.value()==401, (req,res) -> { throw new BusinessException(INVALID_KAKAO_TOKEN); })
  .body(KakaoUserResponse.class);
```
- **구현 개념**: 앱이 준 카카오 토큰으로 **서버가 직접 `/v2/user/me` 호출 → 그 호출 자체가 토큰 검증**. 별도 시크릿 불필요.
- `RestClient`(Spring 6.1+ 동기 HTTP 클라이언트) 사용.

### 1-4. 애플 로그인 — `AppleTokenVerifier` ⭐
앱이 보낸 **신원토큰(JWT)** 을 애플 공개키로 검증.
| 메소드 | 기능 |
|---|---|
| `verifyAndGetSub(identityToken)` | 서명 검증 + `iss`=appleid.apple.com + `aud`=앱 번들ID + 만료 확인 → `sub`(안정적 사용자 식별자) 반환 |
| `resolveKey(kid)` | 토큰 헤더의 `kid`에 맞는 애플 공개키 반환(캐시 미스/만료 시 갱신) |
| `refreshKeys()` | 애플 JWKS(`/auth/keys`) 받아 `kid → RSAPublicKey` 맵 구성 |
- **구현 개념**: 애플은 RSA 키를 회전하므로 **JWKS를 받아 kid로 키를 선택**해 검증. 키는 `n`(modulus)/`e`(exponent)로 `RSAPublicKey` 직접 구성(외부 라이브러리 없이).
- **동시성**: 공개키 맵을 `volatile Map<String, PublicKey>`로 캐시(여러 요청 스레드 공유, 가시성 보장), `refreshKeys()`는 `synchronized`로 동시 갱신 직렬화. TTL(6h) + kid 미스 시 갱신.

### 1-5. 로그인/자동로그인 흐름 — `AuthService`
| 메소드 | 기능 |
|---|---|
| `kakaoLogin` / `appleLogin` | 소셜 토큰 검증 → `getOrCreateBy*`(첫 로그인=가입) → JWT 발급 |
| `refresh` | refresh 토큰 검증 → **access+refresh 새로 발급(회전)** |
| `devLogin` | (개발용) 카카오 없이 발급. 운영에선 `dev-login-enabled=false`로 차단 |
- **refresh 회전(rotation)**: 쓸 때마다 refresh도 갱신 → 활성 사용자는 사실상 만료 없이 로그인 유지.
- **한계(의도적 단순화)**: 무상태 JWT라 특정 refresh 강제 폐기 불가 → 추후 DB/Redis 저장 or `token_version`으로 보강 가능.

### 엔드포인트
`POST /auth/kakao` · `POST /auth/apple` · `POST /auth/refresh` · `POST /auth/dev-login` · `GET /me`

---

## 2. 친구 (friend)

### 2-1. 정규화된 친구 관계 — `Friendship`
```java
public static Friendship between(User a, User b) {       // 정적 팩토리
  return a.getId() < b.getId() ? new Friendship(a, b) : new Friendship(b, a);
}
```
- **구현 개념**: A-B/B-A 중복을 막기 위해 **항상 id 작은 쪽 = userLow** 한 행으로 저장. DB의 `UNIQUE(low,high) + CHECK(low<high)`와 **이중 안전장치**.
- `@ManyToOne(fetch = LAZY)` + `@JoinColumn` — 연관을 FK로 매핑, 지연 로딩.

### 2-2. N+1 차단 — `FriendService.listFriends` ⭐
```java
List<User> friends = friendshipRepository.findAllOf(me).stream().map(f -> other(f, me)).toList(); // ① fetch join 1쿼리
Map<Long, DailySummary> summaries = dailySummaryRepository
    .findByUserInAndSummaryDate(friends, today).stream()                                          // ② IN 배치 1쿼리
    .collect(Collectors.toMap(s -> s.getUser().getId(), identity()));
```
- **개념**: 친구가 N명이어도 쿼리는 **2번**(친구관계 + 요약 IN). `@Query`의 `join fetch`로 연관을 한 번에 로딩.
- `@Transactional(readOnly = true)` — 변경감지 스냅샷 생략으로 성능↑.

### 2-3. 친구 요청 — `FriendService.sendRequest`
방어 규칙을 순서대로: 자기 자신(400) → 이미 친구(409) → **역방향 PENDING 있으면 자동 성사** → 중복 요청(409) → 저장 + 이벤트 발행.
- `findByRequesterAndAddresseeAndStatus` — 쿼리 메소드로 특정 방향 요청 조회.
- 저장 후 `eventPublisher.publishEvent(new FriendRequestedEvent(...))` → 커밋 후 비동기 푸시(§5).

### 2-4. 멱등 친구 끊기
```java
friendshipRepository.findBetween(me, friend).ifPresent(friendshipRepository::delete);
```
- **멱등(idempotent)**: 이미 친구가 아니어도 에러 없이 성공 → 클라이언트 재시도 안전.

### 2-5. 권한·상태 검증
- `loadPendingRequestForMe`: 요청 존재(404) + **내가 받은(addressee=me) PENDING만** 처리(403). 남의 요청 처리 차단.
- `@Enumerated(EnumType.STRING)` — 상태 enum을 **문자열로 저장**(ORDINAL은 순서 바뀌면 깨짐).

---

## 3. 콕 찌르기 (poke)

```java
if (!friendshipRepository.existsBetween(me, to)) throw new BusinessException(POKE_NOT_FRIEND);   // 친구만
pokeRepository.findTopByFromUserAndToUserOrderByCreatedAtDesc(me, to).ifPresent(last -> {        // 쿨다운
  if (last.getCreatedAt().isAfter(Instant.now().minus(cooldown))) throw new BusinessException(POKE_COOLDOWN);
});
pokeRepository.save(new Poke(me, to));
eventPublisher.publishEvent(new PokeEvent(to.getId(), me.getNickname()));
```
- **기능**: 친구에게만, 같은 상대엔 쿨다운(스팸 방지). 마지막 콕(`findTopBy…OrderByCreatedAtDesc`)으로 검사.
- **구현 개념**: 쿨다운을 **설정값으로** (`app.poke.cooldown-seconds`, env `POKE_COOLDOWN_SECONDS`) → 테스트는 짧게, 운영은 1시간. `PokeProperties`를 생성자에서 `Duration`으로 변환해 보관.
- DB 복합 인덱스 `(from, to, created_at)`로 쿨다운 조회 가속.

---

## 4. 오늘 요약 (summary)

루틴 **원본은 기기에만**, 서버엔 **친구 공유용 요약**만 업로드(개인정보 최소화).
```java
@JdbcTypeCode(SqlTypes.JSON)
private List<String> doneNames = new ArrayList<>();   // JSONB 컬럼에 리스트 직렬화
```
- **어노테이션**: `@JdbcTypeCode(SqlTypes.JSON)`(Hibernate 6) — `List<String>`을 Postgres **JSONB**로.
- **upsert**: `findByUserAndSummaryDate(me, today).orElseGet(() -> new DailySummary(me, today))` 후 갱신. `UNIQUE(user_id, summary_date)`가 “하루 한 행” 보장.
- `doneCount/totalCount`는 **서버가 목록 크기로 계산**(클라이언트 값 불신). 날짜는 `AppTime.today()`(KST).

---

## 5. 푸시 (push / device)

### 5-1. 이벤트 기반 비동기 발송 ⭐⭐ — `PushEventListener`
```java
@Async
@TransactionalEventListener   // 기본 단계 = AFTER_COMMIT
public void onPoke(PokeEvent event) {
  pushService.sendToUser(event.toUserId(), event.fromNickname()+"님이 콕 찔렀어요", "함께 루틴 해요");
}
```
- **`@TransactionalEventListener`(AFTER_COMMIT)**: `publishEvent`한 트랜잭션이 **성공 커밋된 뒤에만** 실행 → “DB엔 없는데 푸시만 가는” 모순 방지. (일반 `@EventListener`는 커밋 전 동기 실행)
- **`@Async`**: 별도 스레드풀에서 실행(`@EnableAsync`) → APNs 네트워크 호출이 **요청 응답을 막지 않음**, 실패해도 본 트랜잭션과 격리.
- **조합 효과**: “커밋된 사실에 대해서만, 응답과 무관하게, 격리되어” 발송.

### 5-2. APNs 저수준 클라이언트 — `ApnsClient`
| 메소드 | 기능 |
|---|---|
| `send(token, title, body)` | `enabled=false`면 로그만; 아니면 HTTP/2로 발송, 응답 분류 |
| `providerToken()` | `.p8`로 서명한 **ES256 인증 JWT**(50분 캐시) |
| `loadPrivateKey()` | `.p8` PEM → PKCS#8 EC 개인키 로드(1회) |
- **구현 개념**: 토큰 기반(.p8) 인증. APNs는 HTTP/2 필수(`java.net.http.HttpClient`). sandbox/production 엔드포인트를 설정으로 전환.
- **죽은 토큰 자동 폐기**: `410 Unregistered`, `BadDeviceToken`, `BadEnvironmentKeyInToken`, `DeviceTokenNotForTopic` → `UNREGISTERED` → DB에서 삭제.
- **동시성**: `cachedJwt`, `privateKey`를 `volatile`로 캐시(여러 @Async 스레드 공유), 생성은 `synchronized`로 직렬화(중복 서명 방지).

### 5-3. 멀티 디바이스 발송 — `PushService.sendToUser`
- 한 유저의 **모든 기기 토큰**으로 발송(`findByUser`), 결과별 로깅, UNREGISTERED 토큰 삭제. 비동기 스레드에서 새 `@Transactional`로 실행.

### 5-4. 토큰 등록(upsert) — `DeviceTokenService.register`
```java
deviceTokenRepository.findByToken(token).ifPresentOrElse(
  existing -> existing.reassign(me, platform),                 // 있으면 소유자 갱신
  () -> deviceTokenRepository.save(new DeviceToken(me, token, platform)));  // 없으면 생성
```
- 같은 토큰이 다른 계정/기기로 재등록될 때 **소유자만 갈아끼움**(`token` UNIQUE).

> **운영 주의**: 알림 도착 여부는 **받는 기기의 APNs 환경**에만 의존. Xcode 직접 설치=sandbox(`APNS_SANDBOX=true`), TestFlight/App Store=production(`false`). 자세한 트러블슈팅은 README 참고.

---

## 6. 계정 삭제 (유예)

App Store 요건(계정 생성 시 삭제 제공). **즉시 삭제 대신 유예**.
| 메소드 | 기능 |
|---|---|
| `requestDeletion(userId)` | `deletion_requested_at = now` 기록(soft delete), 유예 종료시각 반환 |
| `reactivateIfPending(user)` | **로그인 시** 예약돼 있으면 비워서 취소 |
| `UserPurgeScheduler.purgeExpiredAccounts()` | 유예(3일) 지난 계정 일괄 영구 삭제 |
```java
@Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")   // 매일 04시 KST
@Transactional
public void purgeExpiredAccounts() {
  List<User> expired = userRepository.findByDeletionRequestedAtBefore(Instant.now().minus(DELETION_GRACE));
  userRepository.deleteAll(expired);   // 연관 데이터는 DB ON DELETE CASCADE
}
```
- **어노테이션**: `@Scheduled`(+`@EnableScheduling`) 주기 실행. `@DeleteMapping("/me")`로 예약 트리거.
- **구현 개념**: soft delete + 스케줄러 hard delete + 재로그인 취소. users 한 행 삭제로 친구/요약/콕/토큰이 **CASCADE**로 정리(스키마 `ON DELETE CASCADE`).

---

## 7. 동시성 처리 총정리

이 프로젝트에서 동시성을 다룬 지점들을 한눈에.

| 기법 | 위치 | 목적 |
|---|---|---|
| **`@Async` + 스레드풀** | `PushEventListener` | 푸시 발송을 요청 스레드와 분리(응답성) |
| **`@TransactionalEventListener(AFTER_COMMIT)`** | 푸시 발행 | 커밋된 트랜잭션에 대해서만 후처리(정합성) |
| **`@Transactional`** | 모든 서비스 쓰기 | 원자성 + 런타임 예외 시 롤백. dirty checking으로 상태변경=UPDATE |
| **`@Transactional(readOnly=true)`** | 조회 | 스냅샷 생략 성능↑, 쓰기 방지 |
| **`volatile` 캐시** | `ApnsClient`(provider JWT/키), `AppleTokenVerifier`(JWKS) | 여러 스레드 간 캐시 **가시성** 보장 |
| **`synchronized`** | 위 캐시 갱신 메소드 | 동시 갱신 직렬화(중복 작업 방지) |
| **`@Scheduled`** | `UserPurgeScheduler` | 백그라운드 주기 작업(유예 만료 삭제) |
| **DB 유니크/부분 인덱스** | friendships, friend_requests, device_tokens | 동시 요청에도 **중복 방지를 DB가 최종 보장** |
| **멱등 설계** | 친구 끊기, 토큰 upsert, 계정삭제 | 재시도/중복 호출에 안전 |

### 면접 포인트로 설명한다면
- **“푸시를 왜 이벤트+AFTER_COMMIT+@Async로?”** → 커밋된 사실만 알림(정합성) · 응답 안 막음(응답성) · 본 트랜잭션과 격리(안정성).
- **“JWKS/프로바이더 토큰 캐시의 동시성은?”** → 다중 스레드가 읽으므로 `volatile`로 가시성, 갱신은 `synchronized`로 한 번만.
- **“동시에 같은 친구요청이 두 번 오면?”** → 앱 로직 + **부분 유니크 인덱스(활성 PENDING 1건)** 로 DB가 최종 차단.
- **“N+1은?”** → fetch join + `IN` 배치로 친구 수와 무관하게 쿼리 수 고정.

---

> 더 깊은 한 줄 단위 설명은 [`docs/`](docs/README.md), 실행·배포·API는 [`README.md`](README.md) 참고.
