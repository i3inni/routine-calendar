# routine-calendar-server

루틴 캘린더("같이해")의 친구/통신 백엔드.

## 스택
- Java 17, Spring Boot 3.5.14, Gradle 8.14
- Spring Web / Data JPA(Hibernate) / Security / Validation
- PostgreSQL 16, Flyway(마이그레이션), Lombok

## 로컬 실행

### 1. PostgreSQL 준비
Docker를 쓰는 경우:
```bash
docker compose up -d
```
이미 로컬 Postgres가 있으면 role/db만 만들면 된다:
```sql
CREATE ROLE routine LOGIN PASSWORD 'routine';
CREATE DATABASE routine_calendar OWNER routine;
```

### 2. 앱 실행
```bash
./gradlew bootRun        # 개발 실행
./gradlew build          # 빌드 + 테스트(컨텍스트 로드)
```
접속 정보는 환경변수로 덮어쓸 수 있다: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`.

### 3. 확인
```bash
curl localhost:8080/api/ping
curl localhost:8080/actuator/health
```

## 스키마 (Flyway `V1__init.sql`)
- **users** — Kakao 로그인 신원(`kakao_id`) + 친구추가용 공개 `handle`
- **friendships** — 친구 관계. `(user_low_id < user_high_id)` 한 행으로 정규화(중복 방지)
- **friend_requests** — 친구 요청. 같은 방향 PENDING은 부분 유니크 인덱스로 1건 제한
- **pokes** — 콕 찌르기 기록(쿨다운 검증 + 알림)
- **daily_summaries** — 친구에게 공유되는 오늘 요약. 루틴 이름 목록은 JSONB
- **device_tokens** — APNs 푸시 대상 토큰

> 스키마는 **Flyway가 소유**한다. JPA는 `ddl-auto: validate`로 엔티티↔스키마 일치만 검증한다.

## 패키지 구조 (package-by-feature)
```
config/        SecurityConfig, Jwt/Kakao/AuthProperties
security/      JwtTokenProvider, JwtAuthenticationFilter
auth/          AuthController/Service, KakaoApiClient, DTO
user/          User, UserService, MeController, Repository
friend/        Friendship, FriendRequest(+Status), Repository
poke/          Poke, PokeRepository
summary/       DailySummary, DailySummaryRepository
device/        DeviceToken(+Platform), DeviceTokenRepository
common/error/  ErrorCode, BusinessException, GlobalExceptionHandler
web/           HealthController
```

## 인증 (Kakao 로그인 + JWT)

모바일이라 OAuth 리다이렉트가 아닌 **토큰 교환** 방식: 앱이 카카오 SDK로 받은 액세스 토큰을
서버로 보내면, 서버가 카카오 API로 검증·회원 조회 후 우리 서버 JWT(access+refresh)를 발급한다.

| 메서드 | 경로 | 설명 |
|---|---|---|
| POST | `/auth/kakao` | `{ kakaoAccessToken }` → 로그인, 토큰 발급 |
| POST | `/auth/refresh` | `{ refreshToken }` → **자동 로그인**(토큰 회전) |
| POST | `/auth/dev-login` | 카카오 없이 로그인(개발용, `app.auth.dev-login-enabled=true`) |
| GET | `/me` | 내 정보 (access 토큰 필요) |

**자동 로그인:** 앱은 로그인 시 받은 `refreshToken`을 Keychain에 저장하고 실행할 때마다
`/auth/refresh`로 새 토큰을 받아 조용히 로그인 상태를 유지. refresh가 만료/실패(401)하면
카카오 재로그인으로 유도. refresh는 쓸 때마다 회전되어 활성 사용자는 사실상 만료되지 않음.

> 한계(추후): 현재 refresh는 무상태 JWT라 서버 강제 폐기(로그아웃/기기해제)가 불가.
> 필요 시 refresh를 DB/Redis에 저장하거나 user.token_version 컬럼으로 보강.

## 친구 도메인 API (모두 access 토큰 필요)

| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/me/friends` | 친구 목록 + 각자의 오늘 요약 |
| POST | `/friend-requests` | `{ handle }` 로 친구 요청 (역방향 요청 있으면 자동 성사) |
| GET | `/me/friend-requests` | 내가 받은 요청 목록 |
| POST | `/friend-requests/{id}/accept` | 수락 |
| POST | `/friend-requests/{id}/decline` | 거절 |
| DELETE | `/me/friends/{userId}` | 친구 끊기 (멱등) |
| POST | `/pokes` | `{ toUserId }` 콕 찌르기 (친구만, 1h 쿨다운) |
| POST | `/me/summary` | `{ done[], remaining[], streak }` 오늘 요약 업로드(upsert) |

설계 포인트: 친구 목록/받은요청은 **fetch join**으로 N+1 제거, 친구관계는 `existsBetween`/`findBetween`으로
순서 무관 조회, 친구 끊기는 멱등 DELETE, 콕은 마지막 기록 기준 1시간 쿨다운.

## 푸시 (APNs)

콕/친구요청이 발생하면 상대 기기로 푸시. **도메인 이벤트 → `@TransactionalEventListener(AFTER_COMMIT)`
+ `@Async`** 로 본 트랜잭션과 분리(커밋 성공 후 비동기 발송, 실패해도 본 작업에 영향 없음).

| 메서드 | 경로 | 설명 |
|---|---|---|
| POST | `/me/device-token` | `{ token, platform? }` APNs 토큰 등록(upsert) |

- `ApnsClient`: 토큰 기반(.p8) ES256 프로바이더 JWT(50분 캐시) + HTTP/2(`java.net.http`)로 발송.
  410/Unregistered 응답 시 해당 토큰 폐기.
- **`app.apns.enabled=false`(기본)면 실제 발송 대신 로그만** → 키 없이도 흐름 검증 가능.
  실제 발송하려면 `APNS_ENABLED=true` + `APNS_TEAM_ID`/`APNS_KEY_ID`/`APNS_BUNDLE_ID`/`APNS_PRIVATE_KEY`(.p8 PEM).

## 다음 단계
1. ~~프로젝트 골격 + DB 스키마~~ ✅
2. ~~Kakao 로그인 + JWT(자동 로그인 포함)~~ ✅
3. ~~친구 도메인 API (요청/수락/끊기/목록/요약/콕)~~ ✅
4. ~~APNs 푸시 발송 (.p8, 이벤트+비동기)~~ ✅

**남은 연동 작업**
- iOS `FriendsStore` 스텁을 실제 `APIClient`(REST 호출)로 교체
- Kakao 개발자 콘솔 앱 등록(REST 키) → `/auth/kakao` 실연동
- Apple `.p8` 키 발급 → `APNS_*` 주입해 실제 푸시
- 운영 배포(prod 프로파일: `dev-login-enabled=false`, JWT_SECRET·DB 환경변수)
