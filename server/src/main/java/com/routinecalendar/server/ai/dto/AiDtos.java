package com.routinecalendar.server.ai.dto;

import com.routinecalendar.server.ai.domain.AiCoachMessage;
import com.routinecalendar.server.routine.dto.RoutineDtos.RoutineRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
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

    /** 대화형 코치 요청(새 사용자 메시지). 히스토리는 서버가 보관. */
    public record CoachRequest(
            @NotBlank @Size(max = 500) String message
    ) {}

    /** 코치 응답. reply=AI 답변, pendingActions=사용자 확인 대기 중인 구조 변경. */
    public record CoachResponse(
            String reply,
            List<PendingAction> pendingActions
    ) {}

    /** 코치가 제안한 실행 대기 액션. 사용자 확인 후 클라가 기존 루틴 API로 실행. */
    public record PendingAction(
            String kind,            // "create" | "update" | "delete" | "complete"
            RoutineRequest draft,   // create/update용 (그 외 null)
            String routineId,       // update/delete/complete용 (create면 null)
            Integer count           // complete용 오늘 완료 카운트 (그 외 null)
    ) {}

    /** 저장된 대화 한 줄(표시용). */
    public record CoachMessageResponse(
            String role,
            String content,
            Instant createdAt
    ) {
        public static CoachMessageResponse from(AiCoachMessage m) {
            return new CoachMessageResponse(m.getRole(), m.getContent(), m.getCreatedAt());
        }
    }
}
