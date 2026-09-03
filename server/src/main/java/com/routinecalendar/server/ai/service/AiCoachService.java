package com.routinecalendar.server.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.routinecalendar.server.ai.client.LlmClient;
import com.routinecalendar.server.ai.domain.AiCoachMessage;
import com.routinecalendar.server.ai.dto.AiDtos.CoachResponse;
import com.routinecalendar.server.ai.dto.AiDtos.PendingAction;
import com.routinecalendar.server.ai.prompt.CoachPrompt;
import com.routinecalendar.server.ai.repository.AiCoachMessageRepository;
import com.routinecalendar.server.common.AppTime;
import com.routinecalendar.server.common.RateLimiter;
import com.routinecalendar.server.common.error.BusinessException;
import com.routinecalendar.server.common.error.ErrorCode;
import com.routinecalendar.server.routine.dto.RoutineDtos.RoutineRequest;
import com.routinecalendar.server.routine.dto.RoutineDtos.RoutineResponse;
import com.routinecalendar.server.routine.service.RoutineService;
import com.routinecalendar.server.user.domain.User;
import com.routinecalendar.server.user.repository.UserRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;

/**
 * 대화형 AI 루틴 코치. 서버가 대화를 보관(계정 귀속, 여러 기기 연속).
 * 읽기·완료 도구는 즉시 실행, 생성·수정·삭제는 제안(pendingActions)으로 캡처해 사용자 확인을 거친다.
 */
@Service
public class AiCoachService {

    private static final int RATE_LIMIT = 30;                 // 악용/폭주 방어(넉넉히)
    private static final Duration RATE_WINDOW = Duration.ofMinutes(1);
    private static final int HISTORY_LIMIT = 20;              // LLM 문맥으로 넣을 최근 턴 수
    private static final String METRIC = "ai.coach";

    private final LlmClient llmClient;
    private final RoutineService routineService;
    private final AiCoachMessageRepository messageRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final RateLimiter rateLimiter;
    private final MeterRegistry meterRegistry;

    public AiCoachService(LlmClient llmClient, RoutineService routineService,
                          AiCoachMessageRepository messageRepository, UserRepository userRepository,
                          ObjectMapper objectMapper, RateLimiter rateLimiter, MeterRegistry meterRegistry) {
        this.llmClient = llmClient;
        this.routineService = routineService;
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
        this.rateLimiter = rateLimiter;
        this.meterRegistry = meterRegistry;
    }

