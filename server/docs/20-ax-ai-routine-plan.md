# AX(AI Transformation) 적용 플랜 — 「같이해」

> 목표: ChatGPT 붙이기가 아니라, **기존 기능·데이터를 AI가 활용해 앱 조작 과정을 줄이는 것.**
> 최우선 기준: **가장 적은 변경으로 가장 큰 AX 효과.**

## 0. 실측한 현재 구조 (설계 전제)

| 항목 | 값 |
| --- | --- |
| 스택 | Spring Boot 3.5.14, Java 17, Postgres, Redis, Flyway |
| URL | `/api/` 없음. 내 자원은 `/me/...` |
| 인증 | `@AuthenticationPrincipal Long meId` (principal = raw Long) |
| 응답 | 래핑 없음, record DTO 직접 반환. 에러만 `ErrorResponse(code, message)` |
| 에러 | `ErrorCode` enum → `throw new BusinessException(...)` → `GlobalExceptionHandler` 자동 |
| 설정 | `@ConfigurationProperties` record in `config/`, 시크릿 `${ENV:default}` |
| 아웃바운드 | AI SDK 없음. `RestClient` 사용(기존 `KakaoApiClient`·`ApnsClient` 패턴) |

**루틴 데이터:** `type`("check"/"count"), `target`(int), `repeatMode`("daily"/"weekdays"/"custom"), `repeatDays`(0=일…6=토), `reminder`("HH:MM"), `anytime`, `createdAt`(시작), `endDate`. PK=클라 생성 UUID. 완료 = `count >= target`. **Streak은 저장 안 됨**(`FriendTodayCalculator`가 매번 계산). **친구 개별 루틴 노출 엔드포인트 없음** — 집계 `FriendResponse`만.

## 1. 기능 우선순위 → ①AI 루틴 생성부터

| 기능 | 변경량 | 안전성 | AX효과 |
| --- | --- | --- | --- |
| **①AI 루틴 생성** | **최소(write 0)** | AI는 초안만, 저장은 사용자 확인 | 폼 6~7필드 → 한 문장 |
| ②루틴 분석 | 중(통계 집계) | 읽기전용 | 높음 |
| ③친구 동기부여 | 중 | 프라이버시 주의 | 높음 |
| ④자연어 수정 | 최대(Tool+모호성) | 리스크 | 높지만 위험 |

**구현 순서: ① → ② → ③ → ④(Tool Calling, 마지막)**

**①을 먼저 하는 이유:** write 로직 신규 0 — AI는 `RoutineRequest` 모양 **초안만** 만들고, 사용자가 확인하면 **기존 `POST /me/routines`** 그대로 탐. "명시적 확인 후 생성" + "기존 Service 재사용"을 구조적으로 만족. Structured Output 정석(Tool 불필요). 여기서 만든 `LlmClient` 인프라를 ②③④가 재사용.

## 2. 아키텍처

```
Client ──POST /me/ai/routine-draft(text)──> AiRoutineController
   └─(meId)─> AiRoutineService ─프롬프트+스키마─> LlmClient ─> LLM Provider
                                                    └ structured JSON 반환
   <── RoutineDraftResponse(draft, message, warnings) ──
Client ─(사용자 확인)─> 기존 POST /me/routines ─> RoutineService.create ─> DB
LLM은 DB 직접 접근 X.  usage=Micrometer,  호출제한=Redis RateLimiter.
```

## 3. API (기존 `/me/...` 컨벤션)

```
POST /me/ai/routine-draft     ← ① 이번 구현
GET  /me/ai/insights          ← ② 다음
GET  /me/ai/friend-nudge      ← ③
POST /me/ai/routine-commands  ← ④ Tool Calling
```

## 4. DTO (①)

```java
record RoutineDraftRequest(@NotBlank @Size(max = 200) String text) {}

record RoutineDraftResponse(
        RoutineRequest draft,     // id/createdAt=null, 클라가 확인 후 그대로 POST
        String assistantMessage,  // "월/수/금 저녁 8시 운동 루틴을 만들까요?"
        List<String> warnings) {} // 추정한 부분 알림
```
`RoutineRequest` 재사용 → 확인만 하면 기존 생성 API로 직행.

## 5. Service

- **신규:** `AiRoutineService.draftFromText(Long meId, String text)`
- **신규 공용 인프라:** `LlmClient` 인터페이스 + `RestClient` 구현 1개, `AiProperties`(`app.ai.*`)
- **재사용:** 생성 로직 손 안 댐(기존 `RoutineService.create`), `common/RateLimiter`(Redis 트랙) 재사용

## 6. LLM 프롬프트 (초안)

**System:** 루틴 앱 파서. 한국어 문장을 아래 스키마 JSON으로만 변환.
`type`(check/count, 기본 check) · `target`(int) · `unit`(없으면 "") · `repeatMode`(daily/weekdays/custom) · `repeatDays`(custom만, 0=일…6=토) · `reminder`("HH:MM" 24h, 없으면 null) · `anytime`. 추정값은 `warnings`로. 오늘={today}, TZ=Asia/Seoul.

**User:** `"매주 월수금 저녁 8시에 운동하고 싶어"`
**출력:** `{"name":"운동","type":"check","target":1,"repeatMode":"custom","repeatDays":[1,3,5],"reminder":"20:00","anytime":false}`

## 7. Structured Output / Tool

- **①: Tool 불필요** — 단일 스키마 Structured Output만(Provider의 strict JSON 모드).
- **④에서만:** `getMyRoutines`, `updateRoutine(...)` Tool 정의, 실행은 여전히 `RoutineService`.

## 8. Provider 선택

셋 다 충분(짧은 한국어→작은 JSON). 판단: 비용·Java연동·구조화출력.

| | 구조화 | Java | 비용 |
| --- | --- | --- | --- |
| OpenAI 소형 | strict json_schema 최강 | 예제 최다 | 저렴 |
| Gemini Flash | responseSchema | 준수 | 최저 |
| Claude Haiku | tool 기반, 한국어 강함 | 준수 | 저렴 |

**권장:** `LlmClient` 인터페이스 뒤에 구현 1개 → 교체 가능(포트폴리오 포인트). 무거운 Spring AI 대신 **`RestClient` 직접 호출**. 첫 구현은 strict JSON 안정성으로 **OpenAI 소형 모델** 추천(추상화돼 있어 교체 자유).

## 9. DB 변경 & 보안

- **DB 변경 0.** AI 사용량은 엔티티 대신 **Micrometer 카운터**(Prometheus 트랙 연결). 대화기록 저장 안 함.
- 보안: 본인만(`@AuthenticationPrincipal`), LLM엔 **문장+스키마만**(PII·JWT·타인정보 0), AI는 DB 못 씀, 생성은 확인 후. AI 엔드포인트 **rate limit**(Redis 재사용). 신규 `ErrorCode`: `AI_PROVIDER_ERROR`(502), `AI_RATE_LIMITED`(429).

## 10. 구현 순서 (학습 트랙 — 직접 타이핑)

```
1. Provider 결정 + API 키 env + AiProperties(config record)
2. LlmClient 인터페이스 + RestClient 구현 1개
3. ParsedRoutine 스키마 + 프롬프트
4. AiRoutineService.draftFromText
5. AiRoutineController  POST /me/ai/routine-draft
6. RateLimiter 적용 + ErrorCode 2개
7. Micrometer 카운터
8. 테스트(LlmClient mock)
9. 예외/타임아웃 처리
10. 문서화(노션 백엔드/AX 시리즈)
```
