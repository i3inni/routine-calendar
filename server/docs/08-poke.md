# 08 — poke 도메인 (콕 찌르기)

> [← 07 friend 도메인](07-friend.md) · [목차](README.md) · 다음: [09 summary 도메인 →](09-summary.md)

대상 파일: `poke/Poke.java`, `PokeRepository.java`, `PokeService.java`, `PokeController.java`, `PokeEvent.java`

친구에게 "콕 찔러" 알림을 보내는 기능. **쿨다운(스팸 방지)** 과 **도메인 이벤트** 가 핵심.

---

## `Poke.java`

```java
@Entity @Table(name = "pokes")
public class Poke {
    @ManyToOne(LAZY) fromUser;   // 찌른 사람
    @ManyToOne(LAZY) toUser;     // 찔린 사람
    @CreationTimestamp createdAt;
    public Poke(User fromUser, User toUser) { ... }
}
```
- 콕 기록. **쿨다운 검증**(마지막 콕 시각 확인)과 **알림 추적**용. User와는 LAZY 다대일([07 friend](07-friend.md)의 `@ManyToOne` 설명 참고).

---

## `PokeRepository.java`

```java
    Optional<Poke> findTopByFromUserAndToUserOrderByCreatedAtDesc(User fromUser, User toUser);
    long countByToUserAndCreatedAtAfter(User toUser, Instant after);
```
- **`findTopBy...OrderByCreatedAtDesc`**: 쿼리 메서드 키워드. `Top`=1건, `OrderByCreatedAtDesc`=최신순 → **특정 상대에게 보낸 가장 최근 콕** 1건. 쿨다운 검사용.
- `countBy...CreatedAtAfter`: 특정 시각 이후 받은 콕 개수(현재 미사용, 확장 여지).
- 이 조회는 DB의 `idx_poke_pair(from_user_id, to_user_id, created_at)` 복합 인덱스로 빠르게 처리된다([12 스키마](12-database-schema.md)).

---

## `PokeService.java`

```java
@Service
public class PokeService {
    private static final Duration COOLDOWN = Duration.ofHours(1);
```
- `Duration.ofHours(1)`: 시간 간격을 타입으로 안전하게 표현(매직넘버 대신).

```java
    @Transactional
    public void poke(Long meId, Long toUserId) {
        User me = getUser(meId);
        User to = getUser(toUserId);

        if (!friendshipRepository.existsBetween(me, to)) throw new BusinessException(ErrorCode.POKE_NOT_FRIEND);

        pokeRepository.findTopByFromUserAndToUserOrderByCreatedAtDesc(me, to)
                .ifPresent(last -> {
                    if (last.getCreatedAt().isAfter(Instant.now().minus(COOLDOWN)))
                        throw new BusinessException(ErrorCode.POKE_COOLDOWN);
                });

        pokeRepository.save(new Poke(me, to));
        eventPublisher.publishEvent(new PokeEvent(to.getId(), me.getNickname()));
    }
    private User getUser(Long id) { return userRepository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND)); }
}
```
규칙과 흐름:
1. **친구만** 콕 가능 — `existsBetween`([07 friend](07-friend.md))으로 확인, 아니면 403 `POKE_NOT_FRIEND`.
2. **쿨다운**: 마지막 콕(`findTopBy...`)이 1시간 이내면 429 `TOO_MANY_REQUESTS`. `last.createdAt > now-1h` 이면 차단.
   - `ifPresent`: 이전 콕이 있을 때만 검사(처음이면 통과).
3. 통과하면 `Poke` 저장.
4. `PokeEvent` 발행 → 트랜잭션 커밋 후 비동기 푸시([11 push](11-push-apns.md)).
- `@Transactional`이라 저장이 롤백되면 이벤트도 발행 효과가 없다(AFTER_COMMIT이므로).

---

## `PokeController.java`

```java
    @PostMapping("/pokes")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void poke(@AuthenticationPrincipal Long meId, @Valid @RequestBody PokeRequest request) {
        pokeService.poke(meId, request.toUserId());
    }
```
- `{toUserId}`(`PokeRequest`, [07 friend FriendDtos](07-friend.md)에 정의)를 받아 콕. 성공 시 204.
- `@AuthenticationPrincipal`로 "찌르는 사람"은 토큰에서 자동 획득.

---

## `PokeEvent.java`

```java
public record PokeEvent(Long toUserId, String fromNickname) {}
```
- "콕이 발생했다" 도메인 이벤트. `toUserId`(받는 사람)와 `fromNickname`(알림 문구용)만 담는다.

---

> 다음: [09 summary 도메인 →](09-summary.md)
