package com.routinecalendar.server.routine.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.routinecalendar.server.common.error.BusinessException;
import com.routinecalendar.server.common.error.ErrorCode;
import com.routinecalendar.server.routine.domain.Routine;
import com.routinecalendar.server.routine.domain.RoutineCompletion;
import com.routinecalendar.server.routine.dto.RoutineDtos.RoutineRequest;
import com.routinecalendar.server.routine.dto.RoutineDtos.RoutineResponse;
import com.routinecalendar.server.routine.repository.RoutineCompletionRepository;
import com.routinecalendar.server.routine.repository.RoutineRepository;
import com.routinecalendar.server.user.domain.User;
import com.routinecalendar.server.user.repository.UserRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RoutineServiceTest {

    @Mock UserRepository userRepository;
    @Mock RoutineRepository routineRepository;
    @Mock RoutineCompletionRepository completionRepository;

    RoutineService routineService;

    @Mock User me;

    @BeforeEach
    void setUp() {
        routineService = new RoutineService(userRepository, routineRepository, completionRepository);
    }

    private RoutineRequest sampleRequest(UUID id) {
        return new RoutineRequest(id, "아침 스트레칭", "check", 1, "", "07:00", false,
                "custom", List.of(1, 2, 3));
    }

    @Test
    void 루틴_생성하면_내_소유로_저장되고_응답이_반환된다() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(1L)).thenReturn(Optional.of(me));
        when(routineRepository.save(any(Routine.class))).thenAnswer(inv -> inv.getArgument(0));

        RoutineResponse res = routineService.create(1L, sampleRequest(id));

        assertThat(res.id()).isEqualTo(id);
        assertThat(res.name()).isEqualTo("아침 스트레칭");
        assertThat(res.repeatDays()).containsExactly(1, 2, 3);
        verify(routineRepository).save(any(Routine.class));
    }

    @Test
    void 내_루틴은_수정된다() {
        UUID id = UUID.randomUUID();
        Routine routine = new Routine(id, me, "옛이름", "check", 1, "", null, true, "daily", List.of());
        when(userRepository.findById(1L)).thenReturn(Optional.of(me));
        when(routineRepository.findByIdAndUserAndDeletedAtIsNull(id, me)).thenReturn(Optional.of(routine));

        RoutineResponse res = routineService.update(1L, id, sampleRequest(id));

        assertThat(res.name()).isEqualTo("아침 스트레칭");
        assertThat(routine.getName()).isEqualTo("아침 스트레칭");
        assertThat(routine.getReminder()).isEqualTo("07:00");
    }

    @Test
    void 남의_루틴_수정은_차단된다() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(1L)).thenReturn(Optional.of(me));
        when(routineRepository.findByIdAndUserAndDeletedAtIsNull(id, me)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> routineService.update(1L, id, sampleRequest(id)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ROUTINE_NOT_FOUND);
    }

    @Test
    void 루틴_삭제는_soft_delete로_처리된다() {
        UUID id = UUID.randomUUID();
        Routine routine = new Routine(id, me, "이름", "check", 1, "", null, true, "daily", List.of());
        when(userRepository.findById(1L)).thenReturn(Optional.of(me));
        when(routineRepository.findByIdAndUserAndDeletedAtIsNull(id, me)).thenReturn(Optional.of(routine));

        routineService.delete(1L, id);

        assertThat(routine.getDeletedAt()).isNotNull();
    }

    @Test
    void 완료_카운트는_기존행이_있으면_갱신된다() {
        UUID id = UUID.randomUUID();
        LocalDate date = LocalDate.of(2026, 6, 10);
        Routine routine = new Routine(id, me, "이름", "count", 3, "회", null, true, "daily", List.of());
        RoutineCompletion existing = new RoutineCompletion(me, routine, date, 1);
        when(userRepository.findById(1L)).thenReturn(Optional.of(me));
        when(routineRepository.findByIdAndUserAndDeletedAtIsNull(id, me)).thenReturn(Optional.of(routine));
        when(completionRepository.findByRoutineAndCompletionDate(routine, date)).thenReturn(Optional.of(existing));

        routineService.setCompletion(1L, id, date, 3);

        assertThat(existing.getCount()).isEqualTo(3);
        verify(completionRepository).save(existing);
    }

    @Test
    void 완료_카운트는_기존행이_없으면_새로_생성된다() {
        UUID id = UUID.randomUUID();
        LocalDate date = LocalDate.of(2026, 6, 10);
        Routine routine = new Routine(id, me, "이름", "count", 3, "회", null, true, "daily", List.of());
        when(userRepository.findById(1L)).thenReturn(Optional.of(me));
        when(routineRepository.findByIdAndUserAndDeletedAtIsNull(id, me)).thenReturn(Optional.of(routine));
        when(completionRepository.findByRoutineAndCompletionDate(routine, date)).thenReturn(Optional.empty());

        routineService.setCompletion(1L, id, date, 2);

        verify(completionRepository).save(any(RoutineCompletion.class));
    }
}
