package com.routinecalendar.server.friend.repository;
import com.routinecalendar.server.friend.domain.FriendRequest;
import com.routinecalendar.server.friend.domain.FriendRequestStatus;

import com.routinecalendar.server.user.domain.User;
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

    /** 내가 보낸 특정 상태의 요청들. 받는 사람(addressee)을 fetch join (N+1 방지). */
    @Query("""
            select fr from FriendRequest fr
            join fetch fr.addressee
            where fr.requester = :me and fr.status = :status
            """)
    List<FriendRequest> findOutgoing(@Param("me") User me, @Param("status") FriendRequestStatus status);

    Optional<FriendRequest> findByRequesterAndAddresseeAndStatus(
            User requester, User addressee, FriendRequestStatus status);

    /** 나와 특정 상태의 요청이 오가는 상대들의 userId (방향 무관). 후보 제외용. */
    @Query("""
            select case when fr.requester = :me then fr.addressee.id else fr.requester.id end
            from FriendRequest fr
            where (fr.requester = :me or fr.addressee = :me) and fr.status = :status
            """)
    List<Long> findCounterpartIds(@Param("me") User me, @Param("status") FriendRequestStatus status);
}
