package com.routinecalendar.server.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.routinecalendar.server.ai.client.LlmClient;
import com.routinecalendar.server.ai.client.ToolExecutor;
import com.routinecalendar.server.ai.dto.AiDtos.RoutineEditResponse;
import com.routinecalendar.server.common.RateLimiter;
import com.routinecalendar.server.routine.dto.RoutineDtos.RoutineResponse;
import com.routinecalendar.server.routine.service.RoutineService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AiRoutineEditServiceTest {

    @Mock LlmClient llmClient;
    @Mock RoutineService routineService;
    @Mock RateLimiter rateLimiter;

    AiRoutineEditService service;

    @BeforeEach
    void setUp() {
        service = new AiRoutineEditService(llmClient, routineService, new ObjectMapper(),
                rateLimiter, new SimpleMeterRegistry());
    }

    @Test
    void 도구가_제안한_수정을_초안으로_돌려준다_기존_createdAt_유지() {
        UUID id = UUID.fromString("a6757aba-118d-4618-82d2-98269eff9d58");
        Instant created = Instant.parse("2026-08-01T00:00:00Z");
        RoutineResponse existing = new RoutineResponse(id, "운동", "check", 1, "", null, false,
                "daily", List.of(), created, null);

        when(rateLimiter.tryAcquire(anyString(), anyInt(), any(Duration.class))).thenReturn(true);
        when(routineService.listMyRoutines(eq(1L))).thenReturn(List.of(existing));

        // LLM이 propose_routine_update 도구를 부른 상황을 흉내낸다(executor 콜백 실행).
        when(llmClient.runToolLoop(anyString(), anyString(), anyString(), any())).thenAnswer(inv -> {
            ToolExecutor exec = inv.getArgument(3);
            String args = "{\"routineId\":\"" + id + "\",\"name\":\"운동\",\"type\":\"check\","
                    + "\"target\":1,\"unit\":\"\",\"repeatMode\":\"custom\",\"repeatDays\":[1,3,5],"
                    + "\"reminder\":\"20:00\",\"anytime\":false}";
            exec.execute("propose_routine_update", args);
            return "운동 루틴을 월수금 20:00로 바꿀까요?";
        });

        RoutineEditResponse res = service.editFromText(1L, "운동 월수금 8시로 바꿔줘");

        assertThat(res.draft()).isNotNull();
        assertThat(res.draft().id()).isEqualTo(id);
        assertThat(res.draft().repeatMode()).isEqualTo("custom");
        assertThat(res.draft().repeatDays()).containsExactly(1, 3, 5);
        assertThat(res.draft().reminder()).isEqualTo("20:00");
        assertThat(res.draft().createdAt()).isEqualTo(created);   // 기존값 보존(수정 대상 아님)
        assertThat(res.assistantMessage()).contains("월수금");
    }

    @Test
    void 대상_루틴이_없으면_제안없이_되묻는다() {
        when(rateLimiter.tryAcquire(anyString(), anyInt(), any(Duration.class))).thenReturn(true);
        when(routineService.listMyRoutines(eq(1L))).thenReturn(List.of());   // 루틴 없음

        when(llmClient.runToolLoop(anyString(), anyString(), anyString(), any())).thenAnswer(inv -> {
            ToolExecutor exec = inv.getArgument(3);
            String args = "{\"routineId\":\"" + UUID.randomUUID() + "\",\"name\":\"독서\",\"type\":\"check\","
                    + "\"target\":1,\"unit\":\"\",\"repeatMode\":\"daily\",\"repeatDays\":[],"
                    + "\"reminder\":null,\"anytime\":false}";
            exec.execute("propose_routine_update", args);   // not found → 에러 반환됨
            return "'독서' 루틴이 없어요. 확인해 주세요.";
        });

        RoutineEditResponse res = service.editFromText(1L, "독서 매일로 바꿔줘");

        assertThat(res.draft()).isNull();   // 없으니 제안 안 됨(지어내지 않음)
        assertThat(res.assistantMessage()).contains("독서");
    }
}
