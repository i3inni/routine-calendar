package com.routinecalendar.server.friend.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.List;
import jakarta.validation.constraints.Size;

/** 친구 도메인 요청/응답 DTO 묶음. */
public final class FriendDtos {

    private FriendDtos() {
    }

    /** 친구 요청 보내기: 상대 handle로 검색해서 보낸다. */
    public record SendFriendRequest(@NotBlank String handle) {
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

    /** 내가 보낸 친구 요청 1건 (상대=addressee 기준) */
    public record SentFriendRequestResponse(
            Long requestId,
            Long toUserId,
            String toHandle,
            String toNickname,
            String toProfileImageUrl,
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
            List<String> remaining,
            int nudgeRemaining,        // 이 친구에게 남은 자극 횟수 (0~2)
            Long nudgeResetAtMs        // 0회일 때 다시 가능해지는 시각(epoch ms), 아니면 null
    ) {
    }

    /** 친구 자극하기: 보낼 멘트 */
    public record NudgeRequest(
        @NotBlank @Size(max = 50) String message
    ){
    }

    /** 카카오 친구 찾기 요청: 클라이언트가 받은 카카오 액세스 토큰 */
    public record KakaoTokenRequest(@NotBlank String kakaoAccessToken) {
    }

    /** 카카오 친구 찾기 후보 (앱 가입 + 내 카톡친구 + 아직 친구 아님) */
    public record KakaoFriendCandidate(
            Long userId,
            String handle,
            String nickname,
            String profileImageUrl
    ) {
    }
}
