package com.routinecalendar.server.push.service;

import com.routinecalendar.server.device.domain.DeviceToken;
import com.routinecalendar.server.device.repository.DeviceTokenRepository;
import com.routinecalendar.server.push.service.ApnsClient.SendResult;
import com.routinecalendar.server.user.domain.User;
import com.routinecalendar.server.user.repository.UserRepository;
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
        sendToUser(userId, title, body, null);
    }

    /** type: 앱이 분기할 커스텀 종류(예: "friend"=친구목록 갱신 트리거) */
    @Transactional
    public void sendToUser(Long userId, String title, String body, String type) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return;
        }
        List<DeviceToken> tokens = deviceTokenRepository.findByUser(user);
        if (tokens.isEmpty()) {
            log.info("등록된 기기 없음 — userId={} (앱 실행/로그인으로 토큰 재등록 필요)", userId);
            return;
        }
        log.info("푸시 발송 — userId={} 기기 {}대, title='{}'", userId, tokens.size(), title);
        for (DeviceToken dt : tokens) {
            SendResult result = apnsClient.send(dt.getToken(), title, body, type);
            log.info("푸시 결과 — userId={} token={}… result={}",
                    userId, dt.getToken().substring(0, Math.min(8, dt.getToken().length())), result);
            if (result == SendResult.UNREGISTERED) {
                deviceTokenRepository.delete(dt);
            }
        }
    }
}
