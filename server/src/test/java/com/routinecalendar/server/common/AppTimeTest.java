package com.routinecalendar.server.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class AppTimeTest {

    @Test
    void 리셋시각_0이면_그날_그대로() {
        LocalDateTime now = LocalDateTime.of(2026, 6, 30, 2, 0);   // 새벽 2시
        assertThat(AppTime.logicalDate(now, 0)).isEqualTo(LocalDate.of(2026, 6, 30));
    }

    @Test
    void 리셋시각_이전_새벽은_전날로_친다() {
        LocalDateTime now = LocalDateTime.of(2026, 6, 30, 2, 0);   // 새벽 2시, 리셋 4시
        assertThat(AppTime.logicalDate(now, 4)).isEqualTo(LocalDate.of(2026, 6, 29));
    }

    @Test
    void 리셋시각_이후는_그날() {
        LocalDateTime now = LocalDateTime.of(2026, 6, 30, 5, 0);   // 새벽 5시, 리셋 4시
        assertThat(AppTime.logicalDate(now, 4)).isEqualTo(LocalDate.of(2026, 6, 30));
    }

    @Test
    void 정확히_리셋시각이면_그날() {
        LocalDateTime now = LocalDateTime.of(2026, 6, 30, 4, 0);   // 정확히 4시
        assertThat(AppTime.logicalDate(now, 4)).isEqualTo(LocalDate.of(2026, 6, 30));
    }

    @Test
    void 낮시간은_리셋시각과_무관하게_그날() {
        LocalDateTime now = LocalDateTime.of(2026, 6, 30, 16, 0);  // 오후 4시
        assertThat(AppTime.logicalDate(now, 6)).isEqualTo(LocalDate.of(2026, 6, 30));
    }
}
