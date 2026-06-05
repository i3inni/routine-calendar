package com.routinecalendar.server.poke.repository;
import com.routinecalendar.server.poke.domain.Poke;

import com.routinecalendar.server.user.domain.User;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PokeRepository extends JpaRepository<Poke, Long> {

    /** 쿨다운 검증용: 특정 상대에게 보낸 가장 최근 콕 */
    Optional<Poke> findTopByFromUserAndToUserOrderByCreatedAtDesc(User fromUser, User toUser);

    long countByToUserAndCreatedAtAfter(User toUser, Instant after);

    /** 친구 목록용: 내가 각 친구에게 보낸 '마지막 콕 시각'을 한 번에 (N+1 방지). 반환 [toUserId, maxCreatedAt] */
    @Query("""
            select p.toUser.id, max(p.createdAt) from Poke p
            where p.fromUser = :me and p.toUser in :friends
            group by p.toUser.id
            """)
    List<Object[]> findLastPokeAtByMeTo(@Param("me") User me, @Param("friends") List<User> friends);
}
