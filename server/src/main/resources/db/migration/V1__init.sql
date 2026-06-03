-- 사용자: Kakao 로그인 신원 + 친구추가용 handle
CREATE TABLE users (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    kakao_id          BIGINT       NOT NULL UNIQUE,   -- 카카오가 발급하는 회원 번호
    handle            VARCHAR(30)  NOT NULL UNIQUE,   -- 친구가 검색/추가에 쓰는 공개 ID
    nickname          VARCHAR(50)  NOT NULL,
    profile_image_url VARCHAR(500),
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- 친구 관계: 중복을 막기 위해 (user_low_id < user_high_id) 한 행으로 정규화
CREATE TABLE friendships (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_low_id  BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    user_high_id BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_friendship   UNIQUE (user_low_id, user_high_id),
    CONSTRAINT chk_friend_order CHECK (user_low_id < user_high_id)
);
CREATE INDEX idx_friendship_high ON friendships(user_high_id);

-- 친구 요청 (A -> B). B가 수락하면 friendships 행이 생긴다.
CREATE TABLE friend_requests (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    requester_id BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    addressee_id BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status       VARCHAR(20) NOT NULL DEFAULT 'PENDING',  -- PENDING / ACCEPTED / DECLINED
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    responded_at TIMESTAMPTZ,
    CONSTRAINT chk_request_self CHECK (requester_id <> addressee_id)
);
-- 같은 방향으로 동시에 살아있는 PENDING 요청은 1건만 허용
CREATE UNIQUE INDEX uq_pending_request
    ON friend_requests (requester_id, addressee_id)
    WHERE status = 'PENDING';
CREATE INDEX idx_request_addressee ON friend_requests (addressee_id, status);

-- 콕 찌르기 기록 (쿨다운 검증 + 알림 발송용)
CREATE TABLE pokes (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    from_user_id BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    to_user_id   BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_poke_pair ON pokes (from_user_id, to_user_id, created_at);

-- 오늘 루틴 요약 (친구에게 공유되는 데이터)
CREATE TABLE daily_summaries (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id         BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    summary_date    DATE        NOT NULL,
    done_count      INT         NOT NULL DEFAULT 0,
    total_count     INT         NOT NULL DEFAULT 0,
    streak          INT         NOT NULL DEFAULT 0,
    done_names      JSONB       NOT NULL DEFAULT '[]',  -- 완료한 루틴 이름들
    remaining_names JSONB       NOT NULL DEFAULT '[]',  -- 남은 루틴 이름들
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_summary UNIQUE (user_id, summary_date)
);

-- APNs 디바이스 토큰 (한 사용자가 여러 기기 가능)
CREATE TABLE device_tokens (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id    BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token      VARCHAR(255) NOT NULL UNIQUE,
    platform   VARCHAR(10)  NOT NULL DEFAULT 'IOS',
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_device_user ON device_tokens (user_id);
