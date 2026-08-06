package com.routinecalendar.server.common;

import java.time.Duration;
import org.springframework.stereotype.Component;
import org.springframework.data.redis.core.StringRedisTemplate;

/** 
 * 고정 창(Fixed Window) 레이트 리미터. key 별로 window 동안 limit 회까지 허용. Redis INCR+EXPIRE.
 */
@Component
public class RateLimiter {
    
    private final StringRedisTemplate redis;

    public RateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** 이번 요청이 허용되면 true. 창 안에서 limit 초과면 false. */
    public boolean tryAcquire(String key, int limit, Duration window) {
        Long count = redis.opsForValue().increment(key); // INCR (키 없으면 1)
        if (count != null && count == 1L) {
            redis.expire(key, window); // 첫 요청에만 창 수명 부여
        }
        return count != null && count <= limit;
    }
}