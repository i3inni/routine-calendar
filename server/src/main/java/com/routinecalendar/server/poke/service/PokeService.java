package com.routinecalendar.server.poke.service;
import com.routinecalendar.server.config.PokeProperties;
import com.routinecalendar.server.poke.domain.Poke;
import com.routinecalendar.server.poke.domain.PokeEvent;
import com.routinecalendar.server.poke.repository.PokeRepository;

import com.routinecalendar.server.common.error.BusinessException;
import com.routinecalendar.server.common.error.ErrorCode;
import com.routinecalendar.server.friend.repository.FriendshipRepository;
import com.routinecalendar.server.user.domain.User;
import com.routinecalendar.server.user.repository.UserRepository;
import java.time.Duration;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class PokeService {

    private final UserRepository userRepository;
    private final FriendshipRepository friendshipRepository;
    private final PokeRepository pokeRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Duration cooldown;

    public PokeService(UserRepository userRepository,
                       FriendshipRepository friendshipRepository,
                       PokeRepository pokeRepository,
                       ApplicationEventPublisher eventPublisher,
                       PokeProperties pokeProperties) {
        this.userRepository = userRepository;
        this.friendshipRepository = friendshipRepository;
        this.pokeRepository = pokeRepository;
        this.eventPublisher = eventPublisher;
        this.cooldown = Duration.ofSeconds(pokeProperties.cooldownSeconds());
    }

    @Transactional
    public void poke(Long meId, Long toUserId) {
        log.info("[콕] 요청: from={} → to={}", meId, toUserId);
        User me = getUser(meId);
        User to = getUser(toUserId);

        if (!friendshipRepository.existsBetween(me, to)) {
            log.warn("[콕] 차단: 친구 아님 from={} to={}", meId, toUserId);
            throw new BusinessException(ErrorCode.POKE_NOT_FRIEND);
        }

        // 같은 상대에게 1시간 쿨다운
        pokeRepository.findTopByFromUserAndToUserOrderByCreatedAtDesc(me, to)
                .ifPresent(last -> {
                    if (last.getCreatedAt().isAfter(Instant.now().minus(cooldown))) {
                        log.warn("[콕] 차단: 쿨다운 from={} to={} 마지막={}", meId, toUserId, last.getCreatedAt());
                        throw new BusinessException(ErrorCode.POKE_COOLDOWN);
                    }
                });

        pokeRepository.save(new Poke(me, to));
        // 커밋 후 비동기로 푸시 (PushEventListener)
        eventPublisher.publishEvent(new PokeEvent(to.getId(), me.getNickname()));
        log.info("[콕] 저장 완료 → 커밋 후 푸시 이벤트 발행: to={} from='{}'", to.getId(), me.getNickname());
    }

    private User getUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }
}
