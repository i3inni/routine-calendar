package com.routinecalendar.server.common.logging;

import java.util.Map;
import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

/**
 * @Async로 작업이 다른 스레드(풀)로 넘어갈 때 MDC(requestId 등)를 함께 실어 나른다.
 * ThreadLocal인 MDC는 기본적으로 스레드 경계를 넘지 못하므로 명시적으로 복사/복원한다.
 */
public class MdcTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        // decorate()는 "제출하는 스레드(요청 스레드)"에서 호출됨 → 그 시점의 MDC를 캡처
        Map<String, String> captured = MDC.getCopyOfContextMap();
        return () -> {
            // 아래 본문은 "실행하는 스레드(풀)"에서 돌아감 → 캡처한 MDC를 복원
            Map<String, String> previous = MDC.getCopyOfContextMap();
            if (captured != null) {
                MDC.setContextMap(captured);
            } else {
                MDC.clear();
            }
            try {
                runnable.run();
            } finally {
                // 풀 스레드는 재사용되므로 이전 상태로 되돌려 오염 방지
                if (previous != null) {
                    MDC.setContextMap(previous);
                } else {
                    MDC.clear();
                }
            }
        };
    }
}
