package com.routinecalendar.server.web;

import com.routinecalendar.server.poke.PokeProperties;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 클라이언트가 참고하는 서버 설정값. (환경변수로 정한 값을 앱이 그대로 표시하도록)
 */
@RestController
public class ConfigController {

    private final PokeProperties pokeProperties;

    public ConfigController(PokeProperties pokeProperties) {
        this.pokeProperties = pokeProperties;
    }

    @GetMapping("/config")
    public Map<String, Object> config() {
        return Map.of("pokeCooldownSeconds", pokeProperties.cooldownSeconds());
    }
}
