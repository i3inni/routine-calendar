package com.routinecalendar.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * app.jwt.* 바인딩. (access-token-validity → accessTokenValidity 로 relaxed binding)
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        String secret,
        long accessTokenValidity,
        long refreshTokenValidity
) {
}
