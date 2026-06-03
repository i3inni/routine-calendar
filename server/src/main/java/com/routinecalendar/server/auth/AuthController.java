package com.routinecalendar.server.auth;

import com.routinecalendar.server.auth.AuthDtos.AuthResponse;
import com.routinecalendar.server.auth.AuthDtos.DevLoginRequest;
import com.routinecalendar.server.auth.AuthDtos.KakaoLoginRequest;
import com.routinecalendar.server.auth.AuthDtos.RefreshRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /** 카카오 로그인 */
    @PostMapping("/kakao")
    public AuthResponse kakaoLogin(@Valid @RequestBody KakaoLoginRequest request) {
        return authService.kakaoLogin(request);
    }

    /** 자동 로그인 / 토큰 갱신 */
    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request);
    }

    /** 개발용 로그인 (카카오 없이) */
    @PostMapping("/dev-login")
    public AuthResponse devLogin(@RequestBody DevLoginRequest request) {
        return authService.devLogin(request);
    }
}
