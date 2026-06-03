package com.routinecalendar.server.friend;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;

/** 친구 도메인 요청/응답 DTO 묶음. */
public final class FriendDtos {

    private FriendDtos() {
    }

    /** 친구 요청 보내기: 상대 handle로 검색해서 보낸다. */
    public record SendFriendRequest(@NotBlank String handle) {
    }

    /** 콕 찌르기 대상 */
    public record PokeRequest(@NotNull Long toUserId) {
    }

    /** 내가 받은 친구 요청 1건 */
    public record FriendRequestResponse(
            Long requestId,
            Long fromUserId,
            String fromHandle,
            String fromNickname,
            String fromProfileImageUrl,
            Instant createdAt
    ) {
    }

    /** 친구 1명 + 오늘 요약 (iOS Friend 모델과 대응) */
    public record FriendResponse(
            Long userId,
            String handle,
            String nickname,
            String profileImageUrl,
            int doneToday,
            int totalToday,
            int streak,
            List<String> done,
            List<String> remaining
    ) {
    }
}
