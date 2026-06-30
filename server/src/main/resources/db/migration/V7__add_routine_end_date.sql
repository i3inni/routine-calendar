-- 루틴 종료일: 이 날짜(당일 포함)부터 루틴이 더 이상 노출되지 않는다(이전 기록은 보존).
-- 시작일은 기존 created_at(클라가 보낸 선택 날짜)을 그대로 사용한다.
ALTER TABLE routines ADD COLUMN end_date DATE;
