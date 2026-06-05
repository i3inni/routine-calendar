package com.routinecalendar.server.auth;

import com.routinecalendar.server.user.UserResponse;
import jakarta.validation.constraints.NotBlank;

/** 인증 관련 요청/응답 DTO 묶음. */
public final class AuthDtos {

    private AuthDtos() {
    }

    /** 카카오 로그인: 앱이 카카오 SDK로 받은 액세스 토큰을 보낸다. */
    public record KakaoLoginRequest(@NotBlank String kakaoAccessToken) {
    }

    /** 애플 로그인: 앱이 받은 신원토큰(JWT). 이름은 최초 로그인 때만 들어온다. */
    public record AppleLoginRequest(@NotBlank String identityToken, String name) {
    }

    /** 자동 로그인/토큰 갱신: 저장해 둔 refresh 토큰을 보낸다. */
    public record RefreshRequest(@NotBlank String refreshToken) {
    }

    /** 개발용 로그인(카카오 없이). kakaoId를 주면 같은 유저로 재로그인 가능. */
    public record DevLoginRequest(Long kakaoId, String nickname) {
    }

    /** 로그인/갱신 결과: 토큰 쌍 + 내 정보. */
    public record AuthResponse(String accessToken, String refreshToken, UserResponse user) {
    }
}
