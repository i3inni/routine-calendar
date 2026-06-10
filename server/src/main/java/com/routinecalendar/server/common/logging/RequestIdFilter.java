package com.routinecalendar.server.common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 요청마다 상관관계 ID(requestId)를 발급해 MDC에 넣는다.
 * 이후 이 요청에서 찍히는 모든 로그에 같은 requestId가 붙어 한 흐름으로 추적된다.
 * (가장 먼저 실행되도록 HIGHEST_PRECEDENCE)
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";

    // 외부에서 받은 ID는 화이트리스트로만 허용(영숫자·하이픈, 1~36자) → 로그 인젝션/오염 차단
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9-]{1,36}");

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String requestId = request.getHeader(HEADER);
        if (requestId == null || !SAFE_ID.matcher(requestId).matches()) {
            requestId = UUID.randomUUID().toString().substring(0, 8);
        }
        MDC.put(MDC_KEY, requestId);
        response.setHeader(HEADER, requestId); // 클라이언트도 같은 ID를 알 수 있게
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY); // 스레드 재사용 대비 반드시 정리
        }
    }
}
