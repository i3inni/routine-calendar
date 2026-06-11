# 07 — friend 도메인

> [← 06 auth 카카오 로그인](06-auth-kakao.md) · 다음: [08 자극하기 →](08-poke.md)

대상 파일: `friend/domain/`(`Friendship`, `FriendRequest`, `FriendRequestStatus`, `Poke`, `FriendRequestedEvent`, `FriendRequestAcceptedEvent`, `FriendNudgedEvent`), `friend/repository/`(`FriendshipRepository`, `FriendRequestRepository`, `PokeRepository`), `friend/dto/FriendDtos.java`, `friend/service/`(`FriendService`, `KakaoFriendService`, `KakaoFriendMatcher`), `friend/controller/`(`FriendController`, `KakaoFriendController`)

가장 비즈니스 로직이 풍부한 도메인. 친구 요청(받은/보낸)→수락→목록→끊기 + **자극하기**([08](08-poke.md)) + **카카오 친구찾기**(아래 별도 절). **N+1 방지**의 모범 사례가 여기 있다.

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
    public record NudgeRequest(@NotBlank @Size(max = 50) String message) {}  // 자극 멘트(08)
    public record KakaoTokenRequest(@NotBlank String kakaoAccessToken) {}     // 카카오 친구찾기 입력

    // 받은 요청 1건
    public record FriendRequestResponse(Long requestId, Long fromUserId, String fromHandle,
                                        String fromNickname, String fromProfileImageUrl, Instant createdAt) {}
    // 보낸 요청 1건 (상대=addressee 기준)
    public record SentFriendRequestResponse(Long requestId, Long toUserId, String toHandle,
                                            String toNickname, String toProfileImageUrl, Instant createdAt) {}
    // 친구 1명 + 오늘 요약 + 자극 잔여
    public record FriendResponse(Long userId, String handle, String nickname, String profileImageUrl,
                                 int doneToday, int totalToday, int streak, List<String> done, List<String> remaining,
                                 int nudgeRemaining, Long nudgeResetAtMs) {}
    // 카카오 친구찾기 후보 (앱 가입 + 내 카톡친구 + 아직 친구 아님)
    public record KakaoFriendCandidate(Long userId, String handle, String nickname,
                                       String profileImageUrl, String kakaoNickname) {}
```
- 요청은 handle/멘트/카카오토큰만 받고, 응답은 iOS 모델에 맞춰 **친구 정보 + 오늘 요약 + 자극 잔여횟수**를 한 번에 내려줌.
- `nudgeRemaining`(0~2)/`nudgeResetAtMs`는 [08 자극하기](08-poke.md)에서 계산. `kakaoNickname`은 카톡 친구목록 표시 이름.
- `@NotBlank`는 문자열 공백까지 금지, `@Size(max=50)`은 길이 제한 — 타입/제약에 맞게 구분 사용.

---

## 도메인 이벤트 (push 트리거)

```java
public record FriendRequestedEvent(Long toUserId, String fromNickname) {}           // 요청 받음
public record FriendRequestAcceptedEvent(Long toUserId, String accepterNickname) {} // 내 요청이 수락됨
public record FriendNudgedEvent(Long toUserId, String fromNickname, String message) {} // 자극받음(08)
```
- **도메인 이벤트**: "친구 요청/수락/자극이 일어났다"는 사실을 값으로 표현. 푸시 리스너가 커밋 후 비동기 처리([11 push](11-push-apns.md)). 서비스가 푸시 코드를 직접 몰라도 되게 **느슨한 결합**.
- 요청/수락 푸시는 `type=friend`로 보내져, 앱이 받으면 **친구목록을 자동 갱신**한다(폴링 없이 변화 시 1회). 자극 푸시는 `type=nudge`.

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

        // 자극 잔여횟수 계산용: 내가 한 자극을 친구별로 1쿼리 집계(08)
        Map<Long, NudgeStat> nudgeStats = pokeRepository
                .findNudgeStats(me, Instant.now().minus(NUDGE_WINDOW)).stream()
                .collect(Collectors.toMap(NudgeStat::getFriendId, Function.identity()));

        return friends.stream()
                .map(u -> toFriendResponse(u, summaries.get(u.getId()), nudgeStats.get(u.getId())))
                .toList();
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
- 결과: 친구가 N명이어도 **쿼리는 3번**(친구관계 fetch join + 요약 IN + 자극통계 group by). 친구마다 따로 조회(N+1)하지 않는다. [09 summary](09-summary.md)의 `findByUserInAndSummaryDate`, [08 자극하기](08-poke.md)의 `findNudgeStats`가 이걸 받쳐준다.

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

### (3) 요청 목록(받은/보낸) / 수락 / 거절
```java
    @Transactional(readOnly = true)
    public List<FriendRequestResponse> listIncomingRequests(Long meId) {        // 받은 요청
        User me = getUser(meId);
        return friendRequestRepository.findIncoming(me, FriendRequestStatus.PENDING).stream()
                .map(this::toRequestResponse).toList();
    }
    @Transactional(readOnly = true)
    public List<SentFriendRequestResponse> listOutgoingRequests(Long meId) {    // 보낸 요청
        User me = getUser(meId);
        return friendRequestRepository.findOutgoing(me, FriendRequestStatus.PENDING).stream()
                .map(this::toSentResponse).toList();
    }
    @Transactional
    public void acceptRequest(Long meId, Long requestId) {
        FriendRequest request = loadPendingRequestForMe(meId, requestId);
        request.accept();                                   // 상태 변경(자동 UPDATE)
        createFriendship(request.getRequester(), request.getAddressee());
        // 요청 보냈던 사람에게 '수락됨' 푸시 → 그 사람 친구목록 자동 갱신
        eventPublisher.publishEvent(new FriendRequestAcceptedEvent(
                request.getRequester().getId(), request.getAddressee().getNickname()));
    }
    @Transactional
    public void declineRequest(Long meId, Long requestId) {
        FriendRequest request = loadPendingRequestForMe(meId, requestId);
        request.decline();
    }
