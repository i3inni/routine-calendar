package com.routinecalendar.server.user.repository;
import com.routinecalendar.server.user.domain.User;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByKakaoId(Long kakaoId);

    Optional<User> findByAppleId(String appleId);

    Optional<User> findByHandle(String handle);

    boolean existsByHandle(String handle);

    /** 유예 기간이 지나 영구 삭제 대상인 계정들 */
    List<User> findByDeletionRequestedAtBefore(Instant cutoff);
}
