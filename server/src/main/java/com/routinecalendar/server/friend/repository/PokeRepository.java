package com.routinecalendar.server.friend.repository;

import com.routinecalendar.server.friend.domain.Poke;
import com.routinecalendar.server.user.domain.User;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PokeRepository extends JpaRepository<Poke, Long> {

    /** from→to 자극 중 since 이후 발생한 횟수 (쿨다운 검증용) */
    long countByFromUserAndToUserAndCreatedAtAfter(User fromUser, User toUser, Instant since);

    /** 내가 한 자극을 친구별로 집계: since 이후 횟수 + 가장 오래된 시각 (남은횟수/리셋시각 계산용) */
    @Query("""
            select p.toUser.id as friendId, count(p) as cnt, min(p.createdAt) as oldest
            from Poke p
            where p.fromUser = :me and p.createdAt >= :since
            group by p.toUser.id
            """)
    List<NudgeStat> findNudgeStats(@Param("me") User me, @Param("since") Instant since);

    /** 친구별 자극 통계 projection */
    interface NudgeStat {
        Long getFriendId();
        long getCnt();
        Instant getOldest();
    }
}
