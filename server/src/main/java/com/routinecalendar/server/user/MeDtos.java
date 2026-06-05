package com.routinecalendar.server.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/** 내 정보 관련 요청/응답 DTO. */
public final class MeDtos {

    private MeDtos() {
    }

    /** 닉네임 변경: 친구에게 보이는 이름. */
    public record UpdateNicknameRequest(@NotBlank @Size(max = 50) String nickname) {
    }

    /** 계정 삭제 예약 결과: 이 시각까지 재로그인하면 취소된다. */
    public record DeletionResponse(Instant deletionScheduledAt) {
    }
}
