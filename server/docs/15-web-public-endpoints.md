# 15 — web 공개 엔드포인트 & 출시 준비

> [← 14 요청 흐름](14-request-lifecycle.md)

대상 파일: `web/HealthController.java`, `web/WellKnownController.java`, `web/PrivacyController.java`, `web/SupportController.java`, `web/AdminController.java`

인증 없이 접근하는 **공개 엔드포인트**들. 헬스체크, iOS Universal Links 연결, App Store 제출에 필요한 법적 페이지, 관리자 피드백 조회를 담당한다.
모두 [02 SecurityConfig](02-config-layer.md)의 `permitAll`에 등록돼 토큰 없이 열린다.

```java
.requestMatchers("/auth/**", "/api/ping", "/actuator/health").permitAll()
.requestMatchers("/.well-known/**", "/add-friend", "/privacy", "/support").permitAll()
.requestMatchers("/admin/**").permitAll()   // 화면 진입은 열고, 내부에서 ADMIN_KEY로 가드
```

> 참고: 옛 `/config`(콕 쿨다운 값 제공)는 **제거**됐다. 자극하기 쿨다운이 서버 환경변수가 아니라 **코드 상수**(2회/30분)로 바뀌었고, 남은 횟수·리셋 시각은 친구목록 응답(`nudgeRemaining`/`nudgeResetAtMs`, [07](07-friend.md))에 직접 실려 내려가기 때문.

---

## 1. `HealthController` — 헬스체크

```java
@GetMapping("/api/ping")
public Map<String, String> ping() { return Map.of("status", "ok"); }
```
- 배포/로드밸런서가 서버 생존을 확인하는 가벼운 엔드포인트. 인증 불필요. (`/actuator/health`도 permitAll)

---

## 2. `WellKnownController` — Universal Links(AASA)

iOS가 `https://<도메인>/add-friend/<handle>` 링크를 **앱으로 가로채게** 하는 연결 파일.

```java
@GetMapping(value = "/.well-known/apple-app-site-association",
        produces = MediaType.APPLICATION_JSON_VALUE)
public Map<String, Object> appleAppSiteAssociation() {
    return Map.of("applinks", Map.of("details", List.of(Map.of(
            "appIDs", List.of("TEAMID.BUNDLEID"),
            "components", List.of(Map.of("/", "/add-friend", "?", Map.of("id", "?*")))))));
}
```
- **AASA(apple-app-site-association)**: Apple이 "이 도메인이 이 앱과 연결됨"을 검증하는 파일. **HTTPS + `application/json` + 리다이렉트 없이** 응답해야 함.
- `appIDs = TeamID.BundleID` (예: `DBDJ2HDBU2.com.i3inni.routinecalendar`). 앱 엔타이틀먼트의 `applinks:도메인`과 **서로를 확인**.
- `/add-friend` 폴백 페이지도 제공(앱 미설치 시 브라우저 안내 + 커스텀 스킴 버튼).
- 검증: `curl -i https://<도메인>/.well-known/apple-app-site-association` → 200 + JSON.

> 동작 원리: 앱 설치돼 있으면 iOS가 링크를 가로채 앱을 열고 친구추가 시트에 코드 자동 입력. 미설치면 브라우저가 폴백 페이지.

---

## 3. `PrivacyController` / `SupportController` — App Store 필수 페이지

```java
@GetMapping(value = "/privacy", produces = MediaType.TEXT_HTML_VALUE)
public String privacy() { return "<!doctype html>...개인정보 처리방침..."; }

@GetMapping(value = "/support", produces = MediaType.TEXT_HTML_VALUE)
public String support() { return "<!doctype html>...FAQ + 문의 이메일..."; }
```
- **App Store 제출 요건**: 앱은 **개인정보 처리방침 URL**과 **지원(Support) URL**이 필수.
- 별도 호스팅 없이 **서버에서 직접 HTML 제공** → `https://<도메인>/privacy`, `/support`를 그대로 App Store Connect에 입력.
- `produces = TEXT_HTML_VALUE`: 브라우저가 HTML로 렌더링하도록 Content-Type 지정.
- 수집 항목(카카오/애플 식별자, 닉네임, 기기토큰, 루틴 요약)과 **계정 삭제**([05](05-user.md)) 안내를 명시 → 심사 통과.

---

## 4. `AdminController` — 관리자 피드백 조회

```java
@GetMapping(value = "/admin/feedback", produces = MediaType.TEXT_HTML_VALUE)
public String feedback(@RequestParam(required = false) String key) {
    if (!adminProperties.key().equals(key)) return "<h1>403</h1>";   // ADMIN_KEY 가드
    // feedbackRepository.findAll(최신순) → HTML 표로 렌더
}
```
- 사용자 피드백([feedback 도메인](17-feedback.md), `POST /feedback`)을 **운영자가 브라우저로 한눈에** 보는 페이지.
- 경로는 `permitAll`이지만 **쿼리파라미터 `key`가 `ADMIN_KEY`(env)와 일치해야** 내용을 보여준다 → 별도 로그인 화면 없이 단순 가드.

---

## 출시 준비 체크리스트 (서버 관점)

| 항목 | 엔드포인트/설정 |
|---|---|
| 개인정보 처리방침 URL | `/privacy` |
| 지원 URL | `/support` |
| Universal Links 연결 | `/.well-known/apple-app-site-association` |
| 헬스체크 | `/api/ping`, `/actuator/health` |
| 관리자 피드백 조회 | `/admin/feedback?key=…` |
| 운영 보안값 | `DEV_LOGIN_ENABLED=false`, `JWT_SECRET`(랜덤), `APNS_SANDBOX`(빌드에 맞게), `ADMIN_KEY` |
| 계정 삭제 제공 | `DELETE /me` (App Store 5.1.1 요건) |

---

> 다음: [16 routine 도메인 →](16-routine.md)
