package com.routinecalendar.server.ai.controller;

import com.routinecalendar.server.ai.dto.AiDtos.CoachMessageResponse;
import com.routinecalendar.server.ai.dto.AiDtos.CoachRequest;
import com.routinecalendar.server.ai.dto.AiDtos.CoachResponse;
import com.routinecalendar.server.ai.service.AiCoachService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 대화형 AI 루틴 코치 엔드포인트. */
@RestController
public class AiCoachController {

    private final AiCoachService coachService;

    public AiCoachController(AiCoachService coachService) {
        this.coachService = coachService;
    }

    /** 새 메시지를 보내 코치와 대화. 히스토리는 서버가 보관. */
    @PostMapping("/me/ai/coach")
    public CoachResponse coach(@AuthenticationPrincipal Long meId,
                               @Valid @RequestBody CoachRequest request) {
        return coachService.chat(meId, request.message());
    }

    /** 저장된 대화 전체(표시용). 새 기기가 과거 대화를 렌더링할 때. */
    @GetMapping("/me/ai/coach/messages")
    public List<CoachMessageResponse> messages(@AuthenticationPrincipal Long meId) {
        return coachService.history(meId);
    }
}
