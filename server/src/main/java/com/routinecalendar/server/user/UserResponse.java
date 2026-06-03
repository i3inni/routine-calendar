package com.routinecalendar.server.user;

public record UserResponse(
        Long id,
        String handle,
        String nickname,
        String profileImageUrl
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getHandle(),
                user.getNickname(),
                user.getProfileImageUrl()
        );
    }
}
