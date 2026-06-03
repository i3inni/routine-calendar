package com.routinecalendar.server.user;

import java.security.SecureRandom;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    // 헷갈리는 문자(0/O, 1/I/L) 제외한 친구코드용 문자셋
    private static final char[] HANDLE_CHARS = "ABCDEFGHJKMNPQRSTUVWXYZ23456789".toCharArray();
    private static final int HANDLE_LENGTH = 8;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /** 카카오 회원 번호로 기존 사용자를 찾고, 없으면 새로 만든다. */
    @Transactional
    public User getOrCreateByKakao(Long kakaoId, String nickname, String profileImageUrl) {
        return userRepository.findByKakaoId(kakaoId)
                .orElseGet(() -> userRepository.save(User.builder()
                        .kakaoId(kakaoId)
                        .handle(generateUniqueHandle())
                        .nickname(nickname != null ? nickname : "사용자")
                        .profileImageUrl(profileImageUrl)
                        .build()));
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
