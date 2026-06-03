# 14 — 요청 1건의 전체 흐름 + 면접 셀프체크

> [← 13 테스트](13-testing.md) · [목차](README.md)

앞 문서들에서 본 조각들이 **요청 한 번**에 어떻게 맞물리는지 종합한다.

---

## 예: 콕 찌르기 `POST /pokes`

요청: `POST /pokes` · 헤더 `Authorization: Bearer <access토큰>` · 본문 `{ "toUserId": 7 }`

```
1. 톰캣이 요청 수신
2. [SecurityFilterChain] JwtAuthenticationFilter 실행          → 03 보안 & JWT
     - 헤더에서 Bearer 토큰 추출 → parseAccessToken → userId=3 복원
     - SecurityContext에 principal=3 세팅
3. 인가 검사: /pokes 는 인증 필요 → 인증 됐으므로 통과          → 02 SecurityConfig
4. [DispatcherServlet] PokeController.poke 매핑                → 08 poke
     - @RequestBody 로 JSON → PokeRequest(toUserId=7) 역직렬화
     - @Valid → @NotNull 검증
     - @AuthenticationPrincipal → meId=3 주입
5. PokeService.poke(3, 7) — @Transactional 시작              → 08 poke
     - getUser(3), getUser(7)
     - existsBetween(3,7) 친구 확인  (아니면 BusinessException(POKE_NOT_FRIEND) → 403)
     - 최근 콕 조회 → 1시간 이내면 BusinessException(POKE_COOLDOWN) → 429
     - pokeRepository.save(new Poke(...))  → INSERT
     - eventPublisher.publishEvent(new PokeEvent(7, "내닉네임"))   ← 아직 발송 X
   --- 트랜잭션 커밋 ---
6. 커밋 성공 → @TransactionalEventListener(AFTER_COMMIT) onPoke 트리거  → 11 push
     - @Async → 별도 스레드로 PushService.sendToUser(7, ...)
     - userId=7의 모든 device_token 으로 ApnsClient.send (enabled=false면 로그만)
     - 410/Unregistered 토큰은 삭제
7. 컨트롤러는 void + @ResponseStatus(204) → 클라이언트에 204 No Content (푸시와 무관하게 즉시)

* 중간에 BusinessException 발생 시:                          → 04 글로벌 예외 처리
   - 트랜잭션 롤백(INSERT 취소) → 이벤트도 발행 안 됨(커밋 전이라 푸시 X)
   - GlobalExceptionHandler 가 ErrorCode → {code,message} + 상태코드 응답
```

이 한 흐름 안에 **인증(필터/토큰) → 인가 → 검증 → 트랜잭션 → 도메인 규칙 → 이벤트 → 커밋 후 비동기 푸시 → 전역 예외 처리**가 다 들어있다. 이걸 말로 설명할 수 있으면 이 백엔드를 이해한 것.

---

## 예: 카카오 로그인 `POST /auth/kakao`

```
1. /auth/** 는 permitAll → 토큰 없이 통과                      → 02 SecurityConfig
2. AuthController.kakaoLogin(@Valid KakaoLoginRequest)         → 06 auth
3. AuthService.kakaoLogin
     - KakaoApiClient.fetchUser(카카오토큰)  → 카카오 /v2/user/me 호출(검증 겸)  → 06 auth
         (토큰 틀리면 카카오 401 → INVALID_KAKAO_TOKEN)
     - UserService.getOrCreateByKakao  → 있으면 조회, 없으면 가입(handle 발급)   → 05 user
     - issueTokens → access(1h) + refresh(30d) 발급                            → 03 보안
4. AuthResponse(access, refresh, user) JSON 반환 (200)
5. 앱: refresh를 Keychain 저장 → 이후 실행마다 POST /auth/refresh 로 자동 로그인  → 06 auth
```

---

## 면접 셀프체크 Q&A

- **OSIV를 왜 껐나?**
  영속성 컨텍스트를 트랜잭션 범위로 한정해 커넥션 점유 시간↓, LAZY 로딩 경계를 명확히. 컨트롤러에서 LAZY 접근하면 예외 나도록 일부러. → [01](01-build-and-config.md)

- **N+1은 어디서 막았나?**
  친구 목록(`findAllOf` fetch join), 친구들 요약(`findByUserInAndSummaryDate` IN 쿼리), 받은 요청(`findIncoming` fetch join). 친구 N명이어도 쿼리 2번. → [07](07-friend.md), [09](09-summary.md)

- **JWT access/refresh를 왜 나눴나?**
  access는 짧게(탈취 피해 최소화), refresh는 길게(자동 로그인). `type` 클레임으로 혼용 차단. → [03](03-security-jwt.md)

- **refresh 강제 로그아웃은?**
  현재 무상태라 불가. 보강하려면 refresh를 DB/Redis 저장 또는 `user.token_version` 도입. → [06](06-auth-kakao.md)

- **푸시를 왜 이벤트+AFTER_COMMIT+@Async로?**
  커밋된 사실만 알림(정합성), 요청 응답을 막지 않음(응답성), 푸시 실패가 본 트랜잭션에 영향 없음(격리). → [11](11-push-apns.md)

- **엔티티에 setter가 없는 이유?**
  불변/캡슐화. 상태 변경은 의미 있는 도메인 메서드(accept/decline/update/reassign)로만, dirty checking이 UPDATE. → [00](00-core-concepts.md), [05](05-user.md)

- **enum을 STRING으로 저장한 이유?**
  ORDINAL은 상수 순서가 바뀌면 기존 데이터 의미가 깨짐. → [07](07-friend.md)

- **DTO와 엔티티를 분리한 이유?**
  내부 모델(kakaoId 등) 노출 차단, API 스펙과 DB 모델의 독립 변경. → [05](05-user.md)

- **카카오 토큰을 서버가 어떻게 검증?**
  앱이 준 카카오 액세스토큰으로 서버가 직접 `/v2/user/me`를 호출 → 그 호출이 200이면 유효, 401이면 무효. 별도 시크릿 불필요. → [06](06-auth-kakao.md)

- **글로벌 예외 처리의 장점?**
  컨트롤러/서비스가 `throw new BusinessException(ErrorCode.X)` 한 줄만, 응답 변환은 `@RestControllerAdvice` 한 곳에서. 일관된 `{code,message}` + 상태코드. → [04](04-error-handling.md)

- **DB 제약을 앱과 중복으로 거는 이유?**
  앱은 친절한 에러(UX), DB는 무결성 최후 방어선(버그·동시성·직접쿼리 대비). → [12](12-database-schema.md)

---

축하한다. 여기까지 읽었으면 `server/`의 모든 코드를 줄 단위로 이해한 것이다. 처음으로 돌아가 [목차](README.md)에서 약한 부분을 다시 보자.
