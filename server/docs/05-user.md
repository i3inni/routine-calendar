# 05 — user 도메인

> [← 04 글로벌 예외 처리](04-error-handling.md) · 다음: [06 auth 카카오 로그인 →](06-auth-kakao.md)

대상 파일: `user/domain/User.java`, `user/repository/UserRepository.java`, `user/service/`(`UserService`, `UserPurgeScheduler`), `user/dto/`(`UserResponse`, `MeDtos`), `user/controller/MeController.java`

신원은 **카카오 또는 애플** 로그인으로 잡고(둘 중 하나로 식별), 친구추가는 공개 `handle`로 검색한다.

---

## `User.java` — 엔티티

```java
@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {
```
- **`@Entity`**: 이 클래스를 DB 테이블과 매핑되는 영속 객체로 선언.
- **`@Table(name="users")`**: 매핑할 테이블명(`user`는 SQL 예약어라 복수형 `users`).
- **`@Getter`**: 롬복 getter. **setter는 일부러 안 만든다** → 아무 데서나 필드를 못 바꾸게(불변성/캡슐화). 변경은 의미 있는 메서드(`updateProfile`)로만.
- **`@NoArgsConstructor(access = PROTECTED)`**: JPA는 **기본 생성자 필수**(리플렉션으로 객체 생성). 하지만 외부에서 빈 `User()`를 못 만들게 `protected`로 숨긴다. 생성은 빌더로만.

```java
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
```
- **`@Id`**: 기본키(PK).
- **`@GeneratedValue(IDENTITY)`**: PK 생성을 **DB의 자동증가(Postgres IDENTITY)** 에 위임. INSERT 시 DB가 채워주고 그 값을 다시 받아온다.

```java
    @Column(name = "kakao_id", unique = true)            // 애플 전용 유저면 null
    private Long kakaoId;
    @Column(name = "apple_id", unique = true, length = 255)  // 카카오 전용 유저면 null
    private String appleId;
    @Column(nullable = false, unique = true, length = 30)
    private String handle;
    @Column(nullable = false, length = 50)
    private String nickname;
    @Column(name = "profile_image_url", length = 500)
    private String profileImageUrl;
    @Column(name = "deletion_requested_at")              // null = 정상 계정
    private Instant deletionRequestedAt;
```
- **`@Column`**: 컬럼 매핑. `name`(컬럼명), `nullable`(기본 true), `unique`(유니크), `length`(VARCHAR 길이).
- **신원은 `kakaoId` 또는 `appleId` 둘 중 하나**(둘 다 nullable + unique). 카카오/애플 어느 쪽으로 가입해도 한 행. (애플 로그인 추가는 [12 V2 마이그레이션](12-database-schema.md))
- `handle`: 친구추가용 공개 ID(유니크). `deletionRequestedAt`: 계정 삭제 유예(아래).

```java
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
```
- **`@CreationTimestamp`**(Hibernate): INSERT 시 현재 시각 자동 기록. `updatable=false`로 이후 수정 불가.
- **`@UpdateTimestamp`**(Hibernate): UPDATE 때마다 현재 시각 자동 갱신.
- `Instant`: UTC 기준 절대 시각 타입(타임존 혼동 없음).

```java
    @Builder
    public User(Long kakaoId, String appleId, String handle, String nickname, String profileImageUrl) { ... }
```
- **`@Builder`**(롬복): `User.builder().kakaoId(..).handle(..)...build()` 빌더 패턴. 인자가 많을 때 가독성↑, 순서 실수↓. id/타임스탬프는 자동 생성이라 빌더 대상에서 제외.

```java
    public void updateProfile(String nickname, String profileImageUrl) { ... }
    public void updateNickname(String nickname) { ... }
    public void linkKakao(Long kakaoId)  { this.kakaoId = kakaoId; }          // 애플 계정에 카카오 연동(친구찾기)
    public void requestDeletion()        { this.deletionRequestedAt = Instant.now(); }  // 삭제 예약
    public void cancelDeletion()         { this.deletionRequestedAt = null; }           // 재로그인 시 취소
```
- 상태 변경을 **의미 있는 도메인 메서드**로만 노출(setter 없음). 트랜잭션 안에서 호출하면 dirty checking으로 UPDATE([00 핵심개념](00-core-concepts.md) 참고).
- `linkKakao`: 애플로 가입한 유저가 카카오 친구찾기([07](07-friend.md))를 쓸 때 `kakaoId`를 채워 넣는다.

