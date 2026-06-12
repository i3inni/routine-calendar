-- 서버 푸시 리마인더: 오늘 이미 리마인드(평가)한 날짜를 기록해 중복 발송 방지.
ALTER TABLE routines ADD COLUMN last_reminded_on DATE;

-- 매분 "지금이 reminder 시각인 활성 루틴"을 빠르게 찾기 위한 부분 인덱스.
-- (anytime=false = 알림 켠 루틴, 삭제 안 된 것만 대상)
CREATE INDEX idx_routine_reminder ON routines (reminder)
    WHERE deleted_at IS NULL AND anytime = FALSE;
