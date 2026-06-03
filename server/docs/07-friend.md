# 07 — friend 도메인

> [← 06 auth 카카오 로그인](06-auth-kakao.md) · [목차](README.md) · 다음: [08 poke 도메인 →](08-poke.md)

대상 파일: `friend/Friendship.java`, `FriendshipRepository.java`, `FriendRequest.java`, `FriendRequestStatus.java`, `FriendRequestRepository.java`, `FriendDtos.java`, `FriendRequestedEvent.java`, `FriendService.java`, `FriendController.java`

가장 비즈니스 로직이 풍부한 도메인. 친구 요청→수락→목록→끊기. **N+1 방지**의 모범 사례가 여기 있다.

---

## `Friendship.java` — 정규화된 친구 관계

```java
@Entity @Table(name = "friendships")
public class Friendship {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "user_low_id", nullable = false) private User userLow;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "user_high_id", nullable = false) private User userHigh;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
```
- **`@ManyToOne`**: 다대일 연관관계. 친구관계 여러 개가 한 User를 가리킬 수 있음. DB에선 FK 컬럼(`user_low_id`)으로 표현.
- **`fetch = FetchType.LAZY`**: 지연 로딩. Friendship 조회 시 User를 **즉시 join하지 않고**, 실제 `userLow`에 접근하는 순간 별도 쿼리로 로딩 → 불필요한 join 방지. (단, 남발하면 N+1 → 목록 조회는 아래 fetch join 사용)
- **`@JoinColumn`**: 이 연관을 매핑할 FK 컬럼명.
- **정규화 핵심**: A-B와 B-A를 두 행으로 두면 중복/정합성 문제. 그래서 **항상 id 작은 쪽=userLow, 큰 쪽=userHigh** 한 행으로만 저장.

```java
    private Friendship(User userLow, User userHigh) { ... }   // private 생성자
    public static Friendship between(User a, User b) {
        if (a.getId() < b.getId()) return new Friendship(a, b);
        return new Friendship(b, a);
    }
```
- 생성자를 `private`로 막고 **정적 팩토리 `between`** 으로만 생성 → 순서 정렬 규칙을 강제(외부에서 순서 틀리게 못 만듦). DB의 `CHECK (user_low_id < user_high_id)` 제약([12 스키마](12-database-schema.md))과 이중 안전장치.

---

## `FriendshipRepository.java` — @Query + fetch join

```java
    @Query("""
            select f from Friendship f
            join fetch f.userLow
            join fetch f.userHigh
            where f.userLow = :user or f.userHigh = :user
            """)
    List<Friendship> findAllOf(@Param("user") User user);
```
- **`@Query`**: 메서드 이름 규칙으로 표현 안 되는 쿼리를 **JPQL**(엔티티 기준 쿼리 언어)로 직접 작성. 텍스트블록(`"""`)으로 가독성.
- **`join fetch`**: 연관 엔티티(userLow/userHigh)를 **한 번의 쿼리로 함께 로딩**. LAZY라도 즉시 가져옴 → **N+1 문제 해결**.
  - N+1: 친구 N명을 가져온 뒤 각자 User를 따로 조회하면 1+N번 쿼리. fetch join이면 1번.
- `:user` / `@Param("user")`: 이름 기반 바인딩 파라미터.

```java
    @Query("""select count(f) > 0 from Friendship f
             where (f.userLow = :a and f.userHigh = :b) or (f.userLow = :b and f.userHigh = :a)""")
    boolean existsBetween(@Param("a") User a, @Param("b") User b);

    @Query("""select f from Friendship f
             where (f.userLow = :a and f.userHigh = :b) or (f.userLow = :b and f.userHigh = :a)""")
    Optional<Friendship> findBetween(@Param("a") User a, @Param("b") User b);
```
- 정규화로 순서가 정해져 있지만, **호출부가 순서를 신경 안 쓰게** 양방향(`a-b` or `b-a`)을 다 검사. 캡슐화.
- `existsBetween`: 친구 여부만(가벼움). `findBetween`: 끊을 때 행 자체가 필요.

---

## `FriendRequest.java` — 요청 + 상태

