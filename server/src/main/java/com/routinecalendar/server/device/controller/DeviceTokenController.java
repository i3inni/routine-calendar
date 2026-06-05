package com.routinecalendar.server.device.controller;
import com.routinecalendar.server.device.service.DeviceTokenService;

import com.routinecalendar.server.device.dto.DeviceTokenDtos.RegisterRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DeviceTokenController {

    private final DeviceTokenService deviceTokenService;

    public DeviceTokenController(DeviceTokenService deviceTokenService) {
        this.deviceTokenService = deviceTokenService;
    }

    /** APNs 디바이스 토큰 등록 (iOS AppDelegate가 토큰 받으면 호출) */
    @PostMapping("/me/device-token")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void register(@AuthenticationPrincipal Long meId,
                         @Valid @RequestBody RegisterRequest request) {
        deviceTokenService.register(meId, request.token(), request.platform());
    }
}
