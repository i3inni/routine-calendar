package com.routinecalendar.server.routine.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.routinecalendar.server.common.AppTime;
import com.routinecalendar.server.push.service.PushService;
import com.routinecalendar.server.routine.domain.Routine;
import com.routinecalendar.server.routine.domain.RoutineCompletion;
import com.routinecalendar.server.routine.repository.RoutineCompletionRepository;
import com.routinecalendar.server.routine.repository.RoutineRepository;
import com.routinecalendar.server.user.domain.User;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReminderSchedulerTest {

    @Mock RoutineRepository routineRepository;
    @Mock RoutineCompletionRepository completionRepository;
    @Mock PushService pushService;
    @Mock User me;

    ReminderScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new ReminderScheduler(routineRepository, completionRepository, pushService);
    }

    private Routine daily(String name, int target) {
        return new Routine(UUID.randomUUID(), me, name, "check", target, "", "09:00", false, "daily", List.of());
    }

    @Test
    void 미완료_루틴이면_푸시하고_오늘_리마인드로_기록한다() {
        Routine r = daily("물 마시기", 1);
        when(me.getId()).thenReturn(7L);
        when(routineRepository.findDueReminders(any(), any())).thenReturn(List.of(r));
        when(completionRepository.findByRoutineAndCompletionDate(eq(r), any())).thenReturn(Optional.empty());

        scheduler.sendDueReminders();

        verify(pushService).sendToUser(eq(7L), eq("물 마시기"), any(), eq("routine"));
        assertThat(r.getLastRemindedOn()).isEqualTo(AppTime.today());
    }

    @Test
    void 완료한_루틴이면_푸시하지_않는다() {
        Routine r = daily("운동", 1);
        RoutineCompletion done = new RoutineCompletion(me, r, AppTime.today(), 1);   // count >= target
        when(routineRepository.findDueReminders(any(), any())).thenReturn(List.of(r));
        when(completionRepository.findByRoutineAndCompletionDate(eq(r), any())).thenReturn(Optional.of(done));

        scheduler.sendDueReminders();

        verify(pushService, never()).sendToUser(any(), any(), any(), any());
        assertThat(r.getLastRemindedOn()).isEqualTo(AppTime.today());   // 평가는 했으니 마크됨
    }

    @Test
    void 오늘_예정_요일이_아니면_건너뛴다() {
        int todayWd = AppTime.today().getDayOfWeek().getValue() % 7;   // 0=일 … 6=토
        int otherDay = (todayWd + 1) % 7;
        Routine r = new Routine(UUID.randomUUID(), me, "특정요일루틴", "check", 1, "", "09:00", false,
                "custom", List.of(otherDay));
        when(routineRepository.findDueReminders(any(), any())).thenReturn(List.of(r));

        scheduler.sendDueReminders();

        verify(pushService, never()).sendToUser(any(), any(), any(), any());
        verify(completionRepository, never()).findByRoutineAndCompletionDate(any(), any());
        assertThat(r.getLastRemindedOn()).isNull();   // 평가 안 함 → 마크 안 됨
    }
}