```java
@Entity @Table(name = "friend_requests")
public class FriendRequest {
    @ManyToOne(LAZY) requester;   // 보낸 사람
    @ManyToOne(LAZY) addressee;   // 받는 사람
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=20) private FriendRequestStatus status;
    @CreationTimestamp ... createdAt;
    @Column(name="responded_at") private Instant respondedAt;  // 수락/거절 시각
```
- **`@Enumerated(EnumType.STRING)`**: enum을 DB에 **문자열("PENDING")로 저장**. `ORDINAL`(0,1,2 숫자)은 상수 순서가 바뀌면 데이터 의미가 깨지므로 **STRING이 안전**(면접 포인트).

```java
    public FriendRequest(User requester, User addressee) {
        this.requester = requester; this.addressee = addressee;
        this.status = FriendRequestStatus.PENDING;  // 생성 시 항상 대기 상태
    }
    public void accept()  { this.status = ACCEPTED; this.respondedAt = Instant.now(); }
    public void decline() { this.status = DECLINED; this.respondedAt = Instant.now(); }
```
- **상태 전이를 도메인 메서드로** 표현(`accept`/`decline`). 트랜잭션 안에서 호출하면 dirty checking으로 자동 UPDATE — `save()` 명시 호출 없이 반영.

### `FriendRequestStatus.java`
```java
public enum FriendRequestStatus { PENDING, ACCEPTED, DECLINED }
```
- 요청의 생애주기. 단순 enum.

---

## `FriendRequestRepository.java`

```java
    @Query("""select fr from FriendRequest fr join fetch fr.requester
             where fr.addressee = :me and fr.status = :status""")
    List<FriendRequest> findIncoming(@Param("me") User me, @Param("status") FriendRequestStatus status);

    Optional<FriendRequest> findByRequesterAndAddresseeAndStatus(User requester, User addressee, FriendRequestStatus status);
```
- `findIncoming`: 내가 받은 특정 상태 요청 + 보낸 사람(requester) fetch join(N+1 방지). 목록에 보낸 사람 닉네임/handle을 보여줘야 하므로.
- 두번째는 쿼리 메서드: `requester AND addressee AND status`로 특정 방향의 요청 1건 조회(중복 요청/역방향 확인용).

---

## `FriendDtos.java`

```java
    public record SendFriendRequest(@NotBlank String handle) {}        // 친구 요청: 상대 handle
    public record PokeRequest(@NotNull Long toUserId) {}               // 콕 대상 (poke 도메인이 사용)
    public record FriendRequestResponse(Long requestId, Long fromUserId, String fromHandle,
                                        String fromNickname, String fromProfileImageUrl, Instant createdAt) {}
    public record FriendResponse(Long userId, String handle, String nickname, String profileImageUrl,
                                 int doneToday, int totalToday, int streak, List<String> done, List<String> remaining) {}
```
- 요청은 handle/userId만 받고, 응답은 iOS의 `Friend` 모델에 맞춰 **친구 정보 + 오늘 요약**을 한 번에 내려줌.
- `@NotNull`은 "null만 금지"(`@NotBlank`는 문자열 공백까지 금지) — 타입에 맞게 구분 사용.

---

## `FriendRequestedEvent.java`

```java
public record FriendRequestedEvent(Long toUserId, String fromNickname) {}
```
- **도메인 이벤트**. "친구 요청이 일어났다"는 사실을 값으로 표현. 푸시 리스너가 커밋 후 비동기 처리([11 push](11-push-apns.md)). 서비스가 푸시 코드를 직접 몰라도 되게 **느슨한 결합**.

---

## `FriendService.java` — 흐름의 핵심

생성자 주입: `UserRepository`, `FriendshipRepository`, `FriendRequestRepository`, `DailySummaryRepository`, **`ApplicationEventPublisher`**(이벤트 발행기).

