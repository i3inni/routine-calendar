# 00 — 먼저 알아야 할 핵심 개념

> 다음: [01 빌드 & 설정 →](01-build-and-config.md)

코드를 읽기 전에 **반복적으로 등장하는 개념**을 먼저 정리한다. 이후 모든 문서는 이걸 안다고 가정한다.

---

## IoC 컨테이너 / DI (의존성 주입)

- 스프링은 객체(빈, Bean)를 **내가 직접 `new` 하지 않고** 컨테이너가 만들어 관리한다 → **IoC(제어의 역전)**.
- 어떤 클래스가 다른 객체를 필요로 하면, 생성자 파라미터로 "나 이거 필요해"라고 선언만 하면 컨테이너가 알아서 넣어준다 → **DI(의존성 주입)**.
- 이 프로젝트는 전부 **생성자 주입(constructor injection)** 을 쓴다. (필드 주입 `@Autowired` 안 씀)
  - 장점: ① 불변(`final`) 가능 ② 테스트에서 `new Service(mock)`으로 쉽게 주입 ③ 순환참조를 기동 시점에 발견.

```java
// 이 프로젝트의 전형적인 생성자 주입
@Service
public class UserService {
    private final UserRepository userRepository;       // final
    public UserService(UserRepository userRepository) { // 생성자 1개 → @Autowired 생략 가능
        this.userRepository = userRepository;
    }
}
```

---

## 빈으로 등록되는 어노테이션 (스테레오타입)

| 어노테이션 | 의미 | 예시 |
|---|---|---|
| `@Component` | 가장 일반적인 빈 | `JwtTokenProvider`, `JwtAuthenticationFilter`, `KakaoApiClient`, `ApnsClient`, `PushEventListener` |
| `@Service` | 비즈니스 로직 계층 (`@Component`의 특수형) | `UserService`, `AuthService`, `FriendService`, ... |
| `@RestController` | 웹 요청 수신 + JSON 응답 | 모든 `*Controller` |
| `@Configuration` | 설정/빈 정의용 | `SecurityConfig`, `AsyncConfig` |
| `@Repository` | DB 접근 계층 — Spring Data JPA가 인터페이스만 보고 구현체를 자동 생성하므로 직접 안 붙임 |

> 기능상 `@Service`/`@Component`/`@RestController`는 전부 "빈으로 등록해라"로 **동일**하다. 이름이 다른 이유는 **계층(역할)을 코드로 드러내기 위함** + AOP 대상 구분.

---

## 계층 구조 (Layered Architecture)

```
HTTP 요청
  └→ Controller   (요청 받기, 검증, DTO 변환, 응답 코드)
       └→ Service  (@Transactional 비즈니스 로직 — "진짜 일")
            └→ Repository (DB 입출력 — Spring Data JPA)
                 └→ DB
```

- **Controller는 얇게**: 입력 받고 서비스 호출하고 결과 돌려주기만.
- **Service에 로직**: 트랜잭션 경계가 여기. "친구인지 확인 → 쿨다운 검사 → 저장 → 이벤트 발행" 같은 흐름.
- **Entity는 DB 테이블 1:1, DTO는 통신용**. 둘을 분리해 내부 모델이 API에 새어나가지 않게 한다.

---

## record (자바 16+)

```java
public record Foo(int a, String b) {}
```
한 줄이면 **불변 필드 + 생성자 + getter(`a()`,`b()`) + equals/hashCode/toString** 을 자동 생성.

- 이 프로젝트는 **DTO와 설정(Properties)을 전부 record**로 만든다. 값만 담고 바뀌지 않으니 딱 맞다.
- getter가 `getA()`가 아니라 `a()` 형태인 점에 주의.

---

## Lombok

컴파일 시점에 코드를 생성해주는 라이브러리 (`build.gradle`의 `annotationProcessor`로 동작).

| 어노테이션 | 생성물 |
|---|---|
| `@Getter` | 모든 필드 getter |
| `@NoArgsConstructor(access=PROTECTED)` | 기본 생성자 (JPA 필수, 외부 차단) |
| `@Builder` | 빌더 패턴 |
| `@Slf4j` | `log` 로거 필드 |

엔티티/컴포넌트에서 보일러플레이트를 줄이는 데 사용. **setter는 일부러 안 만든다**(불변/캡슐화).

---

## JPA / Hibernate

- **JPA**는 "자바 객체 ↔ DB 테이블"을 매핑하는 **표준(인터페이스)**, **Hibernate**는 그 구현체.
- 핵심 메커니즘 **dirty checking(변경 감지)**:

```java
@Transactional
public void acceptRequest(Long meId, Long requestId) {
    FriendRequest request = loadPendingRequestForMe(meId, requestId);
    request.accept();   // 엔티티 상태만 바꿈 — save() 호출 없음!
}                       // 트랜잭션 커밋 시 Hibernate가 변경을 감지해 UPDATE SQL 자동 실행
```

> 이 프로젝트 곳곳에서 `save()` 없이 엔티티 상태만 바꾸는 코드(`accept()`, `decline()`, `update()`, `reassign()`)의 정체가 바로 이것이다. **트랜잭션 안에서 영속 상태인 엔티티를 바꾸면 자동 반영**.

---

## 자주 쓰는 JPA 어노테이션 미리보기

| 어노테이션 | 의미 |
|---|---|
| `@Entity` / `@Table(name=)` | 영속 객체 / 매핑 테이블명 |
| `@Id` / `@GeneratedValue(IDENTITY)` | PK / DB 자동증가에 위임 |
| `@Column(name, nullable, unique, length)` | 컬럼 매핑 |
| `@ManyToOne(fetch=LAZY)` / `@JoinColumn` | 다대일 연관 / FK 컬럼 |
| `@Enumerated(EnumType.STRING)` | enum을 문자열로 저장 |
| `@CreationTimestamp` / `@UpdateTimestamp` | 생성/수정 시각 자동 |
| `@JdbcTypeCode(SqlTypes.JSON)` | 컬렉션을 JSONB로 |

각각은 등장하는 도메인 문서에서 한 줄씩 자세히 설명한다.

---

> 다음: [01 빌드 & 설정 →](01-build-and-config.md)
