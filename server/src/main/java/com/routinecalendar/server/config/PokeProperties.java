package com.routinecalendar.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 콕 찌르기 설정. 같은 상대에게 다시 찌를 수 있는 최소 간격(쿨다운).
 * 운영 기본 3600초(1시간), 테스트 땐 POKE_COOLDOWN_SECONDS로 짧게.
 */
@ConfigurationProperties(prefix = "app.poke")
public record PokeProperties(long cooldownSeconds) {
}
