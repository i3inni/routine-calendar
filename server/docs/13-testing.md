# 13 — 테스트

> [← 12 DB 스키마](12-database-schema.md) · [목차](README.md) · 다음: [14 요청 흐름 + 셀프체크 →](14-request-lifecycle.md)

대상 파일: `src/test/java/.../security/JwtTokenProviderTest.java`, `RoutineCalendarServerApplicationTests.java`

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

- `@DataJpaTest` + Testcontainers(Postgres)로 레포지토리 쿼리(특히 `findAllOf` fetch join, 부분 유니크 인덱스 동작) 검증.
- `@WebMvcTest` + `@MockBean`으로 컨트롤러 단위 테스트(검증 실패 400, 인증 없을 때 401).
- `FriendService`의 분기(자기자신/이미친구/역방향 자동성사/중복요청)를 Mockito로.

---

> 다음: [14 요청 흐름 + 셀프체크 →](14-request-lifecycle.md)