### (1) 친구 목록 + 오늘 요약 — N+1 방지의 정수
```java
    @Transactional(readOnly = true)
    public List<FriendResponse> listFriends(Long meId) {
        User me = getUser(meId);
        List<User> friends = friendshipRepository.findAllOf(me).stream()
                .map(f -> other(f, me)).toList();
        if (friends.isEmpty()) return List.of();

        LocalDate today = AppTime.today();
        Map<Long, DailySummary> summaries = dailySummaryRepository
                .findByUserInAndSummaryDate(friends, today).stream()
                .collect(Collectors.toMap(s -> s.getUser().getId(), Function.identity()));

        return friends.stream().map(u -> toFriendResponse(u, summaries.get(u.getId()))).toList();
    }
```
- **`@Transactional(readOnly = true)`**: 읽기 전용 트랜잭션 → Hibernate가 변경감지 스냅샷을 생략해 **성능↑**, 실수로 쓰기 방지.
- 흐름:
  1. `findAllOf(me)` (fetch join 1쿼리) → 내 친구관계 전부.
  2. `other(f, me)`로 "나 말고 상대"를 추출해 친구 User 리스트.
  3. 친구가 없으면 빈 리스트 즉시 반환(불필요 쿼리 차단).
  4. **친구들의 오늘 요약을 `IN` 쿼리 1번**(`findByUserInAndSummaryDate`)으로 한 방에 → 친구마다 따로 조회(N+1) 안 함.
  5. `Collectors.toMap(userId → summary)`로 맵 만들어 O(1) 매칭.
  6. 각 친구를 요약과 합쳐 DTO로.
- `Function.identity()`: "값 그대로"(s -> s)를 뜻하는 람다. 맵의 값으로 summary 객체 자체를 넣음.
- 결과: 친구가 N명이어도 **쿼리는 2번**(친구관계 + 요약 IN). [09 summary](09-summary.md)의 `findByUserInAndSummaryDate`가 이걸 받쳐준다.

### (2) 친구 요청 보내기 — 방어 + 자동 성사
```java
    @Transactional
    public void sendRequest(Long meId, String handle) {
        User me = getUser(meId);
        User target = userRepository.findByHandle(handle)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (target.getId().equals(me.getId())) throw new BusinessException(ErrorCode.CANNOT_FRIEND_SELF);
        if (friendshipRepository.existsBetween(me, target)) throw new BusinessException(ErrorCode.ALREADY_FRIEND);

        var reverse = friendRequestRepository
                .findByRequesterAndAddresseeAndStatus(target, me, FriendRequestStatus.PENDING);
        if (reverse.isPresent()) {                 // 상대가 이미 나한테 보냈으면
            reverse.get().accept();                //   → 그 요청 수락 처리
            createFriendship(me, target);          //   → 바로 친구 성사
            return;
        }

        boolean alreadySent = friendRequestRepository
                .findByRequesterAndAddresseeAndStatus(me, target, FriendRequestStatus.PENDING).isPresent();
        if (alreadySent) throw new BusinessException(ErrorCode.FRIEND_REQUEST_ALREADY_SENT);

        friendRequestRepository.save(new FriendRequest(me, target));
        eventPublisher.publishEvent(new FriendRequestedEvent(target.getId(), me.getNickname()));
    }
```
- handle로 상대 검색(없으면 404).
- **방어 규칙 순서대로**: 자기 자신 금지(400) → 이미 친구(409) → **역방향 PENDING 있으면 자동 친구 성사**(서로 요청하는 흔한 케이스를 매끄럽게) → 중복 요청(409) → 정상 저장.
- 저장 후 `eventPublisher.publishEvent(...)`로 **푸시 이벤트 발행**(직접 푸시 호출 X). 트랜잭션 커밋 후 비동기 처리됨([11 push](11-push-apns.md)).

### (3) 받은 요청 목록 / 수락 / 거절
```java
    @Transactional(readOnly = true)
    public List<FriendRequestResponse> listIncomingRequests(Long meId) {
        User me = getUser(meId);
        return friendRequestRepository.findIncoming(me, FriendRequestStatus.PENDING).stream()
                .map(this::toRequestResponse).toList();
    }
    @Transactional
    public void acceptRequest(Long meId, Long requestId) {
        FriendRequest request = loadPendingRequestForMe(meId, requestId);
        request.accept();                                   // 상태 변경(자동 UPDATE)
        createFriendship(request.getRequester(), request.getAddressee());
    }
    @Transactional
    public void declineRequest(Long meId, Long requestId) {
        FriendRequest request = loadPendingRequestForMe(meId, requestId);
        request.decline();
    }
```
- 수락: 요청 상태를 ACCEPTED로 + friendships 행 생성.
- 거절: 상태만 DECLINED.
- 둘 다 `loadPendingRequestForMe`로 **권한·상태 검증**을 먼저 통과해야 함.

