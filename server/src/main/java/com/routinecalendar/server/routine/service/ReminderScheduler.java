package com.routinecalendar.server.routine.service;

import com.routinecalendar.server.common.AppTime;
import com.routinecalendar.server.push.service.PushService;
import com.routinecalendar.server.routine.domain.Routine;
import com.routinecalendar.server.routine.repository.RoutineCompletionRepository;
import com.routinecalendar.server.routine.repository.RoutineRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 루틴 리마인더 푸시 스케줄러.
 * 매분, "지금이 알림시각(HH:mm)인 활성 루틴"을 찾아 — 오늘 예정 요일이고
 * 아직 완료하지 않은 사용자에게만 푸시한다. (완료했으면 보내지 않음 = 핵심)
 *
 * 중복 방지: 처리한 루틴에 {@code lastRemindedOn=today}를 찍어 하루 1회만 평가.
 * 타임존: 모든 판정은 KST 기준({@link AppTime}).
 */
@Slf4j
@Component
public class ReminderScheduler {

    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");

    private final RoutineRepository routineRepository;
    private final RoutineCompletionRepository completionRepository;
    private final PushService pushService;

    public ReminderScheduler(RoutineRepository routineRepository,
                             RoutineCompletionRepository completionRepository,
                             PushService pushService) {
        this.routineRepository = routineRepository;
        this.completionRepository = completionRepository;
        this.pushService = pushService;
    }

    @Scheduled(cron = "0 * * * * *", zone = "Asia/Seoul")   // 매분 0초 (KST)
    @Transactional
    public void sendDueReminders() {
        LocalDate today = AppTime.today();
        int weekday = today.getDayOfWeek().getValue() % 7;   // java 1=월…7=일 → 0=일…6=토
        String hhmm = LocalTime.now(AppTime.KST).format(HHMM);

        List<Routine> due = routineRepository.findDueReminders(hhmm, today);
        if (due.isEmpty()) {
            return;
        }

        int sent = 0;
        for (Routine r : due) {
            if (!r.isScheduledOn(weekday)) {
                continue;   // 오늘 예정 요일 아님 → 평가/발송 안 함
            }
            // 시작 전(시작일이 미래)이거나 종료된(종료일 당일/이후) 루틴은 발송 대상 아님
            LocalDate startDay = LocalDate.ofInstant(r.getCreatedAt(), AppTime.KST);
            if (today.isBefore(startDay)) {
                continue;   // 아직 시작 안 함
            }
            if (r.getEndDate() != null && !today.isBefore(r.getEndDate())) {
                continue;   // today >= endDate → 종료됨
            }
            r.markReminded(today);   // 오늘 처리함(중복 발송 방지, dirty checking으로 UPDATE)

            boolean done = completionRepository.findByRoutineAndCompletionDate(r, today)
                    .map(c -> c.getCount() >= r.getTarget())
                    .orElse(false);
            if (done) {
                continue;   // 이미 완료 → 알림 보내지 않음
            }
            pushService.sendToUser(r.getUser().getId(), r.getName(),
                    "오늘 이 루틴을 완료할 시간이에요", "routine");
            sent++;
        }
        log.info("[리마인더] {} {} KST — 대상 {}건, 발송 {}건", today, hhmm, due.size(), sent);
    }
}
