package com.routinecalendar.server.user.dto;
import com.routinecalendar.server.user.domain.User;

public record UserResponse(
        Long id,
        String handle,
        String nickname,
        String profileImageUrl,
        int dayResetHour
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getHandle(),
                user.getNickname(),
                user.getProfileImageUrl(),
                user.getDayResetHour()
        );
    }
}
