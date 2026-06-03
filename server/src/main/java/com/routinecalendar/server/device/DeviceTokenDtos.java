package com.routinecalendar.server.device;

import jakarta.validation.constraints.NotBlank;

public final class DeviceTokenDtos {

    private DeviceTokenDtos() {
    }

    /** APNs 디바이스 토큰 등록. platform 생략 시 IOS. */
    public record RegisterRequest(@NotBlank String token, Platform platform) {
    }
}