```
- 받은 요청은 `findIncoming`(requester fetch join), **보낸 요청은 `findOutgoing`**(addressee fetch join)으로 각각 조회.
- 수락: 요청 상태를 ACCEPTED로 + friendships 행 생성 + **`FriendRequestAcceptedEvent` 발행**(요청자에게 푸시).
- 거절: 상태만 DECLINED.
- 셋 다 `loadPendingRequestForMe`로 **권한·상태 검증**을 먼저 통과해야 함.

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
    private FriendResponse toFriendResponse(User user, DailySummary summary, NudgeStat nudge) {
        long used = (nudge != null) ? nudge.getCnt() : 0;
        int nudgeRemaining = (int) Math.max(0, NUDGE_LIMIT - used);                 // 남은 자극(0~2)
        Long nudgeResetAtMs = (nudgeRemaining == 0 && nudge != null)               // 0회면 리셋 시각
                ? nudge.getOldest().plus(NUDGE_WINDOW).toEpochMilli() : null;
        if (summary == null) return new FriendResponse(..., 0, 0, 0, List.of(), List.of(),
                                                       nudgeRemaining, nudgeResetAtMs);  // 요약 없으면 0/빈
        return new FriendResponse(..., summary.getDoneCount(), ..., summary.getDoneNames(),
                                  summary.getRemainingNames(), nudgeRemaining, nudgeResetAtMs);
    }
    private User getUser(Long id) { return userRepository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND)); }
```
- `loadPendingRequestForMe`: **보안 검증** — 요청 존재(404) + **내가 받은(addressee=me) PENDING 요청만** 처리 가능(403). 남의 요청을 내가 수락/거절 못 하게.
- `createFriendship`: 중복 방지 후 정렬 생성.
- `other`: 친구관계에서 "내가 아닌 쪽" 반환.
- `toFriendResponse`: 오늘 요약이 없는 친구는 0/빈 리스트로 채워 응답 일관성 유지(널 안전). 자극 잔여횟수/리셋시각은 `NudgeStat`(08)로 계산.

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
| GET | `/me/friends` | 친구 목록 + 각자의 오늘 요약 + 자극 잔여 |
| POST | `/friend-requests` | `{ handle }`로 요청(역방향 있으면 자동 성사) |
| GET | `/me/friend-requests` | **받은** 요청 목록 |
| GET | `/me/friend-requests/sent` | **보낸** 요청 목록 |
| POST | `/friend-requests/{id}/accept` | 수락(+ 요청자에게 푸시) |
| POST | `/friend-requests/{id}/decline` | 거절 |
| DELETE | `/me/friends/{userId}` | 친구 끊기(멱등) |
| POST | `/me/friends/{userId}/nudge` | 자극하기([08](08-poke.md)), `{ message }` |
| POST | `/me/kakao/friends` | 카카오 친구찾기(아래), `{ kakaoAccessToken }` |

---

## 카카오 친구찾기 — `KakaoFriendController` / `KakaoFriendService` / `KakaoFriendMatcher`

애플로 로그인한 유저가 **카카오 친구 중 이 앱 가입자**를 찾아 친구 요청하는 기능. 외부 API 호출과 DB 작업을 **두 빈으로 분리**한 게 설계 포인트.

```java
// KakaoFriendService — 오케스트레이션 (트랜잭션 밖에서 외부 호출)
public List<KakaoFriendCandidate> findAppFriends(Long meId, String kakaoAccessToken) {
    KakaoUserResponse kakao = kakaoApiClient.fetchUser(kakaoAccessToken);            // 내 카카오 id
    Map<Long, String> kakaoFriends = kakaoApiClient.fetchFriends(kakaoAccessToken);  // 친구 kakaoId→이름
    return matcher.matchAndLink(meId, kakao.id(), kakaoFriends);                     // DB는 별도 트랜잭션 빈
}
```
- **외부 네트워크 호출(`fetchUser`/`fetchFriends`)은 트랜잭션 밖**에서 한다 → 느린 카카오 API가 DB 커넥션을 점유하지 않게. 그 다음 DB 작업만 `@Transactional`인 `KakaoFriendMatcher`로 위임(**별도 빈이라 프록시 경유 → @Transactional 정상 적용**).
- `KakaoFriendMatcher.matchAndLink`:
  1. **내 카카오 연동**(`linkKakaoIfPossible`): 나도 친구 검색에 잡히도록 내 `kakaoId` 저장. 내 계정에 다른 카카오가 있거나(`ACCOUNT_HAS_OTHER_KAKAO`) 그 카카오를 남이 쓰면(`KAKAO_ALREADY_LINKED`) 충돌.
  2. **후보 필터**(메모리, N+1 회피): `findByKakaoIdIn`으로 가입자 조회 후 — 나 제외 / 삭제예약 제외 / **이미 친구 제외** / **요청 진행중(PENDING) 제외**.
  3. 후보에 **카톡 표시이름(`kakaoNickname`)** 을 실어 반환.
- 입력 토큰에 friends 동의가 없으면 `fetchFriends`가 403 → `KAKAO_FRIENDS_CONSENT_REQUIRED`. (검수/팀원 이슈는 [06](06-auth-kakao.md) 참고)

---

> 다음: [08 자극하기 →](08-poke.md)