---

## `UserRepository.java` — Spring Data JPA

```java
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByKakaoId(Long kakaoId);
    List<User> findByKakaoIdIn(Collection<Long> kakaoIds);          // 카카오 친구찾기 매칭(07)
    Optional<User> findByAppleId(String appleId);                   // 애플 로그인
    Optional<User> findByHandle(String handle);
    boolean existsByHandle(String handle);
    List<User> findByDeletionRequestedAtBefore(Instant cutoff);     // 영구삭제 대상(스케줄러)
}
```
- **`JpaRepository<User, Long>`**: 엔티티=User, PK타입=Long. 상속만 해도 `save/findById/findAll/delete...` 기본 CRUD가 공짜.
- **쿼리 메서드**: 메서드 이름을 규칙대로 지으면 Spring Data가 **이름을 파싱해 SQL을 자동 생성**.
  - `findByKakaoId` → `WHERE kakao_id = ?`, `findByKakaoIdIn` → `WHERE kakao_id IN (?)`
  - `findByAppleId` → `WHERE apple_id = ?`, `findByDeletionRequestedAtBefore` → `WHERE deletion_requested_at < ?`
- `Optional<User>`: 결과가 없을 수 있음을 타입으로 표현(NPE 방지). `.orElseThrow(...)`로 처리.
- 구현 클래스를 우리가 안 짠다 → 스프링이 런타임에 프록시 구현체를 만들어 빈 등록.

---

## `UserService.java` — 유저 생성 + handle 발급

```java
@Service
public class UserService {
    private static final char[] HANDLE_CHARS = "ABCDEFGHJKMNPQRSTUVWXYZ23456789".toCharArray();
    private static final int HANDLE_LENGTH = 8;
    private static final SecureRandom RANDOM = new SecureRandom();
```
- 친구코드용 문자셋에서 **헷갈리는 문자(0/O, 1/I/L) 제외** → 사람이 불러주거나 입력할 때 오류↓ (UX 디테일).
- **`SecureRandom`**: 일반 `Random`보다 예측 어려운 난수(보안용). 코드 추측 공격 방지.

```java
    @Transactional
    public User getOrCreateByKakao(Long kakaoId, String nickname, String profileImageUrl) {
        return userRepository.findByKakaoId(kakaoId)
                .orElseGet(() -> userRepository.save(User.builder()
                        .kakaoId(kakaoId)
                        .handle(generateUniqueHandle())
                        .nickname(nickname != null ? nickname : "사용자")
                        .profileImageUrl(profileImageUrl)
                        .build()));
    }
```
- **`@Transactional`**: 이 메서드 전체를 하나의 DB 트랜잭션으로 묶음. 중간에 런타임 예외 나면 전부 롤백.
- 로직: 카카오ID로 기존 회원 조회 → 있으면 그대로, **없으면(`orElseGet`) 새로 만들어 저장**. = "로그인 = 가입"을 한 번에(첫 로그인 시 자동 가입).
- `orElseGet(() -> ...)`: Optional이 비었을 때만 람다 실행(이미 있으면 새로 안 만듦).

```java
    private String generateUniqueHandle() {
        for (int attempt = 0; attempt < 10; attempt++) {
            String handle = randomHandle();
            if (!userRepository.existsByHandle(handle)) return handle;
        }
        throw new IllegalStateException("고유 handle 생성에 실패했습니다.");
    }
    private String randomHandle() {
        StringBuilder sb = new StringBuilder(HANDLE_LENGTH);
        for (int i = 0; i < HANDLE_LENGTH; i++)
            sb.append(HANDLE_CHARS[RANDOM.nextInt(HANDLE_CHARS.length)]);
        return sb.toString();
    }
```
- 랜덤 handle을 만들어 **DB 중복 확인** 후 반환. 충돌하면 최대 10번 재시도(8자/31문자 조합이라 충돌 거의 없음). 그래도 다 실패하면 예외.

---

## `UserResponse.java` — DTO

