package com.routinecalendar.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 애플 로그인 검증 설정.
 * clientId: 네이티브 로그인은 신원토큰의 aud가 앱 번들ID다. 이 값과 일치해야 통과.
 */
@ConfigurationProperties(prefix = "app.apple")
public record AppleProperties(String clientId) {
}
