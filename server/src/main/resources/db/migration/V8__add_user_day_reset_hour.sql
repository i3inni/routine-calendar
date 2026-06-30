-- 하루 리셋 시각(새벽 0~6시): 사용자별로 '오늘'의 경계를 자정 대신 이 시각으로 둔다.
-- 친구가 보는 그 사용자의 '오늘'과 리마인더 완료판정도 이 값을 따른다. 기본 0 = 자정(기존과 동일).
ALTER TABLE users ADD COLUMN day_reset_hour INT NOT NULL DEFAULT 0;
