package com.routinecalendar.server.user.service;
import com.routinecalendar.server.user.domain.User;
import com.routinecalendar.server.user.repository.UserRepository;

import com.routinecalendar.server.common.logging.RequestIdFilter;
import java.time.Instant;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 유예 기간(3일)이 지난 삭제 예약 계정을 영구 삭제한다.
 * users 삭제 시 친구/요청/콕/요약/기기토큰은 DB의 ON DELETE CASCADE로 함께 삭제된다.
 */
@Slf4j
@Component
public class UserPurgeScheduler {

    private final UserRepository userRepository;

    public UserPurgeScheduler(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /** 매일 새벽 4시(KST). */
    @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")
    @Transactional
    public void purgeExpiredAccounts() {
        // 요청이 아닌 스케줄 작업도 로그 추적되도록 식별자 부여
        MDC.put(RequestIdFilter.MDC_KEY, "sched-purge");
        try {
            Instant cutoff = Instant.now().minus(UserService.DELETION_GRACE);
            List<User> expired = userRepository.findByDeletionRequestedAtBefore(cutoff);
            if (expired.isEmpty()) {
                return;
            }
            log.info("[계정삭제] 유예 종료 {}건 영구 삭제", expired.size());
            userRepository.deleteAll(expired);
        } finally {
            MDC.remove(RequestIdFilter.MDC_KEY);
        }
    }
}
