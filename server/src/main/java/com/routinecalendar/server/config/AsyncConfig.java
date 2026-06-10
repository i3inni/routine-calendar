package com.routinecalendar.server.config;

import com.routinecalendar.server.common.logging.MdcTaskDecorator;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.aop.interceptor.SimpleAsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * @Async 활성화 — 푸시 발송 등을 요청 스레드와 분리.
 * @EnableScheduling — 유예 지난 계정 영구 삭제 등 주기 작업.
 *
 * 전용 스레드풀 + MdcTaskDecorator로, 비동기 작업에도 requestId 로그 추적이 이어지게 한다.
 */
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig implements AsyncConfigurer {

    @Override
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 거동: core까지 스레드 생성 → 그 이상은 큐에 적재 → 큐가 꽉 차야 max까지 증가.
        // 즉 평소엔 core(2)개로 처리, 폭주 시에만 max(10)까지 늘어남.
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("async-");
        executor.setTaskDecorator(new MdcTaskDecorator()); // 비동기 스레드로 MDC 전파
        // 큐+풀이 모두 포화면 작업을 버리지 않고 "제출한 스레드가 직접 실행"(백프레셔)
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        // void @Async에서 터진 예외가 조용히 사라지지 않도록 로깅
        return new SimpleAsyncUncaughtExceptionHandler();
    }
}
