package com.routinecalendar.server.friend.service;
import com.routinecalendar.server.friend.domain.Friendship;
import com.routinecalendar.server.friend.domain.FriendRequest;
import com.routinecalendar.server.friend.domain.FriendRequestStatus;
import com.routinecalendar.server.friend.domain.FriendRequestedEvent;
import com.routinecalendar.server.friend.repository.FriendshipRepository;
import com.routinecalendar.server.friend.repository.FriendRequestRepository;

import com.routinecalendar.server.common.AppTime;
import com.routinecalendar.server.common.error.BusinessException;
import com.routinecalendar.server.common.error.ErrorCode;
import com.routinecalendar.server.friend.dto.FriendDtos.FriendRequestResponse;
import com.routinecalendar.server.friend.dto.FriendDtos.FriendResponse;
import com.routinecalendar.server.poke.repository.PokeRepository;
import com.routinecalendar.server.summary.domain.DailySummary;
import com.routinecalendar.server.summary.repository.DailySummaryRepository;
import com.routinecalendar.server.user.domain.User;
import com.routinecalendar.server.user.repository.UserRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FriendService {

    private final UserRepository userRepository;
    private final FriendshipRepository friendshipRepository;
    private final FriendRequestRepository friendRequestRepository;
    private final DailySummaryRepository dailySummaryRepository;
    private final PokeRepository pokeRepository;
    private final ApplicationEventPublisher eventPublisher;

    public FriendService(UserRepository userRepository,
                         FriendshipRepository friendshipRepository,
                         FriendRequestRepository friendRequestRepository,
                         DailySummaryRepository dailySummaryRepository,
                         PokeRepository pokeRepository,
                         ApplicationEventPublisher eventPublisher) {
        this.userRepository = userRepository;
        this.friendshipRepository = friendshipRepository;
        this.friendRequestRepository = friendRequestRepository;
        this.dailySummaryRepository = dailySummaryRepository;
        this.pokeRepository = pokeRepository;
        this.eventPublisher = eventPublisher;
    }

    // MARK: - 친구 목록 (+ 오늘 요약)

    @Transactional(readOnly = true)
    public List<FriendResponse> listFriends(Long meId) {
        User me = getUser(meId);
        List<User> friends = friendshipRepository.findAllOf(me).stream()
                .map(f -> other(f, me))
                .toList();
        if (friends.isEmpty()) {
            return List.of();
        }

        LocalDate today = AppTime.today();
        Map<Long, DailySummary> summaries = dailySummaryRepository
                .findByUserInAndSummaryDate(friends, today).stream()
                .collect(Collectors.toMap(s -> s.getUser().getId(), Function.identity()));

        // 내가 각 친구에게 보낸 '마지막 콕 시각'을 한 번에 (쿨다운 표시용, 로그아웃에도 유지됨)
        Map<Long, Instant> lastPokes = pokeRepository.findLastPokeAtByMeTo(me, friends).stream()
                .collect(Collectors.toMap(r -> (Long) r[0], r -> (Instant) r[1]));

        return friends.stream()
                .map(u -> toFriendResponse(u, summaries.get(u.getId()), lastPokes.get(u.getId())))
                .toList();
    }

    // MARK: - 친구 요청 보내기

    @Transactional
    public void sendRequest(Long meId, String handle) {
        User me = getUser(meId);
        User target = userRepository.findByHandle(handle)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (target.getId().equals(me.getId())) {
            throw new BusinessException(ErrorCode.CANNOT_FRIEND_SELF);
        }
        if (friendshipRepository.existsBetween(me, target)) {
            throw new BusinessException(ErrorCode.ALREADY_FRIEND);
        }

        // 상대가 이미 나에게 보낸 요청이 있으면 → 새 요청 대신 바로 친구 성사
        var reverse = friendRequestRepository
                .findByRequesterAndAddresseeAndStatus(target, me, FriendRequestStatus.PENDING);
        if (reverse.isPresent()) {
            reverse.get().accept();
            createFriendship(me, target);
            return;
        }

        boolean alreadySent = friendRequestRepository
                .findByRequesterAndAddresseeAndStatus(me, target, FriendRequestStatus.PENDING)
                .isPresent();
        if (alreadySent) {
            throw new BusinessException(ErrorCode.FRIEND_REQUEST_ALREADY_SENT);
        }

        friendRequestRepository.save(new FriendRequest(me, target));
        // 커밋 후 비동기로 푸시 (PushEventListener)
        eventPublisher.publishEvent(new FriendRequestedEvent(target.getId(), me.getNickname()));
    }

    // MARK: - 받은 요청 목록 / 수락 / 거절

    @Transactional(readOnly = true)
    public List<FriendRequestResponse> listIncomingRequests(Long meId) {
        User me = getUser(meId);
        return friendRequestRepository.findIncoming(me, FriendRequestStatus.PENDING).stream()
                .map(this::toRequestResponse)
                .toList();
    }

    @Transactional
    public void acceptRequest(Long meId, Long requestId) {
        FriendRequest request = loadPendingRequestForMe(meId, requestId);
        request.accept();
        createFriendship(request.getRequester(), request.getAddressee());
    }

    @Transactional
    public void declineRequest(Long meId, Long requestId) {
        FriendRequest request = loadPendingRequestForMe(meId, requestId);
        request.decline();
    }

    // MARK: - 친구 끊기 (멱등)

    @Transactional
    public void removeFriend(Long meId, Long friendUserId) {
        User me = getUser(meId);
        User friend = getUser(friendUserId);
        friendshipRepository.findBetween(me, friend)
                .ifPresent(friendshipRepository::delete);
    }

    // MARK: - 헬퍼

    private FriendRequest loadPendingRequestForMe(Long meId, Long requestId) {
        FriendRequest request = friendRequestRepository.findById(requestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FRIEND_REQUEST_NOT_FOUND));
        // 나에게 온 PENDING 요청만 처리 가능
        if (!request.getAddressee().getId().equals(meId)
                || request.getStatus() != FriendRequestStatus.PENDING) {
            throw new BusinessException(ErrorCode.FRIEND_REQUEST_FORBIDDEN);
        }
        return request;
    }

    private void createFriendship(User a, User b) {
        if (!friendshipRepository.existsBetween(a, b)) {
            friendshipRepository.save(Friendship.between(a, b));
        }
    }

    private User other(Friendship f, User me) {
        return f.getUserLow().getId().equals(me.getId()) ? f.getUserHigh() : f.getUserLow();
    }

    private FriendResponse toFriendResponse(User user, DailySummary summary, Instant lastPokedAt) {
        Long lastPokedMillis = lastPokedAt != null ? lastPokedAt.toEpochMilli() : null;
        if (summary == null) {
            return new FriendResponse(user.getId(), user.getHandle(), user.getNickname(),
                    user.getProfileImageUrl(), 0, 0, 0, List.of(), List.of(), lastPokedMillis);
        }
        return new FriendResponse(user.getId(), user.getHandle(), user.getNickname(),
                user.getProfileImageUrl(),
                summary.getDoneCount(), summary.getTotalCount(), summary.getStreak(),
                summary.getDoneNames(), summary.getRemainingNames(), lastPokedMillis);
    }

    private FriendRequestResponse toRequestResponse(FriendRequest request) {
        User from = request.getRequester();
        return new FriendRequestResponse(request.getId(), from.getId(), from.getHandle(),
                from.getNickname(), from.getProfileImageUrl(), request.getCreatedAt());
    }

    private User getUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }
}