### (4) 친구 끊기 — 멱등
```java
    @Transactional
    public void removeFriend(Long meId, Long friendUserId) {
        User me = getUser(meId);
        User friend = getUser(friendUserId);
        friendshipRepository.findBetween(me, friend).ifPresent(friendshipRepository::delete);
    }
```
- **멱등(idempotent)**: 이미 친구가 아니어도 에러 없이 "성공"으로 끝남(`ifPresent`로 있으면만 삭제). DELETE는 여러 번 호출해도 결과 동일 → 클라이언트 재시도 안전.

### (5) 헬퍼들
```java
    private FriendRequest loadPendingRequestForMe(Long meId, Long requestId) {
        FriendRequest request = friendRequestRepository.findById(requestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FRIEND_REQUEST_NOT_FOUND));
        if (!request.getAddressee().getId().equals(meId) || request.getStatus() != FriendRequestStatus.PENDING)
            throw new BusinessException(ErrorCode.FRIEND_REQUEST_FORBIDDEN);
        return request;
    }
    private void createFriendship(User a, User b) {
        if (!friendshipRepository.existsBetween(a, b)) friendshipRepository.save(Friendship.between(a, b));
    }
    private User other(Friendship f, User me) {
        return f.getUserLow().getId().equals(me.getId()) ? f.getUserHigh() : f.getUserLow();
    }
    private FriendResponse toFriendResponse(User user, DailySummary summary) {
        if (summary == null) return new FriendResponse(..., 0, 0, 0, List.of(), List.of()); // 요약 없으면 0/빈
        return new FriendResponse(..., summary.getDoneCount(), ..., summary.getDoneNames(), summary.getRemainingNames());
    }
    private User getUser(Long id) { return userRepository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND)); }
```
- `loadPendingRequestForMe`: **보안 검증** — 요청 존재(404) + **내가 받은(addressee=me) PENDING 요청만** 처리 가능(403). 남의 요청을 내가 수락/거절 못 하게.
- `createFriendship`: 중복 방지 후 정렬 생성.
- `other`: 친구관계에서 "내가 아닌 쪽" 반환.
- `toFriendResponse`: 오늘 요약이 없는 친구는 0/빈 리스트로 채워 응답 일관성 유지(널 안전).

---

## `FriendController.java`

```java
@RestController
public class FriendController {
    @GetMapping("/me/friends")
    public List<FriendResponse> listFriends(@AuthenticationPrincipal Long meId) { return friendService.listFriends(meId); }

    @PostMapping("/friend-requests")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void sendRequest(@AuthenticationPrincipal Long meId, @Valid @RequestBody SendFriendRequest request) {
        friendService.sendRequest(meId, request.handle());
    }

    @GetMapping("/me/friend-requests") ... listIncoming(...)
    @PostMapping("/friend-requests/{id}/accept") @ResponseStatus(NO_CONTENT) accept(@PathVariable Long id)
    @PostMapping("/friend-requests/{id}/decline") @ResponseStatus(NO_CONTENT) decline(...)
    @DeleteMapping("/me/friends/{userId}") @ResponseStatus(NO_CONTENT) removeFriend(@PathVariable Long userId)
}
```
- **`@ResponseStatus(HttpStatus.NO_CONTENT)`**: 반환 본문이 없는 명령형 API는 **204 No Content**(성공했고 줄 데이터 없음). RESTful.
- **`@PathVariable`**: URL 경로의 `{id}`/`{userId}`를 파라미터로 추출.
- **`@AuthenticationPrincipal Long meId`**: 매 엔드포인트에서 "나"를 토큰에서 자동 획득 → 클라이언트가 자기 id를 못 위조(URL에 내 id를 안 받음).
- HTTP 메서드 시맨틱 준수: 조회=GET, 생성/행위=POST, 삭제=DELETE.

### 친구 엔드포인트 요약

| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/me/friends` | 친구 목록 + 각자의 오늘 요약 |
| POST | `/friend-requests` | `{ handle }`로 요청(역방향 있으면 자동 성사) |
| GET | `/me/friend-requests` | 받은 요청 목록 |
| POST | `/friend-requests/{id}/accept` | 수락 |
| POST | `/friend-requests/{id}/decline` | 거절 |
| DELETE | `/me/friends/{userId}` | 친구 끊기(멱등) |

---

> 다음: [08 poke 도메인 →](08-poke.md)
