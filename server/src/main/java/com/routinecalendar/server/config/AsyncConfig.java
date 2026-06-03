package com.routinecalendar.server.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/** @Async 활성화 — 푸시 발송을 요청 스레드와 분리해 비동기로 처리. */
@Configuration
@EnableAsync
public class AsyncConfig {
}
