package com.routinecalendar.server.web;

import java.time.Instant;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 부팅/배포 확인용 핑. 상세 헬스는 /actuator/health 사용. */
@RestController
public class HealthController {

    @GetMapping("/api/ping")
    public Map<String, Object> ping() {
        return Map.of(
                "status", "ok",
                "time", Instant.now().toString()
        );
    }
}
