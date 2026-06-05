package com.routinecalendar.server.summary.repository;
import com.routinecalendar.server.summary.domain.DailySummary;

import com.routinecalendar.server.user.domain.User;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DailySummaryRepository extends JpaRepository<DailySummary, Long> {

    Optional<DailySummary> findByUserAndSummaryDate(User user, LocalDate summaryDate);

    /** 친구 목록의 오늘 요약을 한 번에 조회 (N+1 방지) */
    List<DailySummary> findByUserInAndSummaryDate(Collection<User> users, LocalDate summaryDate);
}
