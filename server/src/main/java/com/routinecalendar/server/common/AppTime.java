package com.routinecalendar.server.common;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/** 앱 기준 시간대(KST). "오늘"을 일관되게 계산하기 위한 헬퍼. */
public final class AppTime {

    public static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private AppTime() {
    }

    public static LocalDate today() {
        return LocalDate.now(KST);
    }

    /**
     * 하루 리셋 시각(새벽 resetHour시)을 반영한 '논리적 오늘'(KST).
     * resetHour=0이면 today()와 동일. 리셋 시각 이전이면 전날로 친다.
     * (예: 리셋 4시면 새벽 2시는 아직 전날)
     */
    public static LocalDate logicalToday(int resetHour) {
        return logicalDate(LocalDateTime.now(KST), resetHour);
    }

    /** 주어진 시각(KST 기준)에서 리셋 시각을 반영한 논리적 날짜. (테스트용 — 시계 비의존) */
    static LocalDate logicalDate(LocalDateTime nowKst, int resetHour) {
        return nowKst.minusHours(resetHour).toLocalDate();
    }
}
