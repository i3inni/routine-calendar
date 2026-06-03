package com.routinecalendar.server.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.routinecalendar.server.config.JwtProperties;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

class JwtTokenProviderTest {

    private final JwtProperties props =
            new JwtProperties("test-secret-test-secret-test-secret-0123456789", 3600, 2592000);
    private final JwtTokenProvider provider = new JwtTokenProvider(props);

    @Test
    void access_토큰_발급후_파싱하면_userId가_복원된다() {
        String token = provider.createAccessToken(42L);
        assertThat(provider.parseAccessToken(token)).isEqualTo(42L);
    }

    @Test
    void refresh_토큰은_access로_파싱되지_않는다() {
        String refresh = provider.createRefreshToken(42L);
        assertThatThrownBy(() -> provider.parseAccessToken(refresh))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void 위조된_토큰은_파싱에_실패한다() {
        assertThatThrownBy(() -> provider.parseAccessToken("not.a.jwt"))
                .isInstanceOf(JwtException.class);
    }
}
