# 16 — routine 도메인 (루틴 계정 동기화)

> [← 15 web 공개 엔드포인트](15-web-public-endpoints.md) · 다음: [17 feedback 도메인 →](17-feedback.md)

대상 파일: `routine/domain/`(`Routine`, `RoutineCompletion`), `routine/repository/`(`RoutineRepository`, `RoutineCompletionRepository`), `routine/dto/RoutineDtos.java`, `routine/service/RoutineService.java`, `routine/controller/RoutineController.java`

루틴은 원래 **기기 로컬**(App Group)에만 있었다. 새 기기 로그인 시 비어 있고, 앱 삭제 시 소실되며, 계정 분리가 안 됐다. 그래서 **서버를 루틴 원본의 소유자**로 두고 계정 귀속 + 기기 간 동기화한다. 스키마는 [12 V5](12-database-schema.md), 요약(친구 공유용 읽기 모델)은 [09 summary](09-summary.md)와 역할이 다르다.

---

## `Routine.java` / `RoutineCompletion.java`

```java
@Entity @Table(name = "routines")
public class Routine {
    @Id private UUID id;                                  // 클라이언트가 생성한 UUID
    @ManyToOne(LAZY) @JoinColumn("user_id") User user;
    private String name; private String type;            // check / count
    private int target; private String unit;
    private String reminder;                             // "HH:MM" or null
    private boolean anytime;
    private String repeatMode;                           // daily / weekdays / custom
    @JdbcTypeCode(SqlTypes.JSON) List<Integer> repeatDays;  // 0=일 … 6=토 (JSONB)
    @CreationTimestamp Instant createdAt; @UpdateTimestamp Instant updatedAt;
    private Instant deletedAt;                            // soft delete (null=활성)

    public void update(...) { ... }       // 상태 변경 → dirty checking
    public void markDeleted() { this.deletedAt = Instant.now(); }
}
```
- **PK가 `@Id UUID`(자동생성 아님)**: 클라이언트가 만든 UUID를 그대로 PK로. `@GeneratedValue`가 없어 INSERT 시 그 값을 쓴다 → 오프라인에서 만든 루틴도 서버 PK를 기다리지 않고 동기화.
- **`repeatDays`는 JSONB**(`@JdbcTypeCode(SqlTypes.JSON)`, [09](09-summary.md)와 같은 기법) — 요일 배열을 한 컬럼에.
- **soft delete**(`deletedAt`): 실제 삭제 대신 시각 기록 → 다기기에서 "삭제됨"을 인지.
- `RoutineCompletion`: `(user, routine, completionDate, count)` + `updateCount`. `UNIQUE(routine_id, completion_date)`로 날짜당 한 행.

---

## `RoutineRepository` / `RoutineCompletionRepository`

```java
    List<Routine> findByUserAndDeletedAtIsNullOrderByCreatedAtAsc(User user);   // 활성 루틴(생성순)
    Optional<Routine> findByIdAndUserAndDeletedAtIsNull(UUID id, User user);     // 내 루틴 1건(소유권 겸 검증)

    List<RoutineCompletion> findByUser(User user);
    List<RoutineCompletion> findByUserAndCompletionDateGreaterThanEqual(User user, LocalDate since);
    Optional<RoutineCompletion> findByRoutineAndCompletionDate(Routine routine, LocalDate date);  // upsert 키
```
- **`findByIdAndUserAndDeletedAtIsNull`**: id로만 찾지 않고 **`AND user = me`** 까지 건다 → 쿼리 자체가 소유권 검증. 남의 루틴은 결과가 비어 `ROUTINE_NOT_FOUND`.
- `...CompletionDateGreaterThanEqual(since)`: 증분 동기화용(특정 날짜 이후 완료만).
- `idx_routine_user_active`(부분 인덱스) + `idx_completion_user`가 이 조회들을 받쳐준다([12](12-database-schema.md)).

---

## `RoutineService.java` — 소유권 + upsert

```java
    @Transactional
    public RoutineResponse create(Long meId, RoutineRequest request) {
        User me = getUser(meId);
        UUID id = request.id() != null ? request.id() : UUID.randomUUID();   // 클라 UUID 우선
        Routine routine = new Routine(id, me, request.name(), request.typeOrDefault(), ...);
        return RoutineResponse.from(routineRepository.save(routine));
    }
    @Transactional
    public RoutineResponse update(Long meId, UUID routineId, RoutineRequest request) {
        Routine routine = getMyRoutine(meId, routineId);   // 없거나 남의 것이면 404
        routine.update(...);                                // dirty checking → UPDATE
        return RoutineResponse.from(routine);
    }
    @Transactional
    public void delete(Long meId, UUID routineId) {
        getMyRoutine(meId, routineId).markDeleted();        // soft delete
    }

    @Transactional
    public void setCompletion(Long meId, UUID routineId, LocalDate date, int count) {
        Routine routine = getMyRoutine(meId, routineId);
        RoutineCompletion c = completionRepository.findByRoutineAndCompletionDate(routine, date)
                .orElseGet(() -> new RoutineCompletion(routine.getUser(), routine, date, count));  // upsert
        c.updateCount(count);
        completionRepository.save(c);
    }

    private Routine getMyRoutine(Long meId, UUID routineId) {
        return routineRepository.findByIdAndUserAndDeletedAtIsNull(routineId, getUser(meId))
                .orElseThrow(() -> new BusinessException(ErrorCode.ROUTINE_NOT_FOUND));
    }
```
- **모든 변경은 `getMyRoutine`로 소유권 검증을 먼저** 통과해야 한다. 쿼리에 `user`를 박았으므로 타인 루틴은 애초에 안 잡혀 404(`ROUTINE_NOT_FOUND`).
- **완료 카운트 upsert**: (루틴, 날짜) 행이 있으면 갱신, 없으면 생성 → 같은 날 여러 번 토글해도 한 행.
- `create`는 클라가 보낸 UUID를 우선 사용(없으면 서버 생성) → 오프라인/동기화 친화.
- `RoutineRequest`의 `typeOrDefault()`/`unitOrEmpty()`/`repeatModeOrDefault()`/`repeatDaysOrEmpty()`는 누락 필드를 안전한 기본값으로 채우는 null 방어 헬퍼([09](09-summary.md)의 `doneOrEmpty`와 같은 패턴).

---

## `RoutineController.java` — 세분화 REST CRUD

| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/me/routines` | 내 활성 루틴 목록(생성순) |
| POST | `/me/routines` | 생성(본문에 클라 UUID 포함 가능) |
| PUT | `/me/routines/{id}` | 수정 |
| DELETE | `/me/routines/{id}` | soft delete (204) |
| GET | `/me/routines/completions?since=YYYY-MM-DD` | 완료기록(선택: 날짜 이후) |
| PUT | `/me/routines/{id}/completions/{date}` | 그 날짜 완료 카운트 upsert (204) |

- **세분화 엔드포인트(루틴별/완료별)**: 전체를 통째로 덮어쓰지 않고 개별 변경만 보내 **다기기 충돌에 강함**.
- `@PathVariable UUID id` / `@DateTimeFormat(ISO.DATE) LocalDate date`: 경로의 UUID·날짜를 타입으로 바로 바인딩.
- iOS는 App Group 캐시를 위젯·오프라인 소스로 유지하면서, 로그인/포그라운드 때 pull, 변경 시 해당 엔드포인트로 push(optimistic). 계정 전환 시 캐시 비우고 새 계정 루틴 pull.

---

> 다음: [17 feedback 도메인 →](17-feedback.md)
