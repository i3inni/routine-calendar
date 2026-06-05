-- Apple 로그인 지원: 신원 제공자가 카카오 또는 애플이 될 수 있다.
-- kakao_id는 더 이상 필수가 아니고(애플 전용 유저), apple_id를 추가한다.
ALTER TABLE users ALTER COLUMN kakao_id DROP NOT NULL;
ALTER TABLE users ADD COLUMN apple_id VARCHAR(255) UNIQUE;  -- 애플의 stable user id(sub)
