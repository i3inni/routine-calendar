package com.routinecalendar.server.routine.controller;

import com.routinecalendar.server.routine.dto.RoutineDtos.CompletionRequest;
import com.routinecalendar.server.routine.dto.RoutineDtos.CompletionResponse;
import com.routinecalendar.server.routine.dto.RoutineDtos.RoutineRequest;
import com.routinecalendar.server.routine.dto.RoutineDtos.RoutineResponse;
import com.routinecalendar.server.routine.service.RoutineService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RoutineController {

    private final RoutineService routineService;

    public RoutineController(RoutineService routineService) {
        this.routineService = routineService;
    }

    /** 내 활성 루틴 목록 */
    @GetMapping("/me/routines")
    public List<RoutineResponse> list(@AuthenticationPrincipal Long meId) {
        return routineService.listMyRoutines(meId);
    }

    /** 루틴 생성 (클라가 UUID 포함 전송) */
    @PostMapping("/me/routines")
    public RoutineResponse create(@AuthenticationPrincipal Long meId,
                                  @Valid @RequestBody RoutineRequest request) {
        return routineService.create(meId, request);
    }

    /** 루틴 수정 */
    @PutMapping("/me/routines/{id}")
    public RoutineResponse update(@AuthenticationPrincipal Long meId,
                                  @PathVariable UUID id,
                                  @Valid @RequestBody RoutineRequest request) {
        return routineService.update(meId, id, request);
    }

    /** 루틴 삭제 (soft delete) */
    @DeleteMapping("/me/routines/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Long meId, @PathVariable UUID id) {
        routineService.delete(meId, id);
    }

    /** 내 완료기록 (선택: since 이후) */
    @GetMapping("/me/routines/completions")
    public List<CompletionResponse> completions(
            @AuthenticationPrincipal Long meId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate since) {
        return routineService.listMyCompletions(meId, since);
    }

    /** 특정 루틴의 특정 날짜 완료 카운트 upsert */
    @PutMapping("/me/routines/{id}/completions/{date}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setCompletion(@AuthenticationPrincipal Long meId,
                              @PathVariable UUID id,
                              @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                              @RequestBody CompletionRequest request) {
        routineService.setCompletion(meId, id, date, request.count());
    }
}
