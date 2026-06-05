package com.routinecalendar.server.feedback.controller;

import com.routinecalendar.server.feedback.dto.FeedbackDtos.CreateFeedbackRequest;
import com.routinecalendar.server.feedback.service.FeedbackService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FeedbackController {

    private final FeedbackService feedbackService;

    public FeedbackController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    /** 피드백/기능 요청 작성 (로그인 필요) */
    @PostMapping("/feedback")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void submit(@AuthenticationPrincipal Long userId,
                       @Valid @RequestBody CreateFeedbackRequest request) {
        feedbackService.create(userId, request.content());
    }
}
