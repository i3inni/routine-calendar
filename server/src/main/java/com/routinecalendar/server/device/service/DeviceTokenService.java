package com.routinecalendar.server.device.service;
import com.routinecalendar.server.device.domain.DeviceToken;
import com.routinecalendar.server.device.domain.Platform;
import com.routinecalendar.server.device.repository.DeviceTokenRepository;

import com.routinecalendar.server.common.error.BusinessException;
import com.routinecalendar.server.common.error.ErrorCode;
import com.routinecalendar.server.user.domain.User;
import com.routinecalendar.server.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeviceTokenService {

    private final UserRepository userRepository;
    private final DeviceTokenRepository deviceTokenRepository;

    public DeviceTokenService(UserRepository userRepository,
                              DeviceTokenRepository deviceTokenRepository) {
        this.userRepository = userRepository;
        this.deviceTokenRepository = deviceTokenRepository;
    }

    /** 토큰 등록(upsert): 같은 토큰이 있으면 소유자만 갱신, 없으면 새로 저장. */
    @Transactional
    public void register(Long meId, String token, Platform platform) {
        User me = userRepository.findById(meId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        Platform p = platform != null ? platform : Platform.IOS;

        deviceTokenRepository.findByToken(token).ifPresentOrElse(
                existing -> existing.reassign(me, p),
                () -> deviceTokenRepository.save(new DeviceToken(me, token, p))
        );
    }
}
