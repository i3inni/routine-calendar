package com.routinecalendar.server.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.routinecalendar.server.ai.client.LlmClient;
import com.routinecalendar.server.ai.client.ToolExecutor;
import com.routinecalendar.server.ai.domain.AiCoachMessage;
import com.routinecalendar.server.ai.dto.AiDtos.CoachResponse;
import com.routinecalendar.server.ai.dto.AiDtos.PendingAction;
import com.routinecalendar.server.ai.repository.AiCoachMessageRepository;
import com.routinecalendar.server.common.RateLimiter;
import com.routinecalendar.server.routine.dto.RoutineDtos.RoutineResponse;
import com.routinecalendar.server.routine.service.RoutineService;
import com.routinecalendar.server.user.domain.User;
import com.routinecalendar.server.user.repository.UserRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AiCoachServiceTest {

    @Mock LlmClient llmClient;
    @Mock RoutineService routineService;
    @Mock AiCoachMessageRepository messageRepository;
    @Mock UserRepository userRepository;
    @Mock RateLimiter rateLimiter;
    @Mock User user;

    AiCoachService service;

    @BeforeEach
    void setUp() {
        service = new AiCoachService(llmClient, routineService, messageRepository, userRepository,
                new ObjectMapper(), rateLimiter, new SimpleMeterRegistry());
        when(rateLimiter.tryAcquire(anyString(), anyInt(), any(Duration.class))).thenReturn(true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        // 히스토리 로드(빈 대화) — recentHistory가 reverse하므로 가변 리스트로.
        when(messageRepository.findByUserOrderByCreatedAtDesc(any(), any()))
                .thenReturn(new ArrayList<>());
    }

    @Test
    void 생성_요청은_제안으로만_캡처되고_저장하지_않는다() {
        when(routineService.listMyRoutines(1L)).thenReturn(List.of());   // chat이 번호목록 컨텍스트를 만든다
        when(llmClient.runToolLoop(anyList(), anyString(), any())).thenAnswer(inv -> {
            ToolExecutor exec = inv.getArgument(2);
            exec.execute("create_routine", """
                {"name":"물 마시기","type":"count","target":8,"unit":"잔",
                 "repeatMode":"daily","repeatDays":[],"reminder":null,"anytime":true}
                """);
            return "물 마시기 루틴을 만들까요?";
        });

        CoachResponse res = service.chat(1L, "물 8잔 루틴 만들어줘");

        assertThat(res.pendingActions()).hasSize(1);
        assertThat(res.pendingActions().get(0).kind()).isEqualTo("create");
        assertThat(res.pendingActions().get(0).draft().name()).isEqualTo("물 마시기");
        assertThat(res.pendingActions().get(0).draft().target()).isEqualTo(8);
        verify(routineService, never()).create(any(), any());   // 실제 저장 안 함
        verify(messageRepository, times(2)).save(any());         // user + assistant 대화 저장
    }

    @Test
    void 완료_요청도_제안으로_캡처되고_바로_반영하지_않는다() {
        UUID id = UUID.fromString("a6757aba-118d-4618-82d2-98269eff9d58");
        RoutineResponse existing = new RoutineResponse(id, "운동", "check", 1, "", null, false,
                "daily", List.of(), Instant.parse("2026-08-01T00:00:00Z"), null);
        when(routineService.listMyRoutines(1L)).thenReturn(List.of(existing));

        when(llmClient.runToolLoop(anyList(), anyString(), any())).thenAnswer(inv -> {
            ToolExecutor exec = inv.getArgument(2);
            exec.execute("complete_routine", "{\"ref\":1,\"count\":null}");   // 목록의 1번 루틴
            return "운동을 오늘 완료로 체크할까요?";
        });

        CoachResponse res = service.chat(1L, "운동 오늘 했어");

        assertThat(res.pendingActions()).hasSize(1);
        PendingAction pa = res.pendingActions().get(0);
        assertThat(pa.kind()).isEqualTo("complete");
        assertThat(pa.routineId()).isEqualTo(id.toString());
        assertThat(pa.count()).isEqualTo(1);   // count 생략 → 목표(target=1)
        // 서버가 직접 완료 처리하지 않는다(클라가 확인 후 반영)
        verify(routineService, never()).setCompletion(any(), any(), any(), anyInt());
    }
}
