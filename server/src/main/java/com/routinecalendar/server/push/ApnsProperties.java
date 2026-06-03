package com.routinecalendar.server.push;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.apns")
public record ApnsProperties(
        boolean enabled,
        boolean useSandbox,
        String teamId,
        String keyId,
        String bundleId,
        String privateKey
) {
}
