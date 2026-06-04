package com.routinecalendar.server.user;

import com.routinecalendar.server.common.error.BusinessException;
import com.routinecalendar.server.common.error.ErrorCode;
import com.routinecalendar.server.user.MeDtos.UpdateNicknameRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 내 정보. JwtAuthenticationFilter가 세팅한 principal(userId)을 받는다.
 * 토큰이 없거나 유효하지 않으면 SecurityConfig에 의해 401.
 */
@RestController
public class MeController {

    private final UserRepository userRepository;
    private final UserService userService;

    public MeController(UserRepository userRepository, UserService userService) {
        this.userRepository = userRepository;
        this.userService = userService;
    }

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return UserResponse.from(user);
    }

    /** 닉네임(친구에게 보이는 이름) 변경. */
    @PatchMapping("/me")
    public UserResponse updateMe(@AuthenticationPrincipal Long userId,
                                 @Valid @RequestBody UpdateNicknameRequest request) {
        User user = userService.updateNickname(userId, request.nickname());
        return UserResponse.from(user);
    }
}
