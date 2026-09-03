package com.routinecalendar.server.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.routinecalendar.server.ai.client.LlmClient;
import com.routinecalendar.server.ai.domain.AiCoachMessage;
import com.routinecalendar.server.ai.dto.AiDtos.CoachMessageResponse;
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
import java.util.stream.Collectors;
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
    private static final String PROPOSAL_NOTE = "\n\n[코치메모]";  // 재제안 방지용 히스토리 마커(표시 시 제거)

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

            // 현재 루틴을 번호 목록으로 컨텍스트에 넣는다(모델은 번호로 지칭 → 긴 id 복사 불필요, 중복이름 구분).
            List<RoutineResponse> myRoutines = routineService.listMyRoutines(meId);

            // 1) 시작 메시지: system + 루틴 번호목록 + 최근 히스토리(시간순) + 새 사용자 메시지
            List<Map<String, Object>> seed = new ArrayList<>();
            seed.add(Map.of("role", "system", "content", CoachPrompt.system(AppTime.today())));
            seed.add(Map.of("role", "system", "content", routineListContext(myRoutines)));
            for (AiCoachMessage m : recentHistory(user)) {
                seed.add(Map.of("role", m.getRole(), "content", m.getContent()));
            }
            seed.add(Map.of("role", "user", "content", message));

            // 2) 에이전트 실행. 도구는 위 목록의 '번호(ref)'로 루틴을 지칭 → 서버가 실제 루틴으로 매핑.
            List<PendingAction> pending = new ArrayList<>();
            String reply = llmClient.runToolLoop(seed, CoachPrompt.TOOLS,
                    (toolName, argsJson) -> executeTool(myRoutines, toolName, argsJson, pending));

            // 한 턴에 제안은 '딱 하나'(가장 최근 동작)만. 이전 요청 재소환 등 중복을 잘라낸다.
            if (pending.size() > 1) {
                PendingAction latest = pending.get(pending.size() - 1);
                pending.clear();
                pending.add(latest);
            }

            // 3) 대화 저장. assistant엔 '이미 제안한 동작' 메모를 덧붙여 다음 턴 재제안을 막는다.
            //    (표시할 땐 history()에서 메모를 제거한다. 사용자에게 반환하는 reply는 메모 없는 원문.)
            messageRepository.save(new AiCoachMessage(user, "user", message));
            messageRepository.save(new AiCoachMessage(user, "assistant", reply + proposalNote(pending)));

            count("success");
            return new CoachResponse(reply, pending);
        } catch (RuntimeException e) {
            count("error");
            throw e;
        }
    }

    /** 표시용 전체 히스토리(시간순). 저장된 '제안 메모'는 제거해 반환. */
    public List<CoachMessageResponse> history(Long meId) {
        User user = userRepository.findById(meId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return messageRepository.findByUserOrderByCreatedAtAsc(user).stream()
                .map(m -> new CoachMessageResponse(m.getRole(), stripNote(m.getContent()), m.getCreatedAt()))
                .toList();
    }

    /** assistant 히스토리에 붙일 재제안 방지 메모(모델만 읽고, 표시 땐 제거). */
    private String proposalNote(List<PendingAction> pending) {
        if (pending.isEmpty()) return "";
        String summary = pending.stream().map(this::actionLabel).collect(Collectors.joining(", "));
        return PROPOSAL_NOTE + " 위 답변에서 이미 카드로 제안한 동작(다시 제안하지 말 것): " + summary;
    }

    private String actionLabel(PendingAction p) {
        String what = p.draft() != null ? p.draft().name()
                : (p.routineId() != null ? p.routineId() : "");
        return p.kind() + " " + what;
    }

    private static String stripNote(String content) {
        int i = content.indexOf(PROPOSAL_NOTE);
        return i >= 0 ? content.substring(0, i).stripTrailing() : content;
    }

    private List<AiCoachMessage> recentHistory(User user) {
        List<AiCoachMessage> recent =
                messageRepository.findByUserOrderByCreatedAtDesc(user, Limit.of(HISTORY_LIMIT));
        Collections.reverse(recent);   // 최신순 → 시간순
        return recent;
    }

    // MARK: - 도구 실행

    private String executeTool(List<RoutineResponse> routines, String toolName, String argsJson,
                               List<PendingAction> pending) {
        try {
            return switch (toolName) {
                case "complete_routine" -> stageComplete(routines, argsJson, pending);
                case "create_routine"   -> stageCreate(argsJson, pending);
                case "update_routine"   -> stageUpdate(routines, argsJson, pending);
                case "delete_routine"   -> stageDelete(routines, argsJson, pending);
                default -> "{\"error\":\"unknown tool\"}";
            };
        } catch (JsonProcessingException e) {
            return "{\"error\":\"invalid arguments\"}";
        }
    }

    /** 완료 — 제안만 캡처. 실제 체크는 사용자 확인 후 클라가 반영(로컬 즉시 표시). */
    private String stageComplete(List<RoutineResponse> routines, String argsJson, List<PendingAction> pending)
            throws JsonProcessingException {
        CompleteArgs a = objectMapper.readValue(argsJson, CompleteArgs.class);
        RoutineResponse r = byRef(routines, a.ref());
        if (r == null) return "{\"error\":\"routine not found\"}";
        int count = a.count() != null ? a.count() : r.target();
        pending.add(new PendingAction("complete", null, r.id().toString(), count));
        return "{\"ok\":true,\"staged\":\"complete\",\"name\":\"" + r.name() + "\"}";
    }

    /** 생성 — 제안만 캡처. */
    private String stageCreate(String argsJson, List<PendingAction> pending) throws JsonProcessingException {
        CreateArgs a = objectMapper.readValue(argsJson, CreateArgs.class);
        RoutineRequest draft = new RoutineRequest(null, a.name(), a.type(), a.target(), a.unit(),
                a.reminder(), a.anytime(), a.repeatMode(), a.repeatDays(), null, null);
        pending.add(new PendingAction("create", draft, null, null));
        return "{\"ok\":true,\"staged\":\"create\"}";
    }

    /** 수정 — 제안만 캡처. 기존 id/createdAt/endDate 보존. */
    private String stageUpdate(List<RoutineResponse> routines, String argsJson, List<PendingAction> pending)
            throws JsonProcessingException {
        UpdateArgs a = objectMapper.readValue(argsJson, UpdateArgs.class);
        RoutineResponse existing = byRef(routines, a.ref());
        if (existing == null) return "{\"error\":\"routine not found\"}";
        RoutineRequest draft = new RoutineRequest(existing.id(), a.name(), a.type(), a.target(), a.unit(),
                a.reminder(), a.anytime(), a.repeatMode(), a.repeatDays(),
                existing.createdAt(), existing.endDate());
        pending.add(new PendingAction("update", draft, existing.id().toString(), null));
        return "{\"ok\":true,\"staged\":\"update\"}";
    }

    /** 삭제 — 제안만 캡처(파괴적). */
    private String stageDelete(List<RoutineResponse> routines, String argsJson, List<PendingAction> pending)
            throws JsonProcessingException {
        DeleteArgs a = objectMapper.readValue(argsJson, DeleteArgs.class);
        RoutineResponse existing = byRef(routines, a.ref());
        if (existing == null) return "{\"error\":\"routine not found\"}";
        pending.add(new PendingAction("delete", null, existing.id().toString(), null));
        return "{\"ok\":true,\"staged\":\"delete\",\"name\":\"" + existing.name() + "\"}";
    }

    // MARK: - 헬퍼

    /** 모델에 보여줄 번호 목록. 순서는 byRef 해석과 반드시 동일(같은 리스트를 쓴다). */
    private String routineListContext(List<RoutineResponse> routines) {
        if (routines.isEmpty()) return "현재 사용자의 루틴이 없습니다.";
        StringBuilder sb = new StringBuilder("현재 사용자 루틴 (번호로 지칭):\n");
        for (int i = 0; i < routines.size(); i++) {
            RoutineResponse r = routines.get(i);
            sb.append(i + 1).append(". ").append(r.name()).append(" (").append(brief(r)).append(")\n");
        }
        return sb.toString();
    }

    private String brief(RoutineResponse r) {
        String repeat = switch (r.repeatMode()) {
            case "weekdays" -> "평일";
            case "custom" -> "요일" + r.repeatDays();
            default -> "매일";
        };
        String type = "count".equals(r.type()) ? "횟수" + r.target() + r.unit() : "체크";
        String time = r.reminder() != null ? " " + r.reminder() : "";
        return type + ", " + repeat + time;
    }

    /** 1-based 번호로 루틴 찾기(범위 밖이면 null). 컨텍스트와 같은 리스트를 쓴다. */
    private RoutineResponse byRef(List<RoutineResponse> routines, Integer ref) {
        if (ref == null || ref < 1 || ref > routines.size()) return null;
        return routines.get(ref - 1);
    }

    private void count(String result) {
        meterRegistry.counter(METRIC, "result", result).increment();
    }

    // 도구 인자 파싱용 record (기존 루틴은 ref=번호로 지칭)
    private record CompleteArgs(Integer ref, Integer count) {}

    private record CreateArgs(String name, String type, int target, String unit,
                              String repeatMode, List<Integer> repeatDays, String reminder, boolean anytime) {}

    private record UpdateArgs(Integer ref, String name, String type, int target, String unit,
                              String repeatMode, List<Integer> repeatDays, String reminder, boolean anytime) {}

    private record DeleteArgs(Integer ref) {}
}
