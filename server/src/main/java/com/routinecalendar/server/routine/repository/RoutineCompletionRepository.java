package com.routinecalendar.server.routine.repository;

import com.routinecalendar.server.routine.domain.Routine;
import com.routinecalendar.server.routine.domain.RoutineCompletion;
import com.routinecalendar.server.user.domain.User;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoutineCompletionRepository extends JpaRepository<RoutineCompletion, Long> {

    /** 내 모든 완료기록 */
    List<RoutineCompletion> findByUser(User user);

    /** 특정 날짜 이후의 내 완료기록 (since 필터) */
    List<RoutineCompletion> findByUserAndCompletionDateGreaterThanEqual(User user, LocalDate since);

    /** (routine, date) 한 행 — upsert용 */
    Optional<RoutineCompletion> findByRoutineAndCompletionDate(Routine routine, LocalDate completionDate);
}
