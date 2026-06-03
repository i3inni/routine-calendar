package com.routinecalendar.server.poke;

import com.routinecalendar.server.user.User;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PokeRepository extends JpaRepository<Poke, Long> {

    /** 쿨다운 검증용: 특정 상대에게 보낸 가장 최근 콕 */
    Optional<Poke> findTopByFromUserAndToUserOrderByCreatedAtDesc(User fromUser, User toUser);

    long countByToUserAndCreatedAtAfter(User toUser, Instant after);
}
