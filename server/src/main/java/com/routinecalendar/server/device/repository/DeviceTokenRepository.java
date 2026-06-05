package com.routinecalendar.server.device.repository;
import com.routinecalendar.server.device.domain.DeviceToken;

import com.routinecalendar.server.user.domain.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceTokenRepository extends JpaRepository<DeviceToken, Long> {

    Optional<DeviceToken> findByToken(String token);

    List<DeviceToken> findByUser(User user);
}