    public CoachResponse chat(Long meId, String message) {
        if (!rateLimiter.tryAcquire("rl:ai:" + meId, RATE_LIMIT, RATE_WINDOW)) {
            count("rate_limited");
            throw new BusinessException(ErrorCode.AI_RATE_LIMITED);
        }
        try {
            User user = userRepository.findById(meId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

            // 1) 시작 메시지: system + 최근 히스토리(시간순) + 새 사용자 메시지
            List<Map<String, Object>> seed = new ArrayList<>();
            seed.add(Map.of("role", "system", "content", CoachPrompt.system(AppTime.today())));
            for (AiCoachMessage m : recentHistory(user)) {
                seed.add(Map.of("role", m.getRole(), "content", m.getContent()));
            }
            seed.add(Map.of("role", "user", "content", message));

            // 2) 에이전트 실행 (도구 콜백으로 실제 도메인 연결)
            List<PendingAction> pending = new ArrayList<>();
            String reply = llmClient.runToolLoop(seed, CoachPrompt.TOOLS,
                    (toolName, argsJson) -> executeTool(meId, toolName, argsJson, pending));

            // 3) 대화 저장(user + assistant). 도구 호출/결과는 저장하지 않는다.
            messageRepository.save(new AiCoachMessage(user, "user", message));
            messageRepository.save(new AiCoachMessage(user, "assistant", reply));

            count("success");
            return new CoachResponse(reply, pending);
        } catch (RuntimeException e) {
            count("error");
            throw e;
        }
    }

    /** 표시용 전체 히스토리(시간순). */
    public List<AiCoachMessage> history(Long meId) {
        User user = userRepository.findById(meId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return messageRepository.findByUserOrderByCreatedAtAsc(user);
    }

    private List<AiCoachMessage> recentHistory(User user) {
        List<AiCoachMessage> recent =
                messageRepository.findByUserOrderByCreatedAtDesc(user, Limit.of(HISTORY_LIMIT));
        Collections.reverse(recent);   // 최신순 → 시간순
        return recent;
    }

    // MARK: - 도구 실행

    private String executeTool(Long meId, String toolName, String argsJson, List<PendingAction> pending) {
        try {
            return switch (toolName) {
                case "list_routines"    -> writeJson(routineService.listMyRoutines(meId));
                case "complete_routine" -> completeRoutine(meId, argsJson);
                case "create_routine"   -> stageCreate(argsJson, pending);
                case "update_routine"   -> stageUpdate(meId, argsJson, pending);
                case "delete_routine"   -> stageDelete(meId, argsJson, pending);
                default -> "{\"error\":\"unknown tool\"}";
            };
        } catch (JsonProcessingException e) {
            return "{\"error\":\"invalid arguments\"}";
        }
    }

    /** 완료 처리 — 즉시 반영(사소·되돌리기 쉬움). */
    private String completeRoutine(Long meId, String argsJson) throws JsonProcessingException {
        CompleteArgs a = objectMapper.readValue(argsJson, CompleteArgs.class);
        UUID id = parseUuid(a.routineId());
        if (id == null) return "{\"error\":\"invalid routineId\"}";
        RoutineResponse r = findRoutine(meId, id);
        if (r == null) return "{\"error\":\"routine not found\"}";
        int count = a.count() != null ? a.count() : r.target();
        routineService.setCompletion(meId, id, AppTime.today(), count);
        return "{\"ok\":true,\"done\":\"" + r.name() + "\"}";
    }

    /** 생성 — 제안만 캡처. */
    private String stageCreate(String argsJson, List<PendingAction> pending) throws JsonProcessingException {
        RoutineArgs a = objectMapper.readValue(argsJson, RoutineArgs.class);
        RoutineRequest draft = new RoutineRequest(null, a.name(), a.type(), a.target(), a.unit(),
                a.reminder(), a.anytime(), a.repeatMode(), a.repeatDays(), null, null);
        pending.add(new PendingAction("create", draft, null));
        return "{\"ok\":true,\"staged\":\"create\"}";
    }

    /** 수정 — 제안만 캡처. 기존 createdAt/endDate 보존. */
    private String stageUpdate(Long meId, String argsJson, List<PendingAction> pending) throws JsonProcessingException {
        RoutineArgs a = objectMapper.readValue(argsJson, RoutineArgs.class);
        UUID id = parseUuid(a.routineId());
        if (id == null) return "{\"error\":\"invalid routineId\"}";
        RoutineResponse existing = findRoutine(meId, id);
        if (existing == null) return "{\"error\":\"routine not found\"}";
        RoutineRequest draft = new RoutineRequest(id, a.name(), a.type(), a.target(), a.unit(),
                a.reminder(), a.anytime(), a.repeatMode(), a.repeatDays(),
                existing.createdAt(), existing.endDate());
        pending.add(new PendingAction("update", draft, id.toString()));
        return "{\"ok\":true,\"staged\":\"update\"}";
    }

    /** 삭제 — 제안만 캡처(파괴적). */
    private String stageDelete(Long meId, String argsJson, List<PendingAction> pending) throws JsonProcessingException {
        DeleteArgs a = objectMapper.readValue(argsJson, DeleteArgs.class);
        UUID id = parseUuid(a.routineId());
        if (id == null) return "{\"error\":\"invalid routineId\"}";
        RoutineResponse existing = findRoutine(meId, id);
        if (existing == null) return "{\"error\":\"routine not found\"}";
        pending.add(new PendingAction("delete", null, id.toString()));
        return "{\"ok\":true,\"staged\":\"delete\",\"name\":\"" + existing.name() + "\"}";
    }

    // MARK: - 헬퍼

    private RoutineResponse findRoutine(Long meId, UUID id) {
        return routineService.listMyRoutines(meId).stream()
                .filter(r -> r.id().equals(id))
                .findFirst().orElse(null);
    }

    private UUID parseUuid(String s) {
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException | NullPointerException e) {
            return null;
        }
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

    // 도구 인자 파싱용 record
    private record CompleteArgs(String routineId, Integer count) {}

    private record RoutineArgs(String routineId, String name, String type, int target, String unit,
                               String repeatMode, List<Integer> repeatDays, String reminder, boolean anytime) {}

    private record DeleteArgs(String routineId) {}
}
