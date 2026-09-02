package com.routinecalendar.server.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.routinecalendar.server.ai.client.LlmClient;
import com.routinecalendar.server.ai.dto.AiDtos.RoutineEditResponse;
import com.routinecalendar.server.ai.prompt.RoutineEditPrompt;
import com.routinecalendar.server.common.RateLimiter;
import com.routinecalendar.server.common.error.BusinessException;
import com.routinecalendar.server.common.error.ErrorCode;
import com.routinecalendar.server.routine.dto.RoutineDtos.RoutineRequest;
import com.routinecalendar.server.routine.dto.RoutineDtos.RoutineResponse;
import com.routinecalendar.server.routine.service.RoutineService;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * ④ 자연어 루틴 수정. LLM이 Tool Calling으로 대상 루틴을 찾고(propose) 수정을 '제안'하면,
 * 서버는 제안(RoutineRequest 초안)만 만들어 반환한다. 실제 저장은 사용자 확인 후 기존 RoutineService.
 */
@Service
public class AiRoutineEditService {

    private static final int RATE_LIMIT = 20;
    private static final Duration RATE_WINDOW = Duration.ofMinutes(1);
    private static final String METRIC = "ai.routine.edit";

    private final LlmClient llmClient;
    private final RoutineService routineService;
    private final ObjectMapper objectMapper;
    private final RateLimiter rateLimiter;
    private final MeterRegistry meterRegistry;

    public AiRoutineEditService(LlmClient llmClient, RoutineService routineService,
                                ObjectMapper objectMapper, RateLimiter rateLimiter,
                                MeterRegistry meterRegistry) {
        this.llmClient = llmClient;
        this.routineService = routineService;
        this.objectMapper = objectMapper;
        this.rateLimiter = rateLimiter;
        this.meterRegistry = meterRegistry;
    }

    public RoutineEditResponse editFromText(Long meId, String text) {
        if (!rateLimiter.tryAcquire("rl:ai:" + meId, RATE_LIMIT, RATE_WINDOW)) {
            count("rate_limited");
            throw new BusinessException(ErrorCode.AI_RATE_LIMITED);
        }
        try {
            // 콜백(도구 실행)에서 채워질 '제안' 홀더. 배열로 감싸 람다에서 대입 가능하게.
            RoutineRequest[] proposal = new RoutineRequest[1];

            String finalText = llmClient.runToolLoop(
                    RoutineEditPrompt.system(LocalDate.now()),
                    text,
                    RoutineEditPrompt.TOOLS,
                    (toolName, argsJson) -> executeTool(meId, toolName, argsJson, proposal));

            count("success");
            return new RoutineEditResponse(proposal[0], finalText);
        } catch (RuntimeException e) {
            count("error");
            throw e;
        }
    }

    /** LLM이 요청한 도구를 실제 실행. 결과 JSON 문자열을 LLM에 돌려준다. */
    private String executeTool(Long meId, String toolName, String argsJson, RoutineRequest[] proposal) {
        return switch (toolName) {
            case "list_routines" -> writeJson(routineService.listMyRoutines(meId));
            case "propose_routine_update" -> handlePropose(meId, argsJson, proposal);
            default -> "{\"error\":\"unknown tool\"}";
        };
    }

    /** 쓰기 도구: 실제 저장 없이 '제안(RoutineRequest 초안)'만 만들어 홀더에 담는다. */
    private String handlePropose(Long meId, String argsJson, RoutineRequest[] proposal) {
        ProposeArgs args;
        try {
            args = objectMapper.readValue(argsJson, ProposeArgs.class);
        } catch (JsonProcessingException e) {
            return "{\"error\":\"invalid arguments\"}";
        }
        UUID id;
        try {
            id = UUID.fromString(args.routineId());
        } catch (IllegalArgumentException e) {
            return "{\"error\":\"invalid routineId\"}";
        }
        // 대상이 실제 내 루틴인지 확인 + 기존 createdAt/endDate 유지(수정 대상 아님)
        RoutineResponse existing = routineService.listMyRoutines(meId).stream()
                .filter(r -> r.id().equals(id))
                .findFirst().orElse(null);
        if (existing == null) {
            return "{\"error\":\"routine not found\"}";
        }
        proposal[0] = new RoutineRequest(
                id, args.name(), args.type(), args.target(), args.unit(),
                args.reminder(), args.anytime(), args.repeatMode(), args.repeatDays(),
                existing.createdAt(), existing.endDate());
        return "{\"ok\":true}";
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.AI_PROVIDER_ERROR);
        }
    }

    private void count(String result) {
        meterRegistry.counter(METRIC, "result", result).increment();
    }

    /** propose_routine_update 도구 인자 */
    private record ProposeArgs(String routineId, String name, String type, int target, String unit,
                               String repeatMode, List<Integer> repeatDays, String reminder, boolean anytime) {}
}
