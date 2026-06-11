# 12 — DB 스키마 (Flyway)

> [← 11 push 도메인](11-push-apns.md) · 다음: [13 테스트 →](13-testing.md)

대상 파일: `db/migration/V1__init.sql`, `V2__add_apple_login.sql`, `V3__add_account_deletion.sql`, `V4__add_feedback.sql`, `V5__add_routines.sql`

---

## Flyway 마이그레이션이란

- **네이밍 규칙**: `V{버전}__{설명}.sql`. 버전 순서대로 한 번씩 실행되고, 실행 이력을 `flyway_schema_history` 테이블에 기록 → **누가 어떤 DB에 적용해도 같은 스키마** 보장(재현성).
- 이미 적용된 파일은 다시 안 돌리고, **수정 금지**(바꾸려면 새 `V2__...sql` 추가).
- [01 application.yml](01-build-and-config.md)에서 `ddl-auto: validate`로 둔 이유: **스키마 소유권은 Flyway**, Hibernate는 엔티티↔테이블 일치만 검증.

---

## 핵심 테이블과 제약

> 큰 원칙: **같은 무결성 규칙을 엔티티(앱)와 DB 제약 양쪽에 둔다.** 앱은 친절한 에러 메시지(409 등)를, DB는 최후의 방어선(앱 버그·동시성·직접 쿼리로 인한 훼손 방지)을 담당.

### users
```sql
CREATE TABLE users (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    kakao_id BIGINT NOT NULL UNIQUE,    -- 한 카카오 계정 = 한 유저
    handle VARCHAR(30) NOT NULL UNIQUE, -- 친구코드 충돌 금지
    nickname VARCHAR(50) NOT NULL,
    profile_image_url VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
```
- **`GENERATED ALWAYS AS IDENTITY`**: 표준 SQL 자동증가 PK(시퀀스보다 현대적). 엔티티의 `@GeneratedValue(IDENTITY)`([05 user](05-user.md))와 짝.
- `kakao_id UNIQUE`: 카카오 계정당 1유저. `handle UNIQUE`: 친구코드 충돌 방지.
- `TIMESTAMPTZ`: 타임존 포함 시각. 엔티티의 `Instant`(UTC)와 대응.

### friendships
```sql
CREATE TABLE friendships (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_low_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    user_high_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_friendship UNIQUE (user_low_id, user_high_id),
    CONSTRAINT chk_friend_order CHECK (user_low_id < user_high_id)
);
CREATE INDEX idx_friendship_high ON friendships(user_high_id);
```
- **`REFERENCES users(id) ON DELETE CASCADE`**: 외래키 + **유저 삭제 시 관련 친구관계 자동 삭제**(고아 데이터 방지).
- **`UNIQUE(low, high)` + `CHECK(low < high)`**: **정규화 강제**. 앱의 `Friendship.between()`([07 friend](07-friend.md))과 이중 안전장치 → A-B/B-A 중복 원천 차단.
- `idx_friendship_high`: low쪽은 유니크 인덱스로 커버되지만 high쪽 조회 가속용 별도 인덱스.

### friend_requests
```sql
CREATE TABLE friend_requests (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    requester_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    addressee_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',  -- PENDING / ACCEPTED / DECLINED
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    responded_at TIMESTAMPTZ,
    CONSTRAINT chk_request_self CHECK (requester_id <> addressee_id)
);
CREATE UNIQUE INDEX uq_pending_request ON friend_requests (requester_id, addressee_id) WHERE status = 'PENDING';
CREATE INDEX idx_request_addressee ON friend_requests (addressee_id, status);
```
- `status VARCHAR`: 엔티티의 `@Enumerated(EnumType.STRING)`([07 friend](07-friend.md))와 짝.
- **`CHECK(requester <> addressee)`**: 자기 자신에게 요청 금지(DB 레벨).
- **부분 유니크 인덱스(partial index)** `WHERE status='PENDING'`: 같은 방향으로 **살아있는(PENDING) 요청은 1건만** 허용. 거절/수락된 과거 요청은 제한 없음 → **재요청 가능하면서 중복 PENDING 차단**. (Postgres partial index의 좋은 활용 예 — 면접 어필 포인트)
- `idx_request_addressee(addressee, status)`: "내가 받은 PENDING 요청" 조회 가속(복합 인덱스, `findIncoming`을 받쳐줌).

