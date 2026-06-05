package com.routinecalendar.server.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * @Async 활성화 — 푸시 발송을 요청 스레드와 분리.
 * @EnableScheduling — 유예 지난 계정 영구 삭제 등 주기 작업.
 */
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {
}
