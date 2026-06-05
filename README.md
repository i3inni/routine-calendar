# 같이해 (RoutineCalendar)

> 친구와 함께 매일의 루틴을 만들어가는 iOS 앱 — **SwiftUI 클라이언트 + Spring Boot 백엔드**를 직접 설계·구현한 풀스택 사이드 프로젝트.

혼자 하면 작심삼일이 되기 쉬운 루틴을, **친구의 진행 상황을 공유하고 ‘콕 찌르기’로 서로 응원**하며 이어가도록 만든 앱입니다.
이 저장소는 iOS 앱과 백엔드 서버를 모두 포함하며, **백엔드(Spring Boot)** 설계·구현에 중점을 두었습니다.

---

## 핵심 요약

| 영역 | 내용 |
|---|---|
| **클라이언트** | iOS / SwiftUI / `@Observable` / WidgetKit / Kakao·Apple 로그인 / APNs |
| **백엔드** | Java 17 / Spring Boot 3.5 / Spring Security / Spring Data JPA(Hibernate) / PostgreSQL 16 / Flyway |
| **인증** | 카카오·애플 소셜 로그인 → 자체 JWT(access+refresh, 회전) / Stateless |
| **인프라** | Docker 멀티스테이지 빌드 / PaaS 배포 / Universal Links(AASA) |
| **배포 상태** | iOS App Store 심사 제출 · 백엔드 PaaS 운영 |

---

## 주요 기능

- **소셜 로그인 + 자동 로그인** — 카카오(토큰 교환), 애플(JWKS 서명 검증) → 자체 JWT 발급, refresh 토큰 회전으로 무중단 로그인 유지
- **친구 시스템** — 핸들(공개 ID) 기반 친구 요청/수락/거절, 양방향 요청 시 자동 성사, 친구별 오늘 루틴 진행률 공유
- **콕 찌르기** — 친구 독려 푸시 알림, 설정 가능한 쿨다운(스팸 방지)
- **원격 푸시(APNs)** — 도메인 이벤트 기반 비동기 발송, 죽은 토큰 자동 정리
- **계정 삭제(유예)** — 3일 유예 soft delete + 스케줄러 영구 삭제 + 재로그인 시 취소 (App Store 요건)
- **위젯** — 홈 화면에서 오늘 루틴 진행 상황 확인 (App Group 데이터 공유)

---

## 아키텍처

```
┌─────────────────┐        HTTPS / JWT         ┌──────────────────────────┐
│   iOS (SwiftUI) │ ───────────────────────▶  │   Spring Boot REST API   │
│  - 루틴/캘린더   │ ◀───────────────────────  │  - 인증, 친구, 콕, 요약   │
│  - 위젯(App Grp) │                            │  - 도메인 이벤트 → 푸시   │
│  - APNs 토큰등록 │        APNs(.p8)           └────────────┬─────────────┘
└─────────────────┘  ◀── Apple Push ──┐                     │ JPA
                                       │              ┌──────▼──────┐
                          ┌────────────┴───┐          │ PostgreSQL  │
                          │  Apple APNs    │          │  (Flyway)   │
                          └────────────────┘          └─────────────┘
```

### 백엔드 패키지 구조 (package-by-feature + 계층 분리)
```
com.routinecalendar.server
├── auth/      controller · service · dto        (카카오/애플 로그인, JWT 발급)
├── user/      controller · service · repository · domain · dto
├── friend/    controller · service · repository · domain · dto
├── poke/      controller · service · repository · domain
├── summary/   controller · service · repository · domain · dto
├── device/    controller · service · repository · domain · dto   (APNs 토큰)
├── push/      service                            (APNs 클라이언트·발송·이벤트 리스너)
├── security/  JWT 발급·검증, 인증 필터
├── config/    SecurityConfig, *Properties(타입 세이프 설정)
└── common/    글로벌 예외 처리, 공통 유틸
```
- **도메인별로 묶고(feature)** 그 안을 **계층(controller/service/repository/domain/dto)으로** 다시 분리해 응집도↑·탐색성↑
- 엔티티·enum·도메인 이벤트는 `domain/`, 모든 `@ConfigurationProperties`는 `config/`로 통일

---

## 설계에서 신경 쓴 점

엔지니어링 의사결정과 그 근거를 정리했습니다. 자세한 구현·개념·동시성 처리는 **[server/docs/](server/docs/README.md)** 정독 가이드 참고.

- **Stateless JWT 인증** — 서버에 세션을 두지 않아 수평 확장에 유리. access(짧게)/refresh(길게) 분리 + `type` 클레임으로 혼용 차단, refresh 회전.
- **애플 신원토큰 검증** — 애플 JWKS(공개키)를 받아 서명·`iss`·`aud`·만료를 직접 검증. 공개키는 캐시(`volatile`)로 재사용.
- **이벤트 기반 비동기 푸시** — 콕/친구요청을 도메인 이벤트로 발행하고 `@TransactionalEventListener(AFTER_COMMIT)` + `@Async`로 처리. **커밋된 사실만**, **응답을 막지 않고**, 푸시 실패가 본 트랜잭션에 영향 없게 분리.
- **N+1 차단** — 친구 목록은 fetch join + `IN` 배치 조회로 친구 수와 무관하게 쿼리 수 고정.
- **데이터 무결성 이중화** — 친구 관계 정규화(`user_low < user_high`)를 앱과 DB 제약(UNIQUE/CHECK) 양쪽에서 강제. 부분 유니크 인덱스로 “활성 PENDING 1건” 제약.
- **글로벌 예외 처리** — `ErrorCode` enum + `BusinessException` + `@RestControllerAdvice`로 일관된 `{code, message}` 응답.
- **계정 삭제 유예** — 즉시 삭제 대신 soft delete + 스케줄러 일괄 삭제 + 재로그인 취소. 연관 데이터는 DB `ON DELETE CASCADE`로 정리.
- **스키마 소유권 분리** — Flyway가 스키마를 소유(`V1`~`V3`), JPA는 `ddl-auto: validate`로 일치 검증만.

---

## 기술 스택

**Backend** · Java 17 · Spring Boot 3.5.14 · Spring Web/Security/Data JPA · Hibernate 6 · PostgreSQL 16 · Flyway · jjwt · Lombok · Gradle · Docker

**iOS** · Swift · SwiftUI · `@Observable`/`@MainActor` · WidgetKit · AuthenticationServices(Apple) · Kakao SDK · Keychain · xcodegen

---

## 실행 방법 (백엔드)

```bash
cd server
docker compose up -d          # PostgreSQL
./gradlew bootRun             # 서버 (http://localhost:8080)
curl localhost:8080/api/ping
```
상세 실행·배포·환경변수는 **[server/README.md](server/README.md)** 참고.

---

## 문서

| 문서 | 내용 |
|---|---|
| **[server/README.md](server/README.md)** | 백엔드 실행·배포·API·스키마 레퍼런스 |
| **[server/docs/](server/docs/README.md)** | 기능별 구현·어노테이션·동시성 처리 + 코드 한 줄씩 정독 가이드 |

---

## 개발 노트

- 처음엔 CloudKit 기반이었으나, **백엔드 역량 강화**를 목표로 Spring Boot + PostgreSQL로 직접 재설계·구현.
- 인증·친구·푸시·계정삭제 등 실제 서비스에 필요한 도메인을 end-to-end로 구현하고 App Store 출시까지 진행.