### pokes
```sql
CREATE TABLE pokes (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    from_user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    to_user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_poke_pair ON pokes (from_user_id, to_user_id, created_at);
```
- `pokes`는 "콕"에서 **자극하기(nudge)** 로 바뀐 뒤에도 같은 테이블을 재사용([08 자극하기](08-poke.md)).
- `(from, to, created_at)` 복합 인덱스 → 쿨다운 검사(`countByFromUserAndToUserAndCreatedAtAfter`)와 친구별 집계(`findNudgeStats`)를 인덱스만으로 빠르게.

### daily_summaries
```sql
CREATE TABLE daily_summaries (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    summary_date DATE NOT NULL,
    done_count INT NOT NULL DEFAULT 0,
    total_count INT NOT NULL DEFAULT 0,
    streak INT NOT NULL DEFAULT 0,
    done_names JSONB NOT NULL DEFAULT '[]',
    remaining_names JSONB NOT NULL DEFAULT '[]',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_summary UNIQUE (user_id, summary_date)
);
```
- **`JSONB`**: 루틴 이름 배열을 한 컬럼에(엔티티의 `@JdbcTypeCode(JSON)`, [09 summary](09-summary.md)와 짝).
- **`UNIQUE(user_id, summary_date)`**: **하루 한 행** 강제 → upsert가 성립하는 근거.

### device_tokens
```sql
CREATE TABLE device_tokens (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token VARCHAR(255) NOT NULL UNIQUE,
    platform VARCHAR(10) NOT NULL DEFAULT 'IOS',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_device_user ON device_tokens (user_id);
```
- `token UNIQUE`: upsert 근거([10 device](10-device.md)). `idx_device_user`: 유저의 기기 목록 조회(푸시 발송) 가속.

---

## 이후 마이그레이션 (스키마 진화)

기존 `V1`은 **수정 금지**. 변경은 새 버전 파일로 누적한다.

### `V2__add_apple_login.sql` — 애플 로그인 지원
```sql
ALTER TABLE users ALTER COLUMN kakao_id DROP NOT NULL;      -- 애플 전용 유저는 kakao_id 없음
ALTER TABLE users ADD COLUMN apple_id VARCHAR(255) UNIQUE;  -- 애플 sub
```
- 신원 제공자가 카카오 **또는** 애플 → `kakao_id`를 선택(nullable)으로 완화하고 `apple_id` 추가.
- **UNIQUE + NULL 허용**: Postgres는 NULL을 유니크 위반으로 안 봄 → 카카오 전용 유저(apple_id=NULL)가 여럿이어도 OK.

### `V3__add_account_deletion.sql` — 계정 삭제 유예
```sql
ALTER TABLE users ADD COLUMN deletion_requested_at TIMESTAMPTZ;       -- NULL = 정상 계정
CREATE INDEX idx_users_deletion_requested ON users (deletion_requested_at);
```
- 삭제 예약 시각 기록(soft delete). 인덱스는 스케줄러의 "유예 지난 계정" 조회(`findByDeletionRequestedAtBefore`)용. ([05 계정삭제](05-user.md))

### `V4__add_feedback.sql` — 피드백/기능 요청
```sql
CREATE TABLE feedback (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,   -- 작성자 탈퇴해도 피드백 보존
    content VARCHAR(2000) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_feedback_created ON feedback (created_at DESC);
```
- **`ON DELETE SET NULL`**(다른 테이블의 CASCADE와 대비): 유저가 탈퇴해도 피드백 내용은 남기고 작성자만 NULL로. 운영자가 피드백을 계속 보기 위함.
- `created_at DESC` 인덱스: 관리자 피드백 조회(`/admin/feedback`)의 최신순 정렬용.

