# 02 — 설정 레이어 (config/)

> [← 01 빌드 & 설정](01-build-and-config.md) · [목차](README.md) · 다음: [03 보안 & JWT →](03-security-jwt.md)

대상 파일: `config/JwtProperties.java`, `KakaoProperties.java`, `AuthProperties.java`, `push/ApnsProperties.java`, `config/AsyncConfig.java`, `config/SecurityConfig.java`

---

## Properties 클래스들 — 설정을 타입 세이프하게 받기

```java
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(String secret, long accessTokenValidity, long refreshTokenValidity) {}
```
- **`@ConfigurationProperties(prefix=...)`**: `application.yml`의 `app.jwt.*`를 이 record에 바인딩.
- **relaxed binding**: yml의 `access-token-validity`(케밥케이스)가 자바의 `accessTokenValidity`(카멜케이스)로 자동 매핑.
- record라 불변. `props.secret()`처럼 접근.
- 효과: 설정을 문자열 키(`@Value("${...}")`)로 흩뿌리지 않고 **타입 안전한 객체 하나**로 다룬다.

같은 패턴의 클래스들:

| 클래스 | prefix | 필드 |
|---|---|---|
| `JwtProperties` | `app.jwt` | secret, accessTokenValidity, refreshTokenValidity |
| `KakaoProperties` | `app.kakao` | userInfoUri |
| `AuthProperties` | `app.auth` | devLoginEnabled |
| `ApnsProperties` (push 패키지) | `app.apns` | enabled, useSandbox, teamId, keyId, bundleId, privateKey |

이 record들은 [01](01-build-and-config.md)에서 본 `@ConfigurationPropertiesScan` 덕에 자동으로 빈 등록되어, 필요한 곳에 생성자 주입된다.

---

## `AsyncConfig.java`

```java
@Configuration
@EnableAsync
public class AsyncConfig {}
```
- **`@EnableAsync`**: `@Async`가 붙은 메서드를 **별도 스레드풀에서 실행**되게 활성화. 푸시 발송([11 push](11-push-apns.md))을 요청 스레드와 분리하기 위함.
- 이게 없으면 `@Async`는 무시되고 그냥 동기 실행된다.

---

## `SecurityConfig.java` — 보안 정책의 중심

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    private final JwtAuthenticationFilter jwtAuthenticationFilter; // 생성자 주입
```
- **`@EnableWebSecurity`**: 스프링 시큐리티 필터체인을 켠다.
- 우리가 만든 JWT 필터([03](03-security-jwt.md))를 주입받아 체인에 끼운다.

```java
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .formLogin(form -> form.disable())
            .httpBasic(basic -> basic.disable())
```
- **`@Bean`**: 이 메서드의 반환 객체를 빈으로 등록. 시큐리티 필터체인을 우리가 직접 구성.
- `csrf.disable()`: CSRF는 브라우저 쿠키 세션 기반 공격 방어책. 우리는 **쿠키 세션을 안 쓰고 토큰(Authorization 헤더)** 만 쓰므로 끈다.
- `formLogin/httpBasic disable()`: 스프링 기본 로그인 폼/팝업 끔(우리는 JWT로 직접 처리).

```java
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
```
- **STATELESS**: 서버가 세션을 만들지 않는다. 매 요청이 토큰만으로 자기 신원을 증명 → 서버 메모리에 로그인 상태 보관 X → **수평 확장(서버 여러 대)** 에 유리. 면접 핵심.

```java
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/auth/**", "/api/ping", "/actuator/health").permitAll()
                .anyRequest().authenticated())
```
- 인가 규칙: `/auth/**`(로그인), 핑, 헬스는 **누구나(permitAll)**. 그 외 모든 요청은 **인증 필요(authenticated)**.

```java
            .exceptionHandling(ex -> ex.authenticationEntryPoint(unauthorizedEntryPoint()))
```
- 인증 안 된 채 보호 자원 접근 시, 기본은 로그인 페이지로 **302 리다이렉트**. API 서버엔 안 맞으므로 **401만 깔끔히** 주도록 EntryPoint 교체.

```java
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
```
- **우리의 JWT 필터를 표준 로그인 필터 앞에** 끼운다. 요청이 들어오면 우리 필터가 먼저 토큰을 보고 인증을 채워 넣는다. ([03](03-security-jwt.md)에서 필터 내부 설명)

```java
    @Bean
    public AuthenticationEntryPoint unauthorizedEntryPoint() {
        return new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED); // 401
    }
```
- 인증 실패 시 본문 없이 상태코드 401만 반환.

> **예외 처리 책임 분리**: 인증 자체 실패(401)는 컨트롤러 도달 전 필터 단계라 여기 EntryPoint가 담당하고, 컨트롤러 안에서 터지는 비즈니스 예외는 [04 글로벌 예외 처리](04-error-handling.md)가 담당한다.

---

> 다음: [03 보안 & JWT →](03-security-jwt.md)
