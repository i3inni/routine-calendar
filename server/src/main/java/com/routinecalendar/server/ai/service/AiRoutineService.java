package com.routinecalendar.server.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.routinecalendar.server.ai.client.LlmClient;
import com.routinecalendar.server.ai.dto.AiDtos.RoutineDraftResponse;
import com.routinecalendar.server.ai.dto.ParsedRoutine;
import com.routinecalendar.server.ai.prompt.RoutineDraftPrompt;
import com.routinecalendar.server.common.RateLimiter;
import com.routinecalendar.server.common.error.BusinessException;
import com.routinecalendar.server.common.error.ErrorCode;
import com.routinecalendar.server.routine.dto.RoutineDtos.RoutineRequest;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.LocalDate;
import org.springframework.stereotype.Service;

/** ① 자연어 → 루틴 초안. LLM은 파싱만, 실제 생성은 확인 후 기존 RoutineService가 한다. */
@Service
public class AiRoutineService {

    // AI 호출은 건당 실제 비용이 든다 → 남용/폭탄 방어를 위해 분당 한도를 둔다.
    private static final int RATE_LIMIT = 20;
    private static final Duration RATE_WINDOW = Duration.ofMinutes(1);

    // 관측용 메트릭 이름. Prometheus에서 ai_routine_draft_total{result="..."}로 노출된다.
    private static final String DRAFT_METRIC = "ai.routine.draft";

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;
    private final RateLimiter rateLimiter;
    private final MeterRegistry meterRegistry;

    public AiRoutineService(LlmClient llmClient, ObjectMapper objectMapper,
                            RateLimiter rateLimiter, MeterRegistry meterRegistry) {
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
        this.rateLimiter = rateLimiter;
        this.meterRegistry = meterRegistry;
    }

    public RoutineDraftResponse draftFromText(Long meId, String text) {
        // LLM(비용)을 부르기 전에 먼저 막는다.
        if (!rateLimiter.tryAcquire("rl:ai:" + meId, RATE_LIMIT, RATE_WINDOW)) {
            count("rate_limited");
            throw new BusinessException(ErrorCode.AI_RATE_LIMITED);
        }

        try {
            LocalDate today = LocalDate.now();

            // 1) LLM 호출 → 스키마로 강제된 JSON '문자열'
            String json = llmClient.completeJson(
                    RoutineDraftPrompt.system(today),
                    text,
                    RoutineDraftPrompt.SCHEMA);

            // 2) 두 번째 파싱: JSON 문자열 → ParsedRoutine 객체
            ParsedRoutine parsed = parse(json);

            // 3) 우리 도메인 초안으로 매핑. id/createdAt/endDate는 사용자가 확인 단계에서 채우므로 null.
            RoutineRequest draft = new RoutineRequest(
                    null,
                    parsed.name(),
                    parsed.type(),
                    parsed.target(),
                    parsed.unit(),
                    parsed.reminder(),
                    parsed.anytime(),
                    parsed.repeatMode(),
                    parsed.repeatDays(),
                    null,
                    null
            );

            String message = "'" + parsed.name() + "' 루틴을 만들까요?";
            count("success");
            return new RoutineDraftResponse(draft, message, parsed.warnings());
        } catch (RuntimeException e) {
            count("error");
            throw e;
        }
    }

    private void count(String result) {
        meterRegistry.counter(DRAFT_METRIC, "result", result).increment();
    }

    private ParsedRoutine parse(String json) {
        try {
            return objectMapper.readValue(json, ParsedRoutine.class);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.AI_PROVIDER_ERROR);
        }
    }
}
