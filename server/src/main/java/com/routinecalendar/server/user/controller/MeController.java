package com.routinecalendar.server.user.controller;
import com.routinecalendar.server.user.domain.User;
import com.routinecalendar.server.user.dto.UserResponse;
import com.routinecalendar.server.user.repository.UserRepository;
import com.routinecalendar.server.user.service.UserService;

import com.routinecalendar.server.common.error.BusinessException;
import com.routinecalendar.server.common.error.ErrorCode;
import com.routinecalendar.server.user.dto.MeDtos.DeletionResponse;
import com.routinecalendar.server.user.dto.MeDtos.UpdateMeRequest;
import jakarta.validation.Valid;
import java.time.Instant;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
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

    /** 내 정보(닉네임 / 하루 리셋 시각) 변경. 보낸 필드만 갱신. */
    @PatchMapping("/me")
    public UserResponse updateMe(@AuthenticationPrincipal Long userId,
                                 @Valid @RequestBody UpdateMeRequest request) {
        User user = userService.updateMe(userId, request.nickname(), request.dayResetHour());
        return UserResponse.from(user);
    }

    /** 계정 삭제 예약(3일 유예). 유예 내 재로그인하면 취소된다. */
    @DeleteMapping("/me")
    public DeletionResponse deleteMe(@AuthenticationPrincipal Long userId) {
        Instant scheduledAt = userService.requestDeletion(userId);
        return new DeletionResponse(scheduledAt);
    }
}
