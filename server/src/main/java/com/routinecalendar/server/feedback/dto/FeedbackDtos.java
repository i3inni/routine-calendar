package com.routinecalendar.server.feedback.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 피드백 도메인 요청 DTO. */
public final class FeedbackDtos {

    private FeedbackDtos() {
    }

    /** 피드백/기능 요청 작성. */
    public record CreateFeedbackRequest(@NotBlank @Size(max = 2000) String content) {
    }
}
