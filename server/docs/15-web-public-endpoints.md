# 15 — web 공개 엔드포인트 & 출시 준비

> [← 14 요청 흐름](14-request-lifecycle.md) · [목차](README.md)

대상 파일: `web/ConfigController.java`, `web/WellKnownController.java`, `web/PrivacyController.java`, `web/SupportController.java`

인증 없이 접근하는 **공개 엔드포인트**들. 클라이언트 설정 제공, iOS Universal Links 연결, App Store 제출에 필요한 법적 페이지를 담당한다.
모두 [02 SecurityConfig](02-config-layer.md)의 `permitAll`에 등록돼 토큰 없이 열린다.

```java
.requestMatchers("/auth/**", "/api/ping", "/actuator/health", "/config").permitAll()
.requestMatchers("/.well-known/**", "/add-friend", "/privacy", "/support").permitAll()
```

---

## 1. `ConfigController` — 클라이언트 설정 제공

```java
@GetMapping("/config")
public Map<String, Object> config() {
    return Map.of("pokeCooldownSeconds", pokeProperties.cooldownSeconds());
}
```
- **왜?**: 콕 쿨다운은 **서버 환경변수**([08 poke](08-poke.md))로 정하는데, 앱이 "X분 후 다시 가능" 같은 안내를 **그 값에 맞춰** 보여주려면 서버 값을 알아야 한다.
- 앱은 진입 시 `/config`를 받아 쿨다운 표시에 사용 → 서버가 값을 바꾸면 앱도 따라옴(하드코딩 불일치 제거).
- 비밀이 아니라 **인증 불필요**. `Map`을 반환하면 `{ "pokeCooldownSeconds": 3600 }` JSON으로 직렬화.

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

## 출시 준비 체크리스트 (서버 관점)

| 항목 | 엔드포인트/설정 |
|---|---|
| 개인정보 처리방침 URL | `/privacy` |
| 지원 URL | `/support` |
| Universal Links 연결 | `/.well-known/apple-app-site-association` |
| 클라이언트 설정 | `/config` |
| 운영 보안값 | `DEV_LOGIN_ENABLED=false`, `JWT_SECRET`(랜덤), `APNS_SANDBOX`(빌드에 맞게) |
| 계정 삭제 제공 | `DELETE /me` (App Store 5.1.1 요건) |

---

> [목차로 →](README.md)
