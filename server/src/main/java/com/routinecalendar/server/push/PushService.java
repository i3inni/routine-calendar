package com.routinecalendar.server.push;

import com.routinecalendar.server.device.DeviceToken;
import com.routinecalendar.server.device.DeviceTokenRepository;
import com.routinecalendar.server.push.ApnsClient.SendResult;
import com.routinecalendar.server.user.User;
import com.routinecalendar.server.user.UserRepository;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 한 사용자의 모든 기기로 푸시를 보낸다. 만료된 토큰(UNREGISTERED)은 정리한다.
 */
@Slf4j
@Service
public class PushService {

    private final UserRepository userRepository;
    private final DeviceTokenRepository deviceTokenRepository;
    private final ApnsClient apnsClient;

    public PushService(UserRepository userRepository,
                       DeviceTokenRepository deviceTokenRepository,
                       ApnsClient apnsClient) {
        this.userRepository = userRepository;
        this.deviceTokenRepository = deviceTokenRepository;
        this.apnsClient = apnsClient;
    }

    @Transactional
    public void sendToUser(Long userId, String title, String body) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return;
        }
        List<DeviceToken> tokens = deviceTokenRepository.findByUser(user);
        if (tokens.isEmpty()) {
            log.info("등록된 기기 없음 — userId={}", userId);
            return;
        }
        for (DeviceToken dt : tokens) {
            SendResult result = apnsClient.send(dt.getToken(), title, body);
            if (result == SendResult.UNREGISTERED) {
                deviceTokenRepository.delete(dt);
            }
        }
    }
}
