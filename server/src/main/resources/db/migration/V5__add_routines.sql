-- 루틴 원본 (계정 귀속). 기존엔 기기 로컬(App Group)에만 있었으나,
-- 계정별 분리 + 기기 간 동기화를 위해 서버를 원본 소유자로 둔다.
CREATE TABLE routines (
    id          UUID         PRIMARY KEY,                       -- 클라이언트 생성 UUID
    user_id     BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name        VARCHAR(100) NOT NULL,
    type        VARCHAR(10)  NOT NULL DEFAULT 'check',          -- check / count
    target      INT          NOT NULL DEFAULT 1,
    unit        VARCHAR(20)  NOT NULL DEFAULT '',
    reminder    VARCHAR(5),                                     -- "HH:MM" or null
    anytime     BOOLEAN      NOT NULL DEFAULT TRUE,
    repeat_mode VARCHAR(10)  NOT NULL DEFAULT 'daily',          -- daily / weekdays / custom
    repeat_days JSONB        NOT NULL DEFAULT '[]',             -- 0=일 … 6=토
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at  TIMESTAMPTZ                                     -- soft delete (NULL = 활성)
);
-- 활성 루틴 조회 최적화
CREATE INDEX idx_routine_user_active ON routines (user_id) WHERE deleted_at IS NULL;

-- 날짜별 완료 카운트 (routine_id + date 당 한 행)
CREATE TABLE routine_completions (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id         BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    routine_id      UUID        NOT NULL REFERENCES routines(id) ON DELETE CASCADE,
    completion_date DATE        NOT NULL,
    count           INT         NOT NULL DEFAULT 0,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_routine_completion UNIQUE (routine_id, completion_date)
);
CREATE INDEX idx_completion_user ON routine_completions (user_id);
