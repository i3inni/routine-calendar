# 12 — DB 스키마 (Flyway)

> [← 11 push 도메인](11-push-apns.md) · [목차](README.md) · 다음: [13 테스트 →](13-testing.md)

대상 파일: `src/main/resources/db/migration/V1__init.sql`

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
- `(from, to, created_at)` 복합 인덱스 → 쿨다운 검사(`findTopByFromUserAndToUserOrderByCreatedAtDesc`, [08 poke](08-poke.md))를 인덱스만으로 빠르게.

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

## 면접 포인트 정리

- **왜 DB 제약도 거나요?** → 앱 버그·동시성·직접 쿼리로 인한 무결성 훼손을 막는 최종 안전장치. 앱은 UX(친절한 에러), DB는 데이터 정합성.
- **친구 관계 정규화** → `low<high` 한 행 + UNIQUE/CHECK로 양방향 중복 제거.
- **부분 유니크 인덱스** → "활성 PENDING 1건만" 같은 조건부 유니크를 우아하게 표현.
- **복합 인덱스 순서** → 조회 패턴(`WHERE addressee AND status`, 쿨다운 `from,to,created_at`)에 맞춰 컬럼 순서 설계.

---

> 다음: [13 테스트 →](13-testing.md)
