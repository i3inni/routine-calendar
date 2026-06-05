-- 계정 삭제 유예: 삭제 요청 시각을 기록하고, 3일 후 스케줄러가 영구 삭제한다.
-- 유예 기간 내 재로그인하면 이 값을 비워 삭제를 취소한다. (NULL = 정상 계정)
ALTER TABLE users ADD COLUMN deletion_requested_at TIMESTAMPTZ;
CREATE INDEX idx_users_deletion_requested ON users (deletion_requested_at);
