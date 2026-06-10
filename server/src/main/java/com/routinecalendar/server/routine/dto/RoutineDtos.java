package com.routinecalendar.server.routine.dto;

import com.routinecalendar.server.routine.domain.Routine;
import com.routinecalendar.server.routine.domain.RoutineCompletion;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** 루틴 도메인 요청/응답 DTO 묶음. */
public final class RoutineDtos {

    private RoutineDtos() {
    }

    /** 루틴 생성/수정 입력. (id는 생성 시에만 사용, 수정은 경로 변수 사용) */
    public record RoutineRequest(
            UUID id,
            @NotBlank String name,
            String type,
            int target,
            String unit,
            String reminder,
            boolean anytime,
            String repeatMode,
            List<Integer> repeatDays
    ) {
        public String typeOrDefault() {
            return type != null ? type : "check";
        }

        public String repeatModeOrDefault() {
            return repeatMode != null ? repeatMode : "daily";
        }

        public String unitOrEmpty() {
            return unit != null ? unit : "";
        }

        public List<Integer> repeatDaysOrEmpty() {
            return repeatDays != null ? repeatDays : List.of();
        }
    }

    /** 루틴 1건 (iOS Routine 모델과 대응) */
    public record RoutineResponse(
            UUID id,
            String name,
            String type,
            int target,
            String unit,
            String reminder,
            boolean anytime,
            String repeatMode,
            List<Integer> repeatDays,
            Instant createdAt
    ) {
        public static RoutineResponse from(Routine r) {
            return new RoutineResponse(r.getId(), r.getName(), r.getType(), r.getTarget(),
                    r.getUnit(), r.getReminder(), r.isAnytime(), r.getRepeatMode(),
                    r.getRepeatDays(), r.getCreatedAt());
        }
    }

    /** 완료 카운트 설정 입력 */
    public record CompletionRequest(int count) {
    }

    /** 완료기록 1건 */
    public record CompletionResponse(
            UUID routineId,
            LocalDate date,
            int count
    ) {
        public static CompletionResponse from(RoutineCompletion c) {
            return new CompletionResponse(c.getRoutine().getId(), c.getCompletionDate(), c.getCount());
        }
    }
}
