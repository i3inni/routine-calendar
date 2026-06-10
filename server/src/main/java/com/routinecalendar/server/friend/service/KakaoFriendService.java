package com.routinecalendar.server.friend.service;

import com.routinecalendar.server.auth.dto.KakaoUserResponse;
import com.routinecalendar.server.auth.service.KakaoApiClient;
import com.routinecalendar.server.common.error.BusinessException;
import com.routinecalendar.server.common.error.ErrorCode;
import com.routinecalendar.server.friend.domain.FriendRequestStatus;
import com.routinecalendar.server.friend.dto.FriendDtos.KakaoFriendCandidate;
import com.routinecalendar.server.friend.repository.FriendRequestRepository;
import com.routinecalendar.server.friend.repository.FriendshipRepository;
import com.routinecalendar.server.user.domain.User;
import com.routinecalendar.server.user.repository.UserRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 카카오 친구 찾기: 카카오 액세스 토큰으로
 * ① 내 카카오를 연동(나도 친구들에게 검색되도록 kakaoId 저장)하고
 * ② 내 카톡 친구 중 이 앱에 가입한 사용자를 찾아 친구 추가 후보로 반환한다.
 */
@Service
public class KakaoFriendService {

    private final UserRepository userRepository;
    private final FriendshipRepository friendshipRepository;
    private final FriendRequestRepository friendRequestRepository;
    private final KakaoApiClient kakaoApiClient;

    public KakaoFriendService(UserRepository userRepository,
                              FriendshipRepository friendshipRepository,
                              FriendRequestRepository friendRequestRepository,
                              KakaoApiClient kakaoApiClient) {
        this.userRepository = userRepository;
        this.friendshipRepository = friendshipRepository;
        this.friendRequestRepository = friendRequestRepository;
        this.kakaoApiClient = kakaoApiClient;
    }

    @Transactional
    public List<KakaoFriendCandidate> findAppFriends(Long meId, String kakaoAccessToken) {
        User me = userRepository.findById(meId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // (1) 내 카카오 정보 → 연동 (나도 친구들 목록에 검색되도록)
        KakaoUserResponse kakao = kakaoApiClient.fetchUser(kakaoAccessToken);
        linkKakaoIfPossible(me, kakao.id());

        // (2) 내 카톡 친구 중 앱 사용자 매칭
        List<Long> friendKakaoIds = kakaoApiClient.fetchFriendKakaoIds(kakaoAccessToken);
        if (friendKakaoIds.isEmpty()) {
            return List.of();
        }

        // 이미 요청이 오간(PENDING) 상대는 후보에서 제외
        Set<Long> pending = new HashSet<>(
                friendRequestRepository.findCounterpartIds(me, FriendRequestStatus.PENDING));

        return userRepository.findByKakaoIdIn(friendKakaoIds).stream()
                .filter(u -> !u.getId().equals(meId))                     // 나 제외
                .filter(u -> u.getDeletionRequestedAt() == null)          // 삭제예약 제외
                .filter(u -> !friendshipRepository.existsBetween(me, u))  // 이미 친구 제외
                .filter(u -> !pending.contains(u.getId()))                // 요청 진행중 제외
                .map(u -> new KakaoFriendCandidate(
                        u.getId(), u.getHandle(), u.getNickname(), u.getProfileImageUrl()))
                .toList();
    }

    /** 현재 계정에 카카오 연동. 내 계정에 다른 카카오가 있거나, 그 카카오를 다른 계정이 쓰면 충돌. */
    private void linkKakaoIfPossible(User me, Long kakaoId) {
        if (kakaoId == null || kakaoId.equals(me.getKakaoId())) {
            return;
        }
        // 내 계정에 이미 다른 카카오가 연동돼 있으면 덮어쓰지 않고 막는다.
        if (me.getKakaoId() != null) {
            throw new BusinessException(ErrorCode.ACCOUNT_HAS_OTHER_KAKAO);
        }
        // 그 카카오를 이미 다른 계정이 쓰고 있으면 충돌
        userRepository.findByKakaoId(kakaoId).ifPresent(other -> {
            if (!other.getId().equals(me.getId())) {
                throw new BusinessException(ErrorCode.KAKAO_ALREADY_LINKED);
            }
        });
        me.linkKakao(kakaoId);
    }
}
