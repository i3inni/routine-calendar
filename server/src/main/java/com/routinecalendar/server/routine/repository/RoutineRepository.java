package com.routinecalendar.server.routine.repository;

import com.routinecalendar.server.routine.domain.Routine;
import com.routinecalendar.server.user.domain.User;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoutineRepository extends JpaRepository<Routine, UUID> {

    /** 내 활성 루틴(삭제 안 된 것) — 생성 순 */
    List<Routine> findByUserAndDeletedAtIsNullOrderByCreatedAtAsc(User user);

    /** 내 활성 루틴 1건 (소유권 검증용) */
    Optional<Routine> findByIdAndUserAndDeletedAtIsNull(UUID id, User user);

    /**
     * 지금(hhmm) 리마인더 시각이고, 알림 켜졌고, 오늘 아직 리마인드 안 한 활성 루틴.
     * (요일 매칭은 repeatDays가 JSONB라 서비스에서 isScheduledOn으로 거른다)
     * user를 fetch join 해 푸시 발송 시 N+1 회피.
     */
    @Query("""
            select r from Routine r
            join fetch r.user
            where r.reminder = :hhmm
              and r.anytime = false
              and r.deletedAt is null
              and (r.lastRemindedOn is null or r.lastRemindedOn < :today)
            """)
    List<Routine> findDueReminders(@Param("hhmm") String hhmm, @Param("today") LocalDate today);
}
