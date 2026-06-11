# 01 — 빌드 & 설정

> [← 00 핵심 개념](00-core-concepts.md) · 다음: [02 설정 레이어 →](02-config-layer.md)

대상 파일: `build.gradle`, `src/main/resources/application.yml`, `RoutineCalendarServerApplication.java`

---

## `build.gradle`

프로젝트의 의존성·빌드 정의. Gradle은 빌드 도구(메이븐의 대안).

```gradle
plugins {
    id 'java'
    id 'org.springframework.boot' version '3.5.14'
    id 'io.spring.dependency-management' version '1.1.7'
}
```
- `java`: 자바 컴파일/테스트/패키징 기능.
- `org.springframework.boot`: 실행 가능한 jar 패키징(`bootJar`), `bootRun` 태스크 제공.
- `io.spring.dependency-management`: **BOM(버전 묶음)**. 아래에서 `spring-boot-starter-web` 등의 버전을 안 적어도 부트 버전(3.5.14)에 맞는 검증된 버전을 자동으로 맞춰준다.

```gradle
java {
    toolchain { languageVersion = JavaLanguageVersion.of(17) }
}
```
- 자바 17로 컴파일·실행 고정. (record, 텍스트블록 `"""` 사용 가능)

```gradle
configurations {
    compileOnly { extendsFrom annotationProcessor }
}
```
- Lombok을 `compileOnly`(컴파일에만)와 `annotationProcessor`(코드 생성기) 양쪽에 쓰기 위한 연결.

### dependencies 핵심

| 의존성 | 역할 |
|---|---|
| `spring-boot-starter-web` | 내장 톰캣 + Spring MVC(REST) |
| `spring-boot-starter-data-jpa` | JPA/Hibernate + 트랜잭션 |
| `spring-boot-starter-security` | 인증/인가 필터체인 |
| `spring-boot-starter-validation` | `@Valid`/`@NotBlank` 등 검증 |
| `spring-boot-starter-actuator` | `/actuator/health` 등 운영 엔드포인트 |
| `flyway-core` + `flyway-database-postgresql` | DB 마이그레이션 |
| `jjwt-api/impl/jackson :0.12.6` | JWT 생성/검증 (api는 컴파일용, impl/jackson은 런타임용) |
| `lombok` | 보일러플레이트 생성 |
| `postgresql` (runtimeOnly) | JDBC 드라이버 (실행 때만 필요) |
| `spring-boot-starter-test` | JUnit5 + AssertJ + Mockito |

- `implementation` vs `runtimeOnly`: 전자는 컴파일+실행 둘 다, 후자는 **실행 때만** 클래스패스에 둠(드라이버처럼 코드에서 직접 import 안 하는 것).
- jjwt가 3개로 쪼개진 이유: `api`(인터페이스, 컴파일 시 참조) / `impl`·`jackson`(실제 구현·JSON 처리, 런타임만) → 컴파일 의존을 최소화.

```gradle
tasks.named('test') { useJUnitPlatform() }
```
- 테스트를 JUnit5(Jupiter) 엔진으로 실행.

---

## `application.yml`

스프링이 기동 시 읽는 설정. `${ENV:기본값}` 문법은 **"환경변수 ENV가 있으면 그 값, 없으면 기본값"** 이라 로컬은 기본값으로 뜨고 운영은 환경변수로 덮어쓴다(= 민감값 외부 주입).

```yaml
spring:
  datasource:
    url: ${DB_URL:jdbc:postgresql://localhost:5432/routine_calendar}
    username: ${DB_USERNAME:routine}
    password: ${DB_PASSWORD:routine}
```
- DB 접속 정보. 로컬 기본은 도커 Postgres와 동일(routine/routine).

```yaml
  jpa:
    hibernate:
      ddl-auto: validate
    open-in-view: false
    properties:
      hibernate:
        format_sql: true
```
- **`ddl-auto: validate`**: Hibernate가 스키마를 절대 건드리지 않고, 시작할 때 엔티티와 실제 테이블이 일치하는지 **검증만** 한다. (스키마 생성은 Flyway 담당) → 운영 안전.
- **`open-in-view: false`** (OSIV 끔): 켜져 있으면 영속성 컨텍스트(DB 세션)가 컨트롤러/뷰 렌더링까지 열려 있어 **커넥션을 오래 잡고 LAZY N+1**이 숨는다. 끄면 트랜잭션(서비스) 안에서만 LAZY 로딩 → 경계가 분명. **면접 단골.**
- `format_sql: true`: 콘솔 SQL을 보기 좋게 들여쓰기.

```yaml
  flyway:
    enabled: true
    baseline-on-migrate: true
```
- Flyway 활성화. `baseline-on-migrate`: 이미 테이블이 있는 DB에 처음 붙어도 기준선을 잡고 진행.

```yaml
app:
  jwt:
    secret: ${JWT_SECRET:dev-only-secret-...0123456789abcdef}
    access-token-validity: ${JWT_ACCESS_VALIDITY:3600}      # 1시간(초)
    refresh-token-validity: ${JWT_REFRESH_VALIDITY:2592000} # 30일(초)
  kakao:
    user-info-uri: https://kapi.kakao.com/v2/user/me
  auth:
    dev-login-enabled: ${DEV_LOGIN_ENABLED:true}
  apns:
    enabled: ${APNS_ENABLED:false}
    ...
```
- `app.*`는 **우리가 정의한 커스텀 설정**. [02 설정 레이어](02-config-layer.md)의 `@ConfigurationProperties` 클래스들이 타입 세이프하게 받아간다.
- `secret`은 HS256 서명 키 → 최소 256bit(32바이트). 운영에선 반드시 환경변수.
- `dev-login-enabled` 운영에선 false여야 함(카카오 없이 로그인 막기).
- `apns.enabled=false`면 실제 발송 대신 로그만 → 키 없이 흐름 검증. ([11 push](11-push-apns.md) 참고)

```yaml
logging:
  level:
    org.hibernate.SQL: debug
    org.hibernate.orm.jdbc.bind: trace
```
- 실행되는 SQL과 **바인딩되는 파라미터 값(`?`에 들어가는 값)** 까지 로그로. 학습/디버깅용(운영에선 끔).

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info
```
- Actuator에서 `/actuator/health`, `/actuator/info`만 외부 노출(나머지 숨김 → 보안).

---

## `RoutineCalendarServerApplication.java` — 부트스트랩

```java
@SpringBootApplication
@ConfigurationPropertiesScan
public class RoutineCalendarServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(RoutineCalendarServerApplication.class, args);
    }
}
```

- **`@SpringBootApplication`** 은 사실 3개의 합성이다:
  - `@SpringBootConfiguration` (설정 클래스)
  - `@EnableAutoConfiguration` (클래스패스를 보고 톰캣·JPA·시큐리티 등 **자동 설정**)
  - `@ComponentScan` (이 패키지 이하의 `@Component/@Service/...`를 전부 스캔해 빈 등록)
- **`@ConfigurationPropertiesScan`**: `@ConfigurationProperties`가 붙은 record들(`JwtProperties` 등)을 자동으로 찾아 빈 등록. (없으면 각 클래스에 `@EnableConfigurationProperties` 등록 필요)
- `SpringApplication.run(...)`: 톰캣 띄우고, 빈 다 만들고, Flyway 마이그레이션 돌리고, 애플리케이션 가동.

---

> 다음: [02 설정 레이어 →](02-config-layer.md)
