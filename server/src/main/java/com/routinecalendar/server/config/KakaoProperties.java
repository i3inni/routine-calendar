package com.routinecalendar.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.kakao")
public record KakaoProperties(
        String userInfoUri
) {
}
