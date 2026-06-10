package com.routinecalendar.server.routine.repository;

import com.routinecalendar.server.routine.domain.Routine;
import com.routinecalendar.server.user.domain.User;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoutineRepository extends JpaRepository<Routine, UUID> {

    /** 내 활성 루틴(삭제 안 된 것) — 생성 순 */
    List<Routine> findByUserAndDeletedAtIsNullOrderByCreatedAtAsc(User user);

    /** 내 활성 루틴 1건 (소유권 검증용) */
    Optional<Routine> findByIdAndUserAndDeletedAtIsNull(UUID id, User user);
}
