package com.routinecalendar.server.ai.controller;

import com.routinecalendar.server.ai.dto.AiDtos.RoutineDraftRequest;
import com.routinecalendar.server.ai.dto.AiDtos.RoutineDraftResponse;
import com.routinecalendar.server.ai.service.AiRoutineService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** AI 루틴 기능 엔드포인트. */
@RestController
public class AiRoutineController {

    private final AiRoutineService aiRoutineService;

    public AiRoutineController(AiRoutineService aiRoutineService) {
        this.aiRoutineService = aiRoutineService;
    }

    /** 자연어 → 루틴 초안(생성 X). 사용자가 확인하면 기존 POST /me/routines로 실제 생성. */
    @PostMapping("/me/ai/routine-draft")
    public RoutineDraftResponse routineDraft(@AuthenticationPrincipal Long meId,
                                             @Valid @RequestBody RoutineDraftRequest request) {
        return aiRoutineService.draftFromText(meId, request.text());
    }
}
