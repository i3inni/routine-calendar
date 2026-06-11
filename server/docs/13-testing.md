# 13 — 테스트

> [← 12 DB 스키마](12-database-schema.md) · 다음: [14 요청 흐름 + 셀프체크 →](14-request-lifecycle.md)

대상 파일: `src/test/java/.../security/JwtTokenProviderTest.java`, `friend/service/FriendServiceTest.java`, `routine/service/RoutineServiceTest.java`, `RoutineCalendarServerApplicationTests.java`

---

## `JwtTokenProviderTest.java` — 단위 테스트

```java
class JwtTokenProviderTest {
    private final JwtProperties props =
            new JwtProperties("test-secret-test-secret-test-secret-0123456789", 3600, 2592000);
    private final JwtTokenProvider provider = new JwtTokenProvider(props);
```
- **단위 테스트**: 스프링 컨텍스트 없이 객체를 직접 `new` 해서 테스트(빠름). 생성자 주입([00 핵심개념](00-core-concepts.md))이라 가능.
- `JwtProperties`도 record라 그냥 `new`로 만들어 주입.

```java
    @Test
    void access_토큰_발급후_파싱하면_userId가_복원된다() {
        String token = provider.createAccessToken(42L);
        assertThat(provider.parseAccessToken(token)).isEqualTo(42L);   // 왕복 검증
    }
    @Test
    void refresh_토큰은_access로_파싱되지_않는다() {
        String refresh = provider.createRefreshToken(42L);
        assertThatThrownBy(() -> provider.parseAccessToken(refresh)).isInstanceOf(JwtException.class);  // type 가드
    }
    @Test
    void 위조된_토큰은_파싱에_실패한다() {
        assertThatThrownBy(() -> provider.parseAccessToken("not.a.jwt")).isInstanceOf(JwtException.class);
    }
```
- **`@Test`**(JUnit5): 테스트 메서드 표시.
- **AssertJ** 유창한 단언: `assertThat(...).isEqualTo(...)`, `assertThatThrownBy(...).isInstanceOf(...)`.
- 검증 포인트 3개 (모두 보안 핵심 동작):
  1. **발급↔파싱 왕복**(핵심 기능): createAccessToken으로 만든 토큰을 parseAccessToken하면 원래 userId가 나온다.
  2. **access/refresh 혼용 차단**(타입 가드, [03](03-security-jwt.md)): refresh 토큰을 access로 파싱하면 예외.
  3. **위조 토큰 거부**(서명 검증): 엉터리 문자열은 파싱 실패.
- 메서드명을 한글로 → 테스트가 곧 명세서 역할.

### 왜 이 클래스를 테스트했나?
JWT는 **인증의 신뢰 기반**이라 여기가 뚫리면 전부 무너진다. 외부 의존(DB·네트워크) 없이 순수 로직이라 단위 테스트로 빠르고 확실하게 보장하기 좋은 1순위 대상.

---

## `FriendServiceTest.java` / `RoutineServiceTest.java` — Mockito 단위 테스트

```java
@ExtendWith(MockitoExtension.class)
class RoutineServiceTest {
    @Mock RoutineRepository routineRepository;
    @InjectMocks RoutineService routineService;
    ...
}
```
- **`@ExtendWith(MockitoExtension.class)`**: 스프링 컨텍스트 없이 **Mock 의존성**만 주입해 서비스 로직을 빠르게 검증. `@Mock`(가짜 레포)·`@InjectMocks`(대상 서비스).
- **`FriendServiceTest`** — 자극하기([08](08-poke.md)) 핵심 분기:
  - `친구를_자극하면_기록을_남기고_이벤트가_발행된다` — `Poke` 저장 + `FriendNudgedEvent` 발행 검증(`verify`).
  - `친구가_아니면_자극할_수_없고_이벤트도_발행되지_않는다` — `NOT_FRIEND` 예외 + 이벤트 미발행.
  - `쿨다운_한도를_넘으면_자극할_수_없다` — 30분 2회 초과 시 `NUDGE_COOLDOWN`.
- **`RoutineServiceTest`** — 루틴 동기화([16 routine](16-routine.md)) **소유권·upsert**:
  - 생성→내 소유 저장, **남의 루틴 수정 차단**(조회 쿼리에 `user`를 박아 `ROUTINE_NOT_FOUND`), **삭제는 soft delete**, 완료 카운트 **있으면 갱신/없으면 생성**(upsert).
- Mockito 패턴: `when(...).thenReturn(...)`으로 레포 응답을 짜고, `verify(...)`로 저장/이벤트 호출을 확인. DB 없이 분기 로직만 빠르게.

---

## `RoutineCalendarServerApplicationTests.java` — 스모크 테스트

```java
@SpringBootTest
class RoutineCalendarServerApplicationTests {
    @Test
    void contextLoads() {}
}
```
- **`@SpringBootTest`**: 전체 스프링 컨텍스트를 띄운다.
- `contextLoads()`: 본문이 비어 있어도 **모든 빈이 정상적으로 생성·주입되는지**(빈 충돌, 설정 오류, 순환참조 없는지) 검증하는 스모크 테스트. 컨텍스트 로딩 자체가 실패하면 이 테스트가 깨진다.

---

## 더 작성한다면 (학습 확장)

- `@DataJpaTest` + Testcontainers(Postgres)로 레포지토리 쿼리(특히 `findAllOf` fetch join, 부분 유니크 인덱스, `findNudgeStats` group by) 검증.
- `@WebMvcTest` + `@MockBean`으로 컨트롤러 단위 테스트(검증 실패 400, 인증 없을 때 401).
- `FriendService`의 친구요청 분기(자기자신/이미친구/역방향 자동성사/중복요청)·카카오 친구찾기 매칭을 Mockito로 확장.

---

> 다음: [14 요청 흐름 + 셀프체크 →](14-request-lifecycle.md)
