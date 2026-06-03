package com.routinecalendar.server.user;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByKakaoId(Long kakaoId);

    Optional<User> findByHandle(String handle);

    boolean existsByHandle(String handle);
}
