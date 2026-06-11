# 08 — 자극하기 (nudge / poke 테이블 재사용)

> [← 07 friend 도메인](07-friend.md) · 다음: [09 summary 도메인 →](09-summary.md)

대상 파일: `friend/domain/Poke.java`, `friend/repository/PokeRepository.java`, `friend/domain/FriendNudgedEvent.java`, 그리고 `friend/service/FriendService.nudge(...)`

친구를 "자극"해 알림(직접 입력한 멘트)을 보내는 기능. 과거 "콕 찌르기"가 **자극하기**로 바뀌었고, 별도 PokeService/Controller 없이 **friend 도메인 안에 통합**됐다. 다만 기록 테이블 이름은 그대로 `pokes`를 **재사용**한다. 핵심은 **쿨다운(스팸 방지)** 과 **도메인 이벤트**.

---

## `Poke.java` — 자극 기록 (pokes 테이블)

```java
@Entity @Table(name = "pokes")
public class Poke {
    @Id @GeneratedValue(IDENTITY) Long id;
    @ManyToOne(LAZY) @JoinColumn("from_user_id") User fromUser;  // 자극한 사람
    @ManyToOne(LAZY) @JoinColumn("to_user_id")   User toUser;    // 자극받은 사람
    @CreationTimestamp Instant createdAt;
    public Poke(User fromUser, User toUser) { ... }
}
```
- 자극 1건 = 한 행. **쿨다운 검증**(최근 N분 횟수)과 **남은횟수/리셋시각 계산**의 근거 데이터.
- User와 LAZY 다대일([07 friend](07-friend.md)의 `@ManyToOne` 설명 참고). 테이블명은 레거시인 `pokes`를 유지.

---

## `PokeRepository.java`

```java
    // from→to 자극 중 since 이후 발생한 횟수 (쿨다운 검증용)
    long countByFromUserAndToUserAndCreatedAtAfter(User fromUser, User toUser, Instant since);

    // 내가 한 자극을 친구별로 집계: since 이후 횟수 + 가장 오래된 시각
    @Query("""
        select p.toUser.id as friendId, count(p) as cnt, min(p.createdAt) as oldest
        from Poke p
        where p.fromUser = :me and p.createdAt >= :since
        group by p.toUser.id
        """)
    List<NudgeStat> findNudgeStats(@Param("me") User me, @Param("since") Instant since);

    interface NudgeStat { Long getFriendId(); long getCnt(); Instant getOldest(); }
```
- **`countBy...CreatedAtAfter`**: 한 친구에게 최근 30분간 몇 번 자극했는지 → 쿨다운 판정.
- **`findNudgeStats`**(JPQL `@Query` + **projection interface**): 친구목록을 한 번 그릴 때 **모든 친구의 자극 통계를 1쿼리로** 집계(N+1 회피). `count`=쓴 횟수, `min(createdAt)`=윈도우 안 가장 오래된 자극(→ 리셋 시각 계산).
- 이 조회는 `idx_poke_pair(from_user_id, to_user_id, created_at)` 복합 인덱스로 빠르게 처리([12 스키마](12-database-schema.md)).

---

## `FriendService.nudge(...)` — 자극 발송 + 쿨다운

```java
private static final int NUDGE_LIMIT = 2;                       // 한 친구당
private static final Duration NUDGE_WINDOW = Duration.ofMinutes(30);  // 30분에 2회

@Transactional
public void nudge(Long meId, Long friendUserId, String message) {
    User me = getUser(meId);
    User friend = getUser(friendUserId);
    if (!friendshipRepository.existsBetween(me, friend)) {
        throw new BusinessException(ErrorCode.NOT_FRIEND);          // 403 친구만
    }
    long recent = pokeRepository.countByFromUserAndToUserAndCreatedAtAfter(
            me, friend, Instant.now().minus(NUDGE_WINDOW));
    if (recent >= NUDGE_LIMIT) {
        throw new BusinessException(ErrorCode.NUDGE_COOLDOWN);      // 429 쿨다운
    }
    pokeRepository.save(new Poke(me, friend));
    eventPublisher.publishEvent(
            new FriendNudgedEvent(friend.getId(), me.getNickname(), message));
}
```
규칙과 흐름:
1. **친구만** 자극 가능 — `existsBetween`([07 friend](07-friend.md)), 아니면 403 `NOT_FRIEND`.
2. **쿨다운**: 최근 30분 자극 횟수가 2회 이상이면 429 `NUDGE_COOLDOWN`. (마지막 1건만 보던 옛 방식과 달리 **윈도우 내 횟수**로 판정 → "30분에 2회".)
3. 통과하면 `Poke` 저장.
4. `FriendNudgedEvent` 발행 → 트랜잭션 커밋 후 비동기 푸시([11 push](11-push-apns.md)). 사용자가 입력한 `message`가 알림 본문이 된다.
- **쿨다운 상수는 코드 상수**(`NUDGE_LIMIT`/`NUDGE_WINDOW`)로 둔다. 설정값으로 빼지 않은 이유는 정책이 UX(2/30분)에 고정돼 있기 때문.

### 친구목록에 남은횟수/리셋시각 노출
`listFriends`가 `findNudgeStats`를 한 번 불러, 각 친구에 대해:
```java
int nudgeRemaining = max(0, NUDGE_LIMIT - used);              // 남은 자극 횟수(0~2)
Long nudgeResetAtMs = (nudgeRemaining == 0 && nudge != null)
        ? nudge.getOldest().plus(NUDGE_WINDOW).toEpochMilli() // 0회면 다시 가능해지는 시각
        : null;
```
→ iOS는 이 값으로 "2/2" 표시와 **30분 카운트다운**을 그린다([07 friend FriendResponse](07-friend.md)).

---

## 엔드포인트 / 이벤트

```java
// FriendController
@PostMapping("/me/friends/{userId}/nudge")
@ResponseStatus(HttpStatus.NO_CONTENT)
public void nudge(@AuthenticationPrincipal Long meId, @PathVariable Long userId,
                  @Valid @RequestBody NudgeRequest request) {
    friendService.nudge(meId, userId, request.message());
}

// NudgeRequest (FriendDtos): @NotBlank @Size(max=50) String message
// FriendNudgedEvent: record (Long toUserId, String fromNickname, String message)
```
- 자극 대상은 **경로변수** `{userId}`, 멘트는 본문 `message`(최대 50자). 성공 시 204.
- "자극하는 사람"은 `@AuthenticationPrincipal`로 토큰에서 자동 획득.
- `FriendNudgedEvent`는 받는 사람 id + 보낸 사람 닉네임 + 멘트를 담아 푸시 리스너로 전달.

---

> 다음: [09 summary 도메인 →](09-summary.md)
