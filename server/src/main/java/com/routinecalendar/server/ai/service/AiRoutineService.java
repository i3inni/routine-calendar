package com.routinecalendar.server.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.routinecalendar.server.ai.client.LlmClient;
import com.routinecalendar.server.ai.dto.AiDtos.RoutineDraftResponse;
import com.routinecalendar.server.ai.dto.ParsedRoutine;
import com.routinecalendar.server.ai.prompt.RoutineDraftPrompt;
import com.routinecalendar.server.common.error.BusinessException;
import com.routinecalendar.server.common.error.ErrorCode;
import com.routinecalendar.server.routine.dto.RoutineDtos.RoutineRequest;
import java.time.LocalDate;
import org.springframework.stereotype.Service;

/** ① 자연어 → 루틴 초안. LLM은 파싱만, 실제 생성은 확인 후 기존 RoutineService가 한다. */
@Service
public class AiRoutineService {

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;

    public AiRoutineService(LlmClient llmClient, ObjectMapper objectMapper) {
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
    }

    public RoutineDraftResponse draftFromText(Long meId, String text) {
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
        return new RoutineDraftResponse(draft, message, parsed.warnings());
    }

    private ParsedRoutine parse(String json) {
        try {
            return objectMapper.readValue(json, ParsedRoutine.class);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.AI_PROVIDER_ERROR);
        }
    }
}
