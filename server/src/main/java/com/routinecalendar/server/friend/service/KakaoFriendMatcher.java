package com.routinecalendar.server.friend.service;

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
 * 카카오 친구 매칭의 DB 작업(카카오 연동 저장 + 후보 필터)만 담당.
 * 외부 API 호출과 분리된 별도 bean이라, @Transactional이 프록시를 거쳐 정상 적용되고
 * 트랜잭션을 짧게 유지한다(외부 네트워크가 DB 커넥션을 점유하지 않음).
 */
@Service
public class KakaoFriendMatcher {

    private final UserRepository userRepository;
    private final FriendshipRepository friendshipRepository;
    private final FriendRequestRepository friendRequestRepository;

    public KakaoFriendMatcher(UserRepository userRepository,
                              FriendshipRepository friendshipRepository,
                              FriendRequestRepository friendRequestRepository) {
        this.userRepository = userRepository;
        this.friendshipRepository = friendshipRepository;
        this.friendRequestRepository = friendRequestRepository;
    }

    @Transactional
    public List<KakaoFriendCandidate> matchAndLink(Long meId, Long myKakaoId, List<Long> friendKakaoIds) {
        User me = userRepository.findById(meId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 내 카카오 연동 (나도 친구들 목록에 검색되도록 kakaoId 저장)
        linkKakaoIfPossible(me, myKakaoId);

        if (friendKakaoIds.isEmpty()) {
            return List.of();
        }

        // 내 친구 id + 요청중 상대 id를 각각 한 번에 조회 → 메모리 필터 (N+1 회피)
        Set<Long> friendIds = new HashSet<>(friendshipRepository.findFriendIds(me));
        Set<Long> pending = new HashSet<>(
                friendRequestRepository.findCounterpartIds(me, FriendRequestStatus.PENDING));

        return userRepository.findByKakaoIdIn(friendKakaoIds).stream()
                .filter(u -> !u.getId().equals(meId))                // 나 제외
                .filter(u -> u.getDeletionRequestedAt() == null)     // 삭제예약 제외
                .filter(u -> !friendIds.contains(u.getId()))         // 이미 친구 제외
                .filter(u -> !pending.contains(u.getId()))           // 요청 진행중 제외
                .map(u -> new KakaoFriendCandidate(
                        u.getId(), u.getHandle(), u.getNickname(), u.getProfileImageUrl()))
                .toList();
    }

    /** 현재 계정에 카카오 연동. 내 계정에 다른 카카오가 있거나, 그 카카오를 다른 계정이 쓰면 충돌. */
    private void linkKakaoIfPossible(User me, Long kakaoId) {
        if (kakaoId == null || kakaoId.equals(me.getKakaoId())) {
            return;
        }
        if (me.getKakaoId() != null) {
            throw new BusinessException(ErrorCode.ACCOUNT_HAS_OTHER_KAKAO);
        }
        userRepository.findByKakaoId(kakaoId).ifPresent(other -> {
            if (!other.getId().equals(me.getId())) {
                throw new BusinessException(ErrorCode.KAKAO_ALREADY_LINKED);
            }
        });
        me.linkKakao(kakaoId);
    }
}
