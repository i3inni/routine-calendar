package com.routinecalendar.server.friend.service;

import com.routinecalendar.server.common.AppTime;
import com.routinecalendar.server.routine.domain.Routine;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * 친구에게 보여줄 "오늘 요약"(완료/남은 루틴·진행률·연속일)을 서버에서 직접 계산한다.
 *
 * 과거엔 클라가 올린 DailySummary(날짜별 스냅샷)에 의존 → 친구가 앱을 그날 안 열면
 * 자정 이후 오늘 행이 없어 친구 루틴이 통째로 사라지는 버그가 있었다.
 * 이제 루틴/완료기록이 서버에 동기화돼 있으므로, 서버가 즉석 계산해 항상 최신을 보장한다.
 *
 * 순수 함수(DB 접근 없음) → 트랜잭션 불필요, 단위 테스트가 쉽다.
 * 예정/완료/스트릭 판정 규칙은 iOS의 Routine.isScheduled(on:)·streak()과 동일하게 맞춘다.
 */
@Component
public class FriendTodayCalculator {

    private static final int MAX_STREAK_LOOKBACK = 366;

    /** 친구 1명의 오늘 요약 결과 */
    public record TodayStat(int doneCount, int totalCount, int streak,
                            List<String> done, List<String> remaining) {
        public static final TodayStat EMPTY = new TodayStat(0, 0, 0, List.of(), List.of());
    }

    /**
     * @param routines        친구의 활성 루틴
     * @param countsByRoutine routineId → (날짜 → 완료 카운트)  (최근 약 1년치)
     * @param today           오늘(KST)
     */
    public TodayStat compute(List<Routine> routines,
                             Map<UUID, Map<LocalDate, Integer>> countsByRoutine,
                             LocalDate today) {
        if (routines.isEmpty()) {
            return TodayStat.EMPTY;
        }
        int weekday = weekdayOf(today);
        List<String> done = new ArrayList<>();
        List<String> remaining = new ArrayList<>();
        int bestStreak = 0;

        for (Routine r : routines) {
            Map<LocalDate, Integer> counts = countsByRoutine.getOrDefault(r.getId(), Map.of());
            if (scheduledOn(r, today, weekday)) {
                (isDone(r, counts, today) ? done : remaining).add(r.getName());
            }
            bestStreak = Math.max(bestStreak, streak(r, counts, today));
        }
        return new TodayStat(done.size(), done.size() + remaining.size(), bestStreak, done, remaining);
    }

    /** 해당 날짜에 예정됐는지: 요일 + 시작일(이후) + 종료일(이전) 게이팅 */
    private boolean scheduledOn(Routine r, LocalDate date, int weekday) {
        if (!r.isScheduledOn(weekday)) {
            return false;
        }
        LocalDate start = LocalDate.ofInstant(r.getCreatedAt(), AppTime.KST);
        if (date.isBefore(start)) {
            return false;   // 시작일 이전
        }
        if (r.getEndDate() != null && !date.isBefore(r.getEndDate())) {
            return false;   // 종료일 당일/이후
        }
        return true;
    }

    private boolean isDone(Routine r, Map<LocalDate, Integer> counts, LocalDate date) {
        return counts.getOrDefault(date, 0) >= r.getTarget();
    }

    /** 오늘부터 거슬러 올라가며 연속 완료일 계산 (예정 아닌 날은 스킵, 미완료에서 끊김) */
    private int streak(Routine r, Map<LocalDate, Integer> counts, LocalDate today) {
        LocalDate date = today;
        // 오늘이 예정 아니거나 아직 미완료면 어제부터 카운트(오늘 미완료로 스트릭이 깨지지 않게)
        if (!scheduledOn(r, today, weekdayOf(today)) || !isDone(r, counts, today)) {
            date = today.minusDays(1);
        }
        LocalDate start = LocalDate.ofInstant(r.getCreatedAt(), AppTime.KST);
        int count = 0;
        while (count < MAX_STREAK_LOOKBACK && !date.isBefore(start)) {
            if (!scheduledOn(r, date, weekdayOf(date))) {
                date = date.minusDays(1);   // 예정 아닌 날 → 스트릭 유지하며 스킵
                continue;
            }
            if (!isDone(r, counts, date)) {
                break;
            }
            count++;
            date = date.minusDays(1);
        }
        return count;
    }

    /** java DayOfWeek(1=월…7=일) → 앱 규칙(0=일…6=토) */
    private int weekdayOf(LocalDate date) {
        return date.getDayOfWeek().getValue() % 7;
    }
}
