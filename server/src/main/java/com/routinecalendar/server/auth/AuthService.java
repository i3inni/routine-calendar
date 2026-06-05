package com.routinecalendar.server.auth;

import com.routinecalendar.server.auth.AuthDtos.AppleLoginRequest;
import com.routinecalendar.server.auth.AuthDtos.AuthResponse;
import com.routinecalendar.server.auth.AuthDtos.DevLoginRequest;
import com.routinecalendar.server.auth.AuthDtos.KakaoLoginRequest;
import com.routinecalendar.server.auth.AuthDtos.RefreshRequest;
import com.routinecalendar.server.common.error.BusinessException;
import com.routinecalendar.server.common.error.ErrorCode;
import com.routinecalendar.server.config.AuthProperties;
import com.routinecalendar.server.security.JwtTokenProvider;
import com.routinecalendar.server.user.User;
import com.routinecalendar.server.user.UserRepository;
import com.routinecalendar.server.user.UserResponse;
import com.routinecalendar.server.user.UserService;
import io.jsonwebtoken.JwtException;
import java.security.SecureRandom;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final KakaoApiClient kakaoApiClient;
    private final AppleTokenVerifier appleTokenVerifier;
    private final UserService userService;
    private final UserRepository userRepository;
    private final JwtTokenProvider tokenProvider;
    private final AuthProperties authProperties;

    public AuthService(KakaoApiClient kakaoApiClient,
                       AppleTokenVerifier appleTokenVerifier,
                       UserService userService,
                       UserRepository userRepository,
                       JwtTokenProvider tokenProvider,
                       AuthProperties authProperties) {
        this.kakaoApiClient = kakaoApiClient;
        this.appleTokenVerifier = appleTokenVerifier;
        this.userService = userService;
        this.userRepository = userRepository;
        this.tokenProvider = tokenProvider;
        this.authProperties = authProperties;
    }

    /** 카카오 토큰 → 회원 조회/생성 → 우리 JWT 발급 */
    public AuthResponse kakaoLogin(KakaoLoginRequest request) {
        KakaoUserResponse kakaoUser = kakaoApiClient.fetchUser(request.kakaoAccessToken());
        User user = userService.getOrCreateByKakao(
                kakaoUser.id(), kakaoUser.nickname(), kakaoUser.profileImageUrl());
        return issueTokens(user);
    }

    /** 애플 신원토큰 검증 → 회원 조회/생성 → 우리 JWT 발급 */
    public AuthResponse appleLogin(AppleLoginRequest request) {
        String appleSub = appleTokenVerifier.verifyAndGetSub(request.identityToken());
        User user = userService.getOrCreateByApple(appleSub, request.name());
        return issueTokens(user);
    }

    /**
     * 자동 로그인. refresh 토큰이 유효하면 access+refresh를 새로 발급(회전)한다.
     * 회전: 쓸 때마다 refresh도 갱신 → 활성 사용자는 사실상 만료 없이 로그인 유지.
     */
    public AuthResponse refresh(RefreshRequest request) {
        Long userId;
        try {
            userId = tokenProvider.parseRefreshToken(request.refreshToken());
        } catch (JwtException e) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));
        return issueTokens(user);
    }

    /** 개발용: 카카오 없이 로그인. dev-login-enabled=true 일 때만. */
    public AuthResponse devLogin(DevLoginRequest request) {
        if (!authProperties.devLoginEnabled()) {
            throw new BusinessException(ErrorCode.DEV_LOGIN_DISABLED);
        }
        Long kakaoId = request.kakaoId() != null
                ? request.kakaoId()
                : -Math.abs(new SecureRandom().nextLong() % 1_000_000_000L); // 음수 = 가짜 카카오id
        String nickname = request.nickname() != null ? request.nickname() : "테스터";
        User user = userService.getOrCreateByKakao(kakaoId, nickname, null);
        return issueTokens(user);
    }

    private AuthResponse issueTokens(User user) {
        String access = tokenProvider.createAccessToken(user.getId());
        String refresh = tokenProvider.createRefreshToken(user.getId());
        return new AuthResponse(access, refresh, UserResponse.from(user));
    }
}
