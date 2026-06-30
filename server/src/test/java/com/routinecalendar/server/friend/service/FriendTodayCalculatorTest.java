package com.routinecalendar.server.friend.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.routinecalendar.server.common.AppTime;
import com.routinecalendar.server.friend.service.FriendTodayCalculator.TodayStat;
import com.routinecalendar.server.routine.domain.Routine;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FriendTodayCalculatorTest {

    private final FriendTodayCalculator calc = new FriendTodayCalculator();
    private final LocalDate today = AppTime.today();

    /** 매일 반복 check 루틴 (user는 계산에 안 쓰여 null) */
    private Routine daily(String name, LocalDate start, LocalDate end) {
        Instant createdAt = start.atStartOfDay(AppTime.KST).toInstant();
        return new Routine(UUID.randomUUID(), null, name, "check", 1, "", null, true, "daily",
                List.of(), createdAt, end);
    }

    @Test
    void 오늘_예정루틴을_완료여부로_done_remaining에_나눈다() {
        Routine a = daily("물", today.minusDays(5), null);
        Routine b = daily("운동", today.minusDays(5), null);
        Map<UUID, Map<LocalDate, Integer>> counts = new HashMap<>();
        counts.put(a.getId(), Map.of(today, 1));   // a만 오늘 완료

        TodayStat stat = calc.compute(List.of(a, b), counts, today);

        assertThat(stat.done()).containsExactly("물");
        assertThat(stat.remaining()).containsExactly("운동");
        assertThat(stat.doneCount()).isEqualTo(1);
        assertThat(stat.totalCount()).isEqualTo(2);
    }

    @Test
    void 종료일_당일부터는_오늘_목록에서_빠진다() {
        Routine ended = daily("종료된루틴", today.minusDays(10), today);   // 오늘부터 종료
        TodayStat stat = calc.compute(List.of(ended), Map.of(), today);
        assertThat(stat.totalCount()).isZero();
        assertThat(stat.remaining()).isEmpty();
    }

    @Test
    void 시작일이_미래면_오늘_목록에서_빠진다() {
        Routine future = daily("미래루틴", today.plusDays(1), null);
        TodayStat stat = calc.compute(List.of(future), Map.of(), today);
        assertThat(stat.totalCount()).isZero();
    }

    @Test
    void 스트릭은_오늘_미완료여도_어제부터의_연속완료일을_센다() {
        Routine r = daily("매일", today.minusDays(30), null);
        Map<LocalDate, Integer> c = new HashMap<>();
        c.put(today.minusDays(1), 1);
        c.put(today.minusDays(2), 1);
        c.put(today.minusDays(3), 1);   // 어제~3일전 연속, 오늘은 미완료

        TodayStat stat = calc.compute(List.of(r), Map.of(r.getId(), c), today);

        assertThat(stat.streak()).isEqualTo(3);
    }
}