```java
public record UserResponse(Long id, String handle, String nickname, String profileImageUrl) {
    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getHandle(), user.getNickname(), user.getProfileImageUrl());
    }
}
```
- 엔티티를 그대로 응답에 쓰지 않고 **필요한 필드만 추린 DTO**로 변환(kakaoId/타임스탬프 같은 내부 정보 노출 차단).
- `from(User)`: 엔티티→DTO 정적 팩토리(변환 책임을 DTO가 가짐).

---

## `MeController.java`

```java
@RestController
public class MeController {
    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return UserResponse.from(user);
    }
}
```
- **`@RestController`**: `@Controller + @ResponseBody`. 반환 객체를 **자동으로 JSON 직렬화**(Jackson)해 본문에 씀.
- **`@GetMapping("/me")`**: HTTP GET `/me`에 매핑.
- **`@AuthenticationPrincipal Long userId`**: 필터([03](03-security-jwt.md))가 SecurityContext에 넣어둔 principal(userId)을 파라미터로 바로 주입 → 컨트롤러가 토큰을 직접 파싱할 필요 없음.
- 흐름: userId로 조회 → 없으면 USER_NOT_FOUND(글로벌 핸들러가 404) → 있으면 DTO 변환 후 반환(200).

### 닉네임 변경 — `PATCH /me`
```java
@PatchMapping("/me")
public UserResponse updateMe(@AuthenticationPrincipal Long userId,
                             @Valid @RequestBody UpdateNicknameRequest request) {
    User user = userService.updateNickname(userId, request.nickname());
    return UserResponse.from(user);
}
```
- 친구에게 보이는 이름(`nickname`)을 바꾼다. `UpdateNicknameRequest(@NotBlank @Size(max=50) String nickname)`.
- `UserService.updateNickname`은 엔티티의 `updateNickname()`만 호출 → **dirty checking으로 UPDATE**(`save()` 불필요).

### 계정 삭제(3일 유예) — `DELETE /me` ⭐
App Store는 **로그인(계정 생성)이 있으면 앱 안에서 계정 삭제도 제공**하라고 요구한다(Guideline 5.1.1). 즉시 삭제 대신 **유예(soft delete)** 패턴.
```java
@DeleteMapping("/me")
public DeletionResponse deleteMe(@AuthenticationPrincipal Long userId) {
    Instant scheduledAt = userService.requestDeletion(userId);   // deletion_requested_at = now
    return new DeletionResponse(scheduledAt);                     // 유예 종료(영구삭제 예정) 시각
}
```
동작 3단계:
1. **요청**: `requestDeletion`이 `deletion_requested_at`만 기록(실제 삭제 X) → 앱은 로그아웃.
2. **취소**: 유예 내 재로그인하면 `getOrCreateBy*`가 `reactivateIfPending`으로 그 값을 비워 **자동 취소**.
   ```java
   private User reactivateIfPending(User user) {
       if (user.getDeletionRequestedAt() != null) user.cancelDeletion();   // dirty checking
       return user;
   }
   ```
3. **영구 삭제**: 유예(3일) 지나면 스케줄러가 일괄 삭제(아래).

### 영구 삭제 스케줄러 — `UserPurgeScheduler`
```java
@Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")   // 매일 04시 KST
@Transactional
public void purgeExpiredAccounts() {
    Instant cutoff = Instant.now().minus(UserService.DELETION_GRACE);   // 3일
    List<User> expired = userRepository.findByDeletionRequestedAtBefore(cutoff);
    userRepository.deleteAll(expired);   // 친구/요약/콕/토큰은 DB ON DELETE CASCADE로 함께 삭제
}
```
- **`@Scheduled`**(+ `@EnableScheduling`, [02](02-config-layer.md)): 주기 실행. `cron` 6필드(초 분 시 일 월 요일) + `zone`.
- **CASCADE 활용**: users 한 행 삭제로 연관 데이터 전부 정리([12 스키마](12-database-schema.md)의 `ON DELETE CASCADE`). 별도 삭제 코드 불필요.
- **동시성/안전**: `@Transactional`로 일괄 삭제. soft delete라 유예 중 실수 복구 가능.

### 내 정보 엔드포인트 요약
| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/me` | 내 정보 |
| PATCH | `/me` | `{ nickname }` 닉네임 변경 |
| DELETE | `/me` | 계정 삭제 예약(3일 유예, 재로그인 취소) |

---

> 다음: [06 auth 카카오 로그인 →](06-auth-kakao.md)
