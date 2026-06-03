# 03 — 보안 & JWT (security/)

> [← 02 설정 레이어](02-config-layer.md) · [목차](README.md) · 다음: [04 글로벌 예외 처리 →](04-error-handling.md)

대상 파일: `security/JwtTokenProvider.java`, `security/JwtAuthenticationFilter.java`

JWT는 `헤더.페이로드.서명` 3토막의 문자열. **페이로드(claims)** 에 정보를 담고, **서명**으로 위변조를 막는다. 서버만 서명 키를 알기 때문에 클라이언트가 내용을 위조할 수 없다.

---

## `JwtTokenProvider.java` — 토큰 발급/검증기

```java
@Component
public class JwtTokenProvider {
    private static final String CLAIM_TYPE = "type";
    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_REFRESH = "refresh";
    private final SecretKey key;
    private final long accessValidity;
    private final long refreshValidity;
```
- `type` 클레임으로 access/refresh를 구분(같은 키로 서명하지만 용도를 분리).

```java
    public JwtTokenProvider(JwtProperties props) {
        this.key = Keys.hmacShaKeyFor(props.secret().getBytes(StandardCharsets.UTF_8));
        this.accessValidity = props.accessTokenValidity();
        this.refreshValidity = props.refreshTokenValidity();
    }
```
- 생성자에서 설정(`JwtProperties`)을 받아, 비밀 문자열을 **HMAC-SHA 키 객체**로 만든다(`Keys.hmacShaKeyFor`).
- **HS256**: 대칭키(같은 키로 서명+검증). 키 길이 ≥ 32바이트 필요 → 설정의 secret이 충분히 길어야 함.

```java
    public String createAccessToken(Long userId) { return create(userId, TYPE_ACCESS, accessValidity); }
    public String createRefreshToken(Long userId) { return create(userId, TYPE_REFRESH, refreshValidity); }

    private String create(Long userId, String type, long validitySeconds) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))          // sub = 누구의 토큰인가(userId)
                .claim(CLAIM_TYPE, type)                   // 커스텀 클레임 type
                .issuedAt(Date.from(now))                  // iat 발급시각
                .expiration(Date.from(now.plusSeconds(validitySeconds))) // exp 만료시각
                .signWith(key)                             // 서명
                .compact();                                // 최종 문자열로 직렬화
    }
```
- `Jwts.builder()`: jjwt 라이브러리의 빌더로 토큰 조립.
- `subject`: 표준 클레임 `sub`. userId를 문자열로 넣어 "이 토큰의 주인" 표현.
- `expiration`: 만료시각. 검증 시 지나면 자동 예외.
- `signWith(key)`: 위 키로 서명.

### access / refresh를 왜 나눴나? (면접)
- **access**: 짧게(1h). 매 API 호출에 실려 다니므로 탈취돼도 피해 시간 최소화.
- **refresh**: 길게(30d). 자동 로그인용. 자주 안 쓰이고 갱신 때만 사용.
- `type` 클레임 가드로 **refresh 토큰으로 API를 호출하는 것을 차단**(아래 parse 참고).

```java
    public Long parseAccessToken(String token) {
        Claims claims = parse(token);
        if (!TYPE_ACCESS.equals(claims.get(CLAIM_TYPE, String.class)))
            throw new JwtException("access 토큰이 아닙니다");
        return Long.valueOf(claims.getSubject());
    }
    public Long parseRefreshToken(String token) { /* 동일하되 TYPE_REFRESH 체크 */ }
```
- 검증 + 파싱. 서명/만료를 통과해도 **type이 맞지 않으면 거부**.
- 마지막에 `sub`(userId)를 꺼내 반환 → 필터가 이 값을 인증 주체로 사용.

```java
    private Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)                // 이 키로 서명 검증
                .build()
                .parseSignedClaims(token)       // 서명 확인 + 만료 확인(실패 시 예외)
                .getPayload();                  // 통과하면 클레임 반환
    }
```
- 서명 불일치/만료/형식 오류면 전부 `JwtException` 계열 예외 → 호출부에서 잡아 401/INVALID로 처리.

---

## `JwtAuthenticationFilter.java` — 매 요청 1회 토큰 검사

```java
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
```
- **`OncePerRequestFilter`**: 한 요청에서 **딱 한 번만** 실행되도록 보장하는 시큐리티 필터 베이스(포워딩 등으로 중복 실행 방지).

```java
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) {
        String token = resolveToken(request);
        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                Long userId = tokenProvider.parseAccessToken(token);
                var authentication = new UsernamePasswordAuthenticationToken(userId, null, List.of());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (Exception e) {
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }
```
흐름:
1. 헤더에서 토큰 추출(`resolveToken`).
2. 토큰이 있고 아직 인증이 안 채워졌으면 → 파싱.
3. 성공하면 `UsernamePasswordAuthenticationToken`을 만들어 **principal(주체)=userId** 로 세팅.
   - 3번째 인자 `List.of()`는 권한 목록(여기선 역할 구분 없음).
   - **principal에 userId를 넣은 게 핵심** → 컨트롤러에서 `@AuthenticationPrincipal Long userId`로 바로 꺼냄.
4. `SecurityContextHolder`: 현재 요청의 인증 정보를 담는 **스레드 로컬 저장소**. 여기 넣으면 이 요청 동안 "인증된 사용자"로 취급.
5. 토큰이 틀리면 컨텍스트를 비우고 **그냥 통과**시킨다 → 보호 자원이면 뒤의 시큐리티가 401, 공개 자원이면 그대로 진행.
6. `filterChain.doFilter(...)`: 다음 필터로 넘김.

```java
    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION); // "Authorization"
        if (header != null && header.startsWith("Bearer "))
            return header.substring("Bearer ".length());
        return null;
    }
```
- `Authorization: Bearer <토큰>` 형식에서 토큰 부분만 잘라낸다. Bearer는 "이 토큰 소지자에게 권한 부여"라는 표준 스킴.

> **필터가 인증을 직접 401로 막지 않는 이유**: 책임 분리. 필터는 "토큰이 유효하면 신원을 채워줄 뿐", 막는 건 `SecurityConfig`의 인가 규칙 + EntryPoint가 한다. 덕분에 공개 엔드포인트는 토큰 없이도 통과한다.

---

> 다음: [04 글로벌 예외 처리 →](04-error-handling.md)
