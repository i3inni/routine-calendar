package com.routinecalendar.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 관리자 페이지 접근 키. (운영에선 ADMIN_KEY 환경변수로 강한 값 권장) */
@ConfigurationProperties(prefix = "app.admin")
public record AdminProperties(String key) {
}
