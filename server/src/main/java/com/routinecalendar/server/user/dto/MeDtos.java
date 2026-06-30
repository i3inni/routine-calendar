package com.routinecalendar.server.user.dto;

import jakarta.validation.constraints.Size;
import java.time.Instant;

/** 내 정보 관련 요청/응답 DTO. */
public final class MeDtos {

    private MeDtos() {
    }

    /** 내 정보 변경. 보낸 필드만 갱신한다(닉네임 / 하루 리셋 시각). */
    public record UpdateMeRequest(
            @Size(max = 50) String nickname,
            Integer dayResetHour
    ) {
    }

    /** 계정 삭제 예약 결과: 이 시각까지 재로그인하면 취소된다. */
    public record DeletionResponse(Instant deletionScheduledAt) {
    }
}
