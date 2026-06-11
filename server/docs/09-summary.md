# 09 — summary 도메인 (오늘 루틴 요약)

> [← 08 자극하기](08-poke.md) · 다음: [10 device 도메인 →](10-device.md)

대상 파일: `summary/`의 `DailySummary`, `DailySummaryRepository`, `SummaryService`, `SummaryController`, `SummaryDtos`

`daily_summaries`는 **친구에게 보여줄 오늘치 스냅샷**(완료/남은 루틴 이름 + 진행률 + 연속일)이다. 루틴 원본([16 routine](16-routine.md))이 서버에 동기화된 뒤에도, 친구 목록을 한 번에 빠르게 그리기 위한 **비정규화된 읽기 모델**로 따로 둔다(친구 N명 조회 시 각자의 루틴/완료를 조인하지 않고 이 한 행만 IN으로).

---

## `DailySummary.java` — JSONB 매핑

```java
@Entity @Table(name = "daily_summaries")
public class DailySummary {
    @ManyToOne(LAZY) @JoinColumn(name="user_id") private User user;
    @Column(name="summary_date") private LocalDate summaryDate;
    private int doneCount; private int totalCount; private int streak;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "done_names", nullable = false)
    private List<String> doneNames = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "remaining_names", nullable = false)
    private List<String> remainingNames = new ArrayList<>();

    @UpdateTimestamp ... updatedAt;
```
- **`@JdbcTypeCode(SqlTypes.JSON)`**(Hibernate 6): `List<String>`을 **Postgres JSONB 컬럼**에 직렬화/역직렬화. 별도 매핑 라이브러리 없이 컬렉션을 JSON 한 컬럼에 저장. → 루틴 이름 배열을 깔끔하게.
- `LocalDate summaryDate`: 시각 없는 날짜(요약의 "그 날").
- `doneCount/totalCount/streak`: 진행률·연속일.

```java
    public DailySummary(User user, LocalDate summaryDate) { ... }   // 새 행 생성
    public void update(int doneCount, int totalCount, int streak, List<String> doneNames, List<String> remainingNames) {
        this.doneCount = doneCount; ... this.remainingNames = remainingNames;
    }
```
- 생성자는 (user, date)만, 나머지는 `update`로 채움 → **같은 (user, date) 행을 매번 갱신(upsert)** 하는 패턴 지원.
- `update`는 영속 엔티티의 상태 변경 → dirty checking으로 UPDATE.

---

## `DailySummaryRepository.java`

```java
    Optional<DailySummary> findByUserAndSummaryDate(User user, LocalDate summaryDate);
    List<DailySummary> findByUserInAndSummaryDate(Collection<User> users, LocalDate summaryDate);
```
- 첫번째: 내 오늘 요약 1건(upsert 시 기존 행 찾기).
- 두번째: **`In`** 키워드 → `WHERE user_id IN (...)`. 친구 여러 명의 오늘 요약을 한 쿼리로 → **[07 friend](07-friend.md)의 친구 목록 N+1 방지**의 핵심.

---

## `SummaryService.java`

```java
    @Transactional
    public void upsertMySummary(Long meId, SummaryUpsertRequest request) {
        User me = userRepository.findById(meId).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        LocalDate today = AppTime.today();

        DailySummary summary = dailySummaryRepository.findByUserAndSummaryDate(me, today)
                .orElseGet(() -> new DailySummary(me, today));   // 있으면 갱신, 없으면 새로

        List<String> done = request.doneOrEmpty();
        List<String> remaining = request.remainingOrEmpty();
        summary.update(done.size(), done.size() + remaining.size(), request.streak(), done, remaining);
        dailySummaryRepository.save(summary);
    }
```
- **upsert**: (나, 오늘) 행이 있으면 갱신, 없으면 생성. `orElseGet`으로 분기.
- `doneCount=done.size()`, `totalCount=done+remaining` → **카운트를 서버가 목록 크기로 계산**(클라이언트가 보낸 카운트를 안 믿음, 정합성).
- 날짜는 `AppTime.today()`(KST, [04](04-error-handling.md)) → 클라이언트 시간대와 무관하게 서버 기준.
- `(user_id, summary_date)` UNIQUE 제약([12 스키마](12-database-schema.md))이 "하루 한 행"을 DB 레벨에서도 보장.

---

## `SummaryController.java`

```java
    @PostMapping("/me/summary")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void upsertMySummary(@AuthenticationPrincipal Long meId, @RequestBody SummaryUpsertRequest request) {
        summaryService.upsertMySummary(meId, request);
    }
```
- 앱이 진입/데이터 변경 시 오늘 요약 업로드. 204.

---

## `SummaryDtos.java`

```java
    public record SummaryUpsertRequest(List<String> done, List<String> remaining, int streak) {
        public List<String> doneOrEmpty() { return done != null ? done : List.of(); }
        public List<String> remainingOrEmpty() { return remaining != null ? remaining : List.of(); }
    }
```
- null 방어 헬퍼(`doneOrEmpty`/`remainingOrEmpty`)로 항상 빈 리스트 보장 → 서비스 코드가 깔끔.

---

> 다음: [10 device 도메인 →](10-device.md)
