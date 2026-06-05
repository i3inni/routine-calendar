-- 사용자 피드백/기능 요청. 작성자가 탈퇴해도 피드백은 남기기 위해 ON DELETE SET NULL.
CREATE TABLE feedback (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id    BIGINT        REFERENCES users(id) ON DELETE SET NULL,
    content    VARCHAR(2000) NOT NULL,
    created_at TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX idx_feedback_created ON feedback (created_at DESC);
