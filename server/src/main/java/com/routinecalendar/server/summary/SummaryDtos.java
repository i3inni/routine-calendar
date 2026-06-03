package com.routinecalendar.server.summary;

import java.util.List;

public final class SummaryDtos {

    private SummaryDtos() {
    }

    /**
     * 내 오늘 요약 업로드. done/remaining 이름 목록과 연속일수를 보낸다.
     * doneCount/totalCount는 서버가 목록 크기로 계산한다.
     */
    public record SummaryUpsertRequest(
            List<String> done,
            List<String> remaining,
            int streak
    ) {
        public List<String> doneOrEmpty() {
            return done != null ? done : List.of();
        }

        public List<String> remainingOrEmpty() {
            return remaining != null ? remaining : List.of();
        }
    }
}
