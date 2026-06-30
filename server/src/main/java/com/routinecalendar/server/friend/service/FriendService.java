package com.routinecalendar.server.friend.service;
import com.routinecalendar.server.friend.domain.Friendship;
import com.routinecalendar.server.friend.domain.FriendRequest;
import com.routinecalendar.server.friend.domain.FriendRequestStatus;
import com.routinecalendar.server.friend.domain.FriendRequestedEvent;
import com.routinecalendar.server.friend.domain.FriendRequestAcceptedEvent;
import com.routinecalendar.server.friend.domain.Poke;
import com.routinecalendar.server.friend.repository.FriendshipRepository;
import com.routinecalendar.server.friend.repository.FriendRequestRepository;
import com.routinecalendar.server.friend.repository.PokeRepository;
import com.routinecalendar.server.friend.repository.PokeRepository.NudgeStat;

import com.routinecalendar.server.common.AppTime;
import com.routinecalendar.server.common.error.BusinessException;
import com.routinecalendar.server.common.error.ErrorCode;
import com.routinecalendar.server.friend.dto.FriendDtos.FriendRequestResponse;
import com.routinecalendar.server.friend.dto.FriendDtos.FriendResponse;
import com.routinecalendar.server.friend.dto.FriendDtos.SentFriendRequestResponse;
import com.routinecalendar.server.friend.domain.FriendNudgedEvent;
import com.routinecalendar.server.friend.service.FriendTodayCalculator.TodayStat;
import com.routinecalendar.server.routine.domain.Routine;
import com.routinecalendar.server.routine.domain.RoutineCompletion;
import com.routinecalendar.server.routine.repository.RoutineCompletionRepository;
import com.routinecalendar.server.routine.repository.RoutineRepository;
import com.routinecalendar.server.user.domain.User;
import com.routinecalendar.server.user.repository.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FriendService {

    /** 자극하기 쿨다운: 한 친구당 NUDGE_WINDOW 동안 NUDGE_LIMIT회까지 */
    private static final int NUDGE_LIMIT = 2;
    private static final Duration NUDGE_WINDOW = Duration.ofMinutes(30);

    /** 친구 스트릭 계산 시 완료기록을 조회할 최대 과거 일수 (스트릭 상한과 일치) */
    private static final int MAX_STREAK_LOOKBACK = 366;

    private final UserRepository userRepository;
    private final FriendshipRepository friendshipRepository;
    private final FriendRequestRepository friendRequestRepository;
    private final RoutineRepository routineRepository;
    private final RoutineCompletionRepository completionRepository;
    private final FriendTodayCalculator todayCalculator;
    private final PokeRepository pokeRepository;
    private final ApplicationEventPublisher eventPublisher;

    public FriendService(UserRepository userRepository,
                         FriendshipRepository friendshipRepository,
                         FriendRequestRepository friendRequestRepository,
                         RoutineRepository routineRepository,
                         RoutineCompletionRepository completionRepository,
                         FriendTodayCalculator todayCalculator,
                         PokeRepository pokeRepository,
                         ApplicationEventPublisher eventPublisher) {
        this.userRepository = userRepository;
        this.friendshipRepository = friendshipRepository;
        this.friendRequestRepository = friendRequestRepository;
        this.routineRepository = routineRepository;
        this.completionRepository = completionRepository;
        this.todayCalculator = todayCalculator;
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

        // '오늘 요약'을 친구의 루틴+완료기록으로 서버에서 즉석 계산한다.
        // (클라가 올리는 DailySummary에 의존하지 않으므로, 친구가 앱을 안 열어도 항상 최신.)
        // 친구 전체분을 배치 2번으로만 조회해 N+1을 피한다.
        Map<Long, List<Routine>> routinesByUser = routineRepository.findActiveByUsers(friends).stream()
                .collect(Collectors.groupingBy(r -> r.getUser().getId()));
        Map<Long, Map<UUID, Map<LocalDate, Integer>>> countsByUser =
                loadCounts(friends, today.minusDays(MAX_STREAK_LOOKBACK));

        // 친구별 자극 남은횟수/리셋시각 계산용 통계 (최근 30분)
        Map<Long, NudgeStat> nudgeStats = pokeRepository
                .findNudgeStats(me, Instant.now().minus(NUDGE_WINDOW)).stream()
                .collect(Collectors.toMap(NudgeStat::getFriendId, Function.identity()));

        return friends.stream()
                .map(u -> {
                    TodayStat stat = todayCalculator.compute(
                            routinesByUser.getOrDefault(u.getId(), List.of()),
                            countsByUser.getOrDefault(u.getId(), Map.of()),
                            today);
                    return toFriendResponse(u, stat, nudgeStats.get(u.getId()));
                })
                .toList();
    }

    /** 친구들의 since 이후 완료기록을 userId → routineId → (날짜 → 카운트)로 묶는다. */
    private Map<Long, Map<UUID, Map<LocalDate, Integer>>> loadCounts(List<User> friends, LocalDate since) {
        Map<Long, Map<UUID, Map<LocalDate, Integer>>> byUser = new HashMap<>();
        for (RoutineCompletion c : completionRepository.findByUserInAndCompletionDateGreaterThanEqual(friends, since)) {
            byUser.computeIfAbsent(c.getUser().getId(), k -> new HashMap<>())
                    .computeIfAbsent(c.getRoutine().getId(), k -> new HashMap<>())
                    .put(c.getCompletionDate(), c.getCount());
        }
        return byUser;
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

    @Transactional(readOnly = true)
    public List<SentFriendRequestResponse> listOutgoingRequests(Long meId) {
        User me = getUser(meId);
        return friendRequestRepository.findOutgoing(me, FriendRequestStatus.PENDING).stream()
                .map(this::toSentResponse)
                .toList();
    }

    @Transactional
    public void acceptRequest(Long meId, Long requestId) {
        FriendRequest request = loadPendingRequestForMe(meId, requestId);
        request.accept();
        createFriendship(request.getRequester(), request.getAddressee());
        // 요청 보냈던 사람에게 '수락됨' 푸시 (친구목록 갱신 트리거)
        eventPublisher.publishEvent(new FriendRequestAcceptedEvent(
                request.getRequester().getId(), request.getAddressee().getNickname()));
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

    // MARK: - 자극하기 (콕)
    @Transactional
    public void nudge(Long meId, Long friendUserId, String message) {
        User me = getUser(meId);
        User friend = getUser(friendUserId);
        if (!friendshipRepository.existsBetween(me, friend)) {
            throw new BusinessException(ErrorCode.NOT_FRIEND);
        }
        // 한 친구당 30분에 2회까지. 초과하면 쿨다운.
        long recent = pokeRepository.countByFromUserAndToUserAndCreatedAtAfter(
                me, friend, Instant.now().minus(NUDGE_WINDOW));
        if (recent >= NUDGE_LIMIT) {
            throw new BusinessException(ErrorCode.NUDGE_COOLDOWN);
        }
        pokeRepository.save(new Poke(me, friend));
        eventPublisher.publishEvent(
                new FriendNudgedEvent(friend.getId(), me.getNickname(), message));
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

    private FriendResponse toFriendResponse(User user, TodayStat stat, NudgeStat nudge) {
        long used = (nudge != null) ? nudge.getCnt() : 0;
        int nudgeRemaining = (int) Math.max(0, NUDGE_LIMIT - used);
        // 0회 남았으면 가장 오래된 자극 + 30분에 다시 가능 (epoch ms)
        Long nudgeResetAtMs = (nudgeRemaining == 0 && nudge != null)
                ? nudge.getOldest().plus(NUDGE_WINDOW).toEpochMilli() : null;

        return new FriendResponse(user.getId(), user.getHandle(), user.getNickname(),
                user.getProfileImageUrl(),
                stat.doneCount(), stat.totalCount(), stat.streak(),
                stat.done(), stat.remaining(),
                nudgeRemaining, nudgeResetAtMs);
    }

    private FriendRequestResponse toRequestResponse(FriendRequest request) {
        User from = request.getRequester();
        return new FriendRequestResponse(request.getId(), from.getId(), from.getHandle(),
                from.getNickname(), from.getProfileImageUrl(), request.getCreatedAt());
    }

    private SentFriendRequestResponse toSentResponse(FriendRequest request) {
        User to = request.getAddressee();
        return new SentFriendRequestResponse(request.getId(), to.getId(), to.getHandle(),
                to.getNickname(), to.getProfileImageUrl(), request.getCreatedAt());
    }

    private User getUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }
}
