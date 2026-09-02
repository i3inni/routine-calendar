package com.routinecalendar.server.ai.dto;

import com.routinecalendar.server.routine.dto.RoutineDtos.RoutineRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/** AI 기능 요청/응답 DTO 묶음. */
public final class AiDtos {

    private AiDtos() {}

    /** 자연어 루틴 초안 요청 */
    public record RoutineDraftRequest(
            @NotBlank @Size(max = 200) String text
    ) {}

    /** 초안 응답. draft를 사용자가 확인하면 기존 POST /me/routines로 그대로 보냄. */
    public record RoutineDraftResponse(
            RoutineRequest draft,
            String assistantMessage,
            List<String> warnings
    ) {}

    /** 자연어 루틴 수정 요청 */
    public record RoutineEditRequest(
            @NotBlank @Size(max = 200) String text
    ) {}

    /** 수정 제안 응답. draft가 있으면 사용자 확인 후 기존 PUT /me/routines/{id}로 저장.
     *  draft가 null이면 LLM이 되물은 것(assistantMessage 참고). */
    public record RoutineEditResponse(
            RoutineRequest draft,
            String assistantMessage
    ) {}
}
