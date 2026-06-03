package com.routinecalendar.server.common;

import java.time.LocalDate;
import java.time.ZoneId;

/** 앱 기준 시간대(KST). "오늘"을 일관되게 계산하기 위한 헬퍼. */
public final class AppTime {

    public static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private AppTime() {
    }

    public static LocalDate today() {
        return LocalDate.now(KST);
    }
}
