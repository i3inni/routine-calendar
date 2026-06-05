# 서버 코드 정독 가이드

`server/`의 모든 자바 코드를 **한 줄 한 줄** 뜯어보며 공부하기 위한 문서 모음.
각 줄이 ① 무슨 기능인지 ② 붙은 어노테이션 개념 ③ 쓴 라이브러리/메서드 ④ 비즈니스 흐름을 설명한다.
목표: 면접에서 "이거 왜 이렇게 했어요?"에 답할 수 있게.

## 읽는 순서

### 기반 (먼저 읽기)
1. [00 — 먼저 알아야 할 핵심 개념](00-core-concepts.md) · IoC/DI, 빈, 계층구조, record, Lombok, JPA
2. [01 — 빌드 & 설정](01-build-and-config.md) · `build.gradle`, `application.yml`, 부트스트랩
3. [02 — 설정 레이어 (config/)](02-config-layer.md) · `@ConfigurationProperties`, `SecurityConfig`, `AsyncConfig`
4. [03 — 보안 & JWT (security/)](03-security-jwt.md) · 토큰 발급/검증, 인증 필터
5. [04 — 글로벌 예외 처리 (common/)](04-error-handling.md) · ErrorCode, BusinessException, RestControllerAdvice

### 도메인 (기능별)
6. [05 — user 도메인](05-user.md) · 엔티티, handle 발급, **닉네임 변경, 계정 삭제(유예)**
7. [06 — auth 도메인 (카카오/애플 로그인)](06-auth-kakao.md) · 토큰 교환, **애플 JWKS 검증**, 자동 로그인
8. [07 — friend 도메인](07-friend.md) · 요청/수락/목록/끊기, N+1 방지
9. [08 — poke 도메인](08-poke.md) · **설정 가능한 쿨다운**, 도메인 이벤트
10. [09 — summary 도메인](09-summary.md) · JSONB, upsert
11. [10 — device 도메인](10-device.md) · 토큰 등록(upsert/reassign)
12. [11 — push 도메인 (APNs)](11-push-apns.md) · 이벤트 + AFTER_COMMIT + @Async, **토큰 자가정리, sandbox/prod**

### 데이터 & 검증
13. [12 — DB 스키마 (Flyway)](12-database-schema.md) · 제약, 인덱스, **V2/V3 마이그레이션**
14. [13 — 테스트](13-testing.md)

### 종합 & 운영
15. [14 — 요청 1건의 전체 흐름 + 동시성 총정리 + 셀프체크](14-request-lifecycle.md)
16. [15 — web 공개 엔드포인트 & 출시 준비](15-web-public-endpoints.md) · `/config`, AASA(Universal Links), `/privacy`, `/support`

## 패키지 구조 (package-by-feature + 계층 분리)
각 도메인을 `controller / service / repository / domain / dto`로 분리. (엔티티·enum·이벤트 → `domain/`)
```
auth/      controller · service(KakaoApiClient, AppleTokenVerifier) · dto
user/      controller · service(+UserPurgeScheduler) · repository · domain · dto
friend/    controller · service · repository · domain(Friendship, FriendRequest(+Status), Event) · dto
poke/      controller · service · repository · domain(Poke, PokeEvent)
summary/   controller · service · repository · domain · dto
device/    controller · service · repository · domain(DeviceToken, Platform) · dto
push/      service(ApnsClient, PushService, PushEventListener)
security/  JwtTokenProvider, JwtAuthenticationFilter
config/    SecurityConfig, AsyncConfig, *Properties(Jwt/Kakao/Auth/Apple/Poke/Apns 통일)
common/    error(ErrorCode, BusinessException, GlobalExceptionHandler), AppTime
web/       HealthController, ConfigController, WellKnownController(AASA), Privacy/SupportController
```
