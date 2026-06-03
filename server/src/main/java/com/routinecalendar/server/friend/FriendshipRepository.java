package com.routinecalendar.server.friend;

import com.routinecalendar.server.user.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FriendshipRepository extends JpaRepository<Friendship, Long> {

    /**
     * 내가 한쪽으로 들어간 모든 친구 관계.
     * 양쪽 User를 fetch join으로 함께 로딩해 N+1을 막는다.
     */
    @Query("""
            select f from Friendship f
            join fetch f.userLow
            join fetch f.userHigh
            where f.userLow = :user or f.userHigh = :user
            """)
    List<Friendship> findAllOf(@Param("user") User user);

    /** 두 사용자가 친구인지 (순서 무관) */
    @Query("""
            select count(f) > 0 from Friendship f
            where (f.userLow = :a and f.userHigh = :b)
               or (f.userLow = :b and f.userHigh = :a)
            """)
    boolean existsBetween(@Param("a") User a, @Param("b") User b);

    /** 두 사용자의 친구 관계 행 (순서 무관) */
    @Query("""
            select f from Friendship f
            where (f.userLow = :a and f.userHigh = :b)
               or (f.userLow = :b and f.userHigh = :a)
            """)
    Optional<Friendship> findBetween(@Param("a") User a, @Param("b") User b);
}
