package com.routinecalendar.server.summary;

import com.routinecalendar.server.summary.SummaryDtos.SummaryUpsertRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SummaryController {

    private final SummaryService summaryService;

    public SummaryController(SummaryService summaryService) {
        this.summaryService = summaryService;
    }

    /** 내 오늘 루틴 요약 업로드 (친구에게 공유됨) */
    @PostMapping("/me/summary")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void upsertMySummary(@AuthenticationPrincipal Long meId,
                                @RequestBody SummaryUpsertRequest request) {
        summaryService.upsertMySummary(meId, request);
    }
}
