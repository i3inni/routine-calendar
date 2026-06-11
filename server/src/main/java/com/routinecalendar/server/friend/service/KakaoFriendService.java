package com.routinecalendar.server.friend.service;

import com.routinecalendar.server.auth.dto.KakaoUserResponse;
import com.routinecalendar.server.auth.service.KakaoApiClient;
import com.routinecalendar.server.friend.dto.FriendDtos.KakaoFriendCandidate;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * 카카오 친구 찾기 오케스트레이션.
 * 외부 API 호출은 트랜잭션 밖에서 하고(느린 네트워크가 DB 커넥션을 점유하지 않게),
 * DB 작업(연동 저장 + 후보 필터)은 KakaoFriendMatcher(@Transactional)에 위임한다.
 */
@Service
public class KakaoFriendService {

    private final KakaoApiClient kakaoApiClient;
    private final KakaoFriendMatcher matcher;

    public KakaoFriendService(KakaoApiClient kakaoApiClient, KakaoFriendMatcher matcher) {
        this.kakaoApiClient = kakaoApiClient;
        this.matcher = matcher;
    }

    public List<KakaoFriendCandidate> findAppFriends(Long meId, String kakaoAccessToken) {
        // 외부 호출은 트랜잭션 밖
        KakaoUserResponse kakao = kakaoApiClient.fetchUser(kakaoAccessToken);
        Map<Long, String> kakaoFriends = kakaoApiClient.fetchFriends(kakaoAccessToken);

        // DB 작업만 트랜잭션 (별도 bean → 프록시 경유 → @Transactional 정상 적용)
        return matcher.matchAndLink(meId, kakao.id(), kakaoFriends);
    }
}
