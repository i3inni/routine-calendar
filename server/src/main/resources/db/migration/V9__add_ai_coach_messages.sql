-- AI 루틴 코치 대화 기록 (계정 귀속). user↔assistant 텍스트만 저장(도구 호출은 미저장).
-- 여러 기기에서 대화를 이어보기 위해 서버가 원본을 보관한다.
CREATE TABLE ai_coach_messages (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role       VARCHAR(16) NOT NULL,          -- 'user' | 'assistant'
    content    TEXT        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
-- 사용자 대화를 시간순으로 로드하는 조회 최적화
CREATE INDEX idx_ai_coach_user_time ON ai_coach_messages (user_id, created_at);
