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

## 배포 (Docker / PaaS)

[`Dockerfile`](Dockerfile)은 멀티스테이지(JDK로 `bootJar` 빌드 → JRE로 실행).
PaaS(KoDeploy/Railway 등)가 주입하는 `PORT`를 `server.port=${PORT:8080}`가 받는다.

```bash
docker build -t routine-server .
docker run -p 8080:8080 --env-file server/.env routine-server
```

운영에서 주입할 환경변수(`server/.env.example` 참고):

| 변수 | 설명 |
|---|---|
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | JDBC 접속 (`jdbc:postgresql://...`) |
| `JWT_SECRET` | 길고 무작위한 256bit+ 값 |
| `DEV_LOGIN_ENABLED` | 운영은 반드시 `false` |
| `APPLE_CLIENT_ID` | 애플 로그인 검증 기준 = 앱 번들ID(`com.i3inni.routinecalendar`) |
| `APNS_ENABLED` 외 `APNS_*` | 실제 푸시 시 (아래 푸시 섹션). `APNS_BUNDLE_ID`=앱 번들ID |

> PaaS의 Postgres 플러그인은 보통 `postgresql://...`(JDBC 아님)을 주므로,
> `DB_URL`은 `jdbc:` 접두사를 붙여 따로 구성한다.

## 스키마 (Flyway)
- `V1__init.sql` 6개 테이블 / `V2__add_apple_login.sql` 애플 컬럼 / `V3__add_account_deletion.sql` 삭제 유예 컬럼
- **users** — 로그인 신원(`kakao_id` 또는 `apple_id`, 둘 중 하나) + 친구추가용 공개 `handle`
- **friendships** — 친구 관계. `(user_low_id < user_high_id)` 한 행으로 정규화(중복 방지)
- **friend_requests** — 친구 요청. 같은 방향 PENDING은 부분 유니크 인덱스로 1건 제한
- **pokes** — 콕 찌르기 기록(쿨다운 검증 + 알림)
- **daily_summaries** — 친구에게 공유되는 오늘 요약. 루틴 이름 목록은 JSONB
- **device_tokens** — APNs 푸시 대상 토큰

> 스키마는 **Flyway가 소유**한다. JPA는 `ddl-auto: validate`로 엔티티↔스키마 일치만 검증한다.

## 패키지 구조 (package-by-feature + 계층 분리)
각 도메인을 `controller / service / repository / domain / dto`로 나눈다. (엔티티·enum·이벤트 → `domain/`)
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
web/       HealthController, ConfigController, WellKnown(AASA), Privacy/SupportController
```

> 기능별 구현·어노테이션·동시성 처리 + 코드 한 줄씩 정독: [`docs/`](docs/README.md)

## 인증 (Kakao / Apple 로그인 + JWT)

모바일이라 OAuth 리다이렉트가 아닌 **토큰 교환** 방식: 앱이 받은 소셜 토큰을 서버로 보내면,
서버가 검증·회원 조회 후 우리 서버 JWT(access+refresh)를 발급한다. (신원은 `kakao_id` 또는 `apple_id`)

| 메서드 | 경로 | 설명 |
|---|---|---|
| POST | `/auth/kakao` | `{ kakaoAccessToken }` → 카카오 API로 검증 후 로그인 |
| POST | `/auth/apple` | `{ identityToken, name? }` → 애플 공개키로 검증 후 로그인 |
| POST | `/auth/refresh` | `{ refreshToken }` → **자동 로그인**(토큰 회전) |
| POST | `/auth/dev-login` | 카카오 없이 로그인(개발용, `app.auth.dev-login-enabled=true`) |
| GET | `/me` | 내 정보 (access 토큰 필요) |
| PATCH | `/me` | `{ nickname }` 닉네임(친구에게 보이는 이름) 변경 |
| DELETE | `/me` | 계정 삭제 예약(3일 유예). `{ deletionScheduledAt }` 반환 |

**애플 로그인 검증**(`AppleTokenVerifier`): 앱이 보낸 신원토큰(JWT)을 애플 JWKS(`/auth/keys`)로
서명 검증 + `iss`(appleid.apple.com)/`aud`(앱 번들ID=`app.apple.client-id`)/만료 확인 → `sub`로 회원 식별.

**계정 삭제(유예 패턴)**: `DELETE /me`는 바로 지우지 않고 `deletion_requested_at`만 기록(soft delete) →
**3일 내 재로그인하면 자동 취소**(`reactivateIfPending`), 지나면 `UserPurgeScheduler`(매일 04시 KST)가
영구 삭제(연관 데이터는 DB `ON DELETE CASCADE`). App Store의 "계정 삭제 제공" 요건 충족.
이름은 애플이 **최초 로그인 때만** 주므로 그때 저장한다.

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
| POST | `/pokes` | `{ toUserId }` 콕 찌르기 (친구만, 쿨다운 적용) |
| POST | `/me/summary` | `{ done[], remaining[], streak }` 오늘 요약 업로드(upsert) |

설계 포인트: 친구 목록/받은요청은 **fetch join**으로 N+1 제거, 친구관계는 `existsBetween`/`findBetween`으로
순서 무관 조회, 친구 끊기는 멱등 DELETE, 콕은 마지막 기록 기준 쿨다운.

**콕 쿨다운은 설정값**: `app.poke.cooldown-seconds`(env `POKE_COOLDOWN_SECONDS`, 기본 3600). 테스트 땐 짧게.

### 클라이언트 설정 조회 (인증 불필요)

| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/config` | `{ pokeCooldownSeconds }` — 앱이 쿨다운 남은시간 표시에 사용 |

