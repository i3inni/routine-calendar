package com.routinecalendar.server.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/** LLM이 자연어를 파싱해 채워주는 루틴 초안(raw). warnings는 추정 내역. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ParsedRoutine(
        String name,
        String type,
        int target,
        String unit,
        String repeatMode,
        List<Integer> repeatDays,
        String reminder,
        boolean anytime,
        List<String> warnings
) {
}
