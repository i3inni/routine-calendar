package com.routinecalendar.server.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 내 정보 관련 요청 DTO. */
public final class MeDtos {

    private MeDtos() {
    }

    /** 닉네임 변경: 친구에게 보이는 이름. */
    public record UpdateNicknameRequest(@NotBlank @Size(max = 50) String nickname) {
    }
}