## 푸시 (APNs)

콕/친구요청이 발생하면 상대 기기로 푸시. **도메인 이벤트 → `@TransactionalEventListener(AFTER_COMMIT)`
+ `@Async`** 로 본 트랜잭션과 분리(커밋 성공 후 비동기 발송, 실패해도 본 작업에 영향 없음).

| 메서드 | 경로 | 설명 |
|---|---|---|
| POST | `/me/device-token` | `{ token, platform? }` APNs 토큰 등록(upsert) |

- `ApnsClient`: 토큰 기반(.p8) ES256 프로바이더 JWT(50분 캐시) + HTTP/2(`java.net.http`)로 발송.
  못 쓰는 토큰은 자동 폐기: `410 Unregistered`, `BadDeviceToken`, `BadEnvironmentKeyInToken`, `DeviceTokenNotForTopic`.
- **`app.apns.enabled=false`(기본)면 실제 발송 대신 로그만** → 키 없이도 흐름 검증 가능.
  실제 발송하려면 `APNS_ENABLED=true` + `APNS_TEAM_ID`/`APNS_KEY_ID`/`APNS_BUNDLE_ID`/`APNS_PRIVATE_KEY`(.p8 PEM).
- 콕/친구요청 전 과정에 단계별 로그(`[콕] 요청/저장/발송 시작`, `푸시 결과 result=...`)가 찍혀 진단 가능.

### sandbox vs production (핵심 주의)
알림 도착 여부는 **받는 사람 기기의 APNs 환경**에만 달려 있다(보내는 쪽 무관).

| 받는 앱 설치 방식 | 토큰 환경 | `APNS_SANDBOX` |
|---|---|---|
| Xcode로 직접 Run | development(sandbox) | `true` |
| TestFlight / App Store | production | `false` |

- 환경이 어긋나면 `BadDeviceToken`(prod 토큰을 sandbox로) 또는 `BadEnvironmentKeyInToken`(sandbox 토큰을 prod로)이 뜨고 토큰이 폐기된다.
- 토큰은 **로그인 상태 + 알림 권한 허용** 시 앱 실행 때 `POST /me/device-token`으로 등록된다.
  권한 거부면 iOS가 토큰을 발급하지 않아 영영 `등록된 기기 없음`.

## Universal Links (친구추가 딥링크)

iOS 앱이 `https://<도메인>/add-friend/<handle>` 링크를 가로채 친구추가 화면을 열도록 지원.

| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/.well-known/apple-app-site-association` | Apple이 도메인↔앱 연결을 검증하는 AASA(JSON). 인증 없이 공개 |
| GET | `/add-friend` | 앱 미설치(브라우저) 시 폴백 안내 페이지 |

- AASA의 `appIDs`는 `TeamID.BundleID`(`DBDJ2HDBU2.com.i3inni.routinecalendar`).
  TeamID/BundleID가 바뀌면 `WellKnownController`의 `APP_ID`도 수정.
- 동작 조건: HTTPS + `application/json` + 리다이렉트 없이 응답되어야 함.
  ```bash
  curl -i https://<도메인>/.well-known/apple-app-site-association
  ```
- 앱 쪽은 `applinks:<도메인>` 엔타이틀먼트가 필요(iOS `project.yml`).
  현재 공유 링크는 서버 없이도 열리는 커스텀 스킴(`routinecalendar://...`)을 쓰고,
  서버 배포 후 https Universal Link로 전환 가능.

## 다음 단계
1. ~~프로젝트 골격 + DB 스키마~~ ✅
2. ~~Kakao 로그인 + JWT(자동 로그인 포함)~~ ✅
3. ~~친구 도메인 API (요청/수락/끊기/목록/요약/콕)~~ ✅
4. ~~APNs 푸시 발송 (.p8, 이벤트+비동기)~~ ✅
5. ~~iOS 연동(REST/카카오 로그인) + Docker 배포 + Universal Links AASA~~ ✅

**남은 연동 작업**
- 실기기에서 APNs 실제 발송 검증(`APNS_ENABLED=true`, sandbox/production 매칭)
- 운영 도메인 확정 → 앱 `applinks`/`API_BASE_URL` + 서버 AASA 도메인 정렬
- 운영 배포(prod: `DEV_LOGIN_ENABLED=false`, `JWT_SECRET`·DB 환경변수, HTTPS)
