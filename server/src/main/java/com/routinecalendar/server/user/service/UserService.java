package com.routinecalendar.server.user.service;
import com.routinecalendar.server.user.domain.User;
import com.routinecalendar.server.user.repository.UserRepository;

import com.routinecalendar.server.common.error.BusinessException;
import com.routinecalendar.server.common.error.ErrorCode;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    /** 계정 삭제 유예 기간. 이 기간 내 재로그인하면 삭제가 취소된다. */
    public static final Duration DELETION_GRACE = Duration.ofDays(3);

    // 헷갈리는 문자(0/O, 1/I/L) 제외한 친구코드용 문자셋
    private static final char[] HANDLE_CHARS = "ABCDEFGHJKMNPQRSTUVWXYZ23456789".toCharArray();
    private static final int HANDLE_LENGTH = 8;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /** 카카오 회원 번호로 기존 사용자를 찾고, 없으면 새로 만든다. (삭제 예약 중이면 취소) */
    @Transactional
    public User getOrCreateByKakao(Long kakaoId, String nickname, String profileImageUrl) {
        return userRepository.findByKakaoId(kakaoId)
                .map(this::reactivateIfPending)
                .orElseGet(() -> userRepository.save(User.builder()
                        .kakaoId(kakaoId)
                        .handle(generateUniqueHandle())
                        .nickname(nickname != null ? nickname : "사용자")
                        .profileImageUrl(profileImageUrl)
                        .build()));
    }

    /** 애플 sub로 기존 사용자를 찾고, 없으면 새로 만든다. (이름은 최초 로그인 때만, 삭제 예약 중이면 취소) */
    @Transactional
    public User getOrCreateByApple(String appleId, String nickname) {
        return userRepository.findByAppleId(appleId)
                .map(this::reactivateIfPending)
                .orElseGet(() -> userRepository.save(User.builder()
                        .appleId(appleId)
                        .handle(generateUniqueHandle())
                        .nickname(nickname != null && !nickname.isBlank() ? nickname : "사용자")
                        .build()));
    }

    /** 재로그인 시 삭제 예약을 취소한다. */
    private User reactivateIfPending(User user) {
        if (user.getDeletionRequestedAt() != null) {
            user.cancelDeletion();
        }
        return user;
    }

    /** 계정 삭제 예약. 유예 종료(=영구 삭제 예정) 시각을 반환. */
    @Transactional
    public Instant requestDeletion(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        user.requestDeletion();
        return user.getDeletionRequestedAt().plus(DELETION_GRACE);
    }

    /** 내 정보 변경: 전달된 필드만 갱신(닉네임 / 하루 리셋 시각). */
    @Transactional
    public User updateMe(Long userId, String nickname, Integer dayResetHour) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (nickname != null && !nickname.isBlank()) {
            user.updateNickname(nickname.trim());
        }
        if (dayResetHour != null) {
            user.updateDayResetHour(dayResetHour);
        }
        return user;
    }

    private String generateUniqueHandle() {
        for (int attempt = 0; attempt < 10; attempt++) {
            String handle = randomHandle();
            if (!userRepository.existsByHandle(handle)) {
                return handle;
            }
        }
        throw new IllegalStateException("고유 handle 생성에 실패했습니다.");
    }

    private String randomHandle() {
        StringBuilder sb = new StringBuilder(HANDLE_LENGTH);
        for (int i = 0; i < HANDLE_LENGTH; i++) {
            sb.append(HANDLE_CHARS[RANDOM.nextInt(HANDLE_CHARS.length)]);
        }
        return sb.toString();
    }
}
