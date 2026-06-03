package com.routinecalendar.server.summary;

import com.routinecalendar.server.common.AppTime;
import com.routinecalendar.server.common.error.BusinessException;
import com.routinecalendar.server.common.error.ErrorCode;
import com.routinecalendar.server.summary.SummaryDtos.SummaryUpsertRequest;
import com.routinecalendar.server.user.User;
import com.routinecalendar.server.user.UserRepository;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SummaryService {

    private final UserRepository userRepository;
    private final DailySummaryRepository dailySummaryRepository;

    public SummaryService(UserRepository userRepository,
                          DailySummaryRepository dailySummaryRepository) {
        this.userRepository = userRepository;
        this.dailySummaryRepository = dailySummaryRepository;
    }

    /** 오늘 요약 upsert: (user, 오늘) 행이 있으면 갱신, 없으면 생성. */
    @Transactional
    public void upsertMySummary(Long meId, SummaryUpsertRequest request) {
        User me = userRepository.findById(meId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        LocalDate today = AppTime.today();

        DailySummary summary = dailySummaryRepository.findByUserAndSummaryDate(me, today)
                .orElseGet(() -> new DailySummary(me, today));

        List<String> done = request.doneOrEmpty();
        List<String> remaining = request.remainingOrEmpty();
        summary.update(done.size(), done.size() + remaining.size(), request.streak(), done, remaining);

        dailySummaryRepository.save(summary);
    }
}
