package com.routinecalendar.server.friend;

import com.routinecalendar.server.user.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FriendRequestRepository extends JpaRepository<FriendRequest, Long> {

    /** 내가 받은 특정 상태의 요청들. 보낸 사람(requester)을 fetch join (N+1 방지). */
    @Query("""
            select fr from FriendRequest fr
            join fetch fr.requester
            where fr.addressee = :me and fr.status = :status
            """)
    List<FriendRequest> findIncoming(@Param("me") User me, @Param("status") FriendRequestStatus status);

    Optional<FriendRequest> findByRequesterAndAddresseeAndStatus(
            User requester, User addressee, FriendRequestStatus status);
}