### `V5__add_routines.sql` — 루틴 계정 귀속(서버 동기화)
```sql
CREATE TABLE routines (
    id UUID PRIMARY KEY,                                       -- 클라이언트 생성 UUID
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    type VARCHAR(10) NOT NULL DEFAULT 'check',                 -- check / count
    target INT NOT NULL DEFAULT 1, unit VARCHAR(20) NOT NULL DEFAULT '',
    reminder VARCHAR(5),                                       -- "HH:MM" or null
    anytime BOOLEAN NOT NULL DEFAULT TRUE,
    repeat_mode VARCHAR(10) NOT NULL DEFAULT 'daily',          -- daily / weekdays / custom
    repeat_days JSONB NOT NULL DEFAULT '[]',                   -- 0=일 … 6=토
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ                                     -- soft delete (NULL=활성)
);
CREATE INDEX idx_routine_user_active ON routines (user_id) WHERE deleted_at IS NULL;

CREATE TABLE routine_completions (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    routine_id UUID NOT NULL REFERENCES routines(id) ON DELETE CASCADE,
    completion_date DATE NOT NULL, count INT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_routine_completion UNIQUE (routine_id, completion_date)
);
CREATE INDEX idx_completion_user ON routine_completions (user_id);
```
- **PK가 클라이언트 생성 `UUID`**: 오프라인에서 만든 루틴도 충돌 없이 서버와 동기화(서버 자동증가 PK를 기다릴 필요 없음).
- **`deleted_at` soft delete + 부분 인덱스(`WHERE deleted_at IS NULL`)**: 다기기에서 삭제를 인지하도록 행을 남기되, 활성 루틴 조회는 인덱스로 빠르게.
- `routine_completions`: **`UNIQUE(routine_id, completion_date)`** → 루틴·날짜당 한 행(완료 카운트 upsert 근거). 루틴 삭제 시 CASCADE.
- 루틴은 원래 기기 로컬(App Group)에만 있었으나 **계정 귀속 + 기기 간 동기화**를 위해 서버를 원본 소유자로. (신규 [routine 도메인](16-routine.md) 참고)

> 스키마 변경을 코드(엔티티)와 함께 **버전으로 남기니**, 어느 시점의 DB든 재현 가능. JPA는 `validate`라 각 마이그레이션 적용 후 엔티티(`appleId`, `deletionRequestedAt`, `Feedback`, `Routine`, `RoutineCompletion`)와 일치해야 부팅됨.

---

## 면접 포인트 정리

- **왜 DB 제약도 거나요?** → 앱 버그·동시성·직접 쿼리로 인한 무결성 훼손을 막는 최종 안전장치. 앱은 UX(친절한 에러), DB는 데이터 정합성.
- **친구 관계 정규화** → `low<high` 한 행 + UNIQUE/CHECK로 양방향 중복 제거.
- **부분 유니크 인덱스** → "활성 PENDING 1건만" 같은 조건부 유니크를 우아하게 표현.
- **복합 인덱스 순서** → 조회 패턴(`WHERE addressee AND status`, 쿨다운 `from,to,created_at`)에 맞춰 컬럼 순서 설계.
- **soft delete + 부분 인덱스** → `routines.deleted_at`으로 삭제를 다기기에 전파하면서, `WHERE deleted_at IS NULL` 부분 인덱스로 활성 조회 성능 유지.
- **클라이언트 생성 UUID PK** → 오프라인 생성/동기화에 유리(서버 PK 의존 제거).
- **CASCADE vs SET NULL** → 관계 데이터는 CASCADE(고아 제거), 보존할 로그성 데이터(feedback)는 SET NULL.

---

> 다음: [13 테스트 →](13-testing.md)
