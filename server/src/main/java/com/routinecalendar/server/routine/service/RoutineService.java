package com.routinecalendar.server.routine.service;

import com.routinecalendar.server.common.error.BusinessException;
import com.routinecalendar.server.common.error.ErrorCode;
import com.routinecalendar.server.routine.domain.Routine;
import com.routinecalendar.server.routine.domain.RoutineCompletion;
import com.routinecalendar.server.routine.dto.RoutineDtos.CompletionResponse;
import com.routinecalendar.server.routine.dto.RoutineDtos.RoutineRequest;
import com.routinecalendar.server.routine.dto.RoutineDtos.RoutineResponse;
import com.routinecalendar.server.routine.repository.RoutineCompletionRepository;
import com.routinecalendar.server.routine.repository.RoutineRepository;
import com.routinecalendar.server.user.domain.User;
import com.routinecalendar.server.user.repository.UserRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 루틴 CRUD + 날짜별 완료 카운트. 모든 작업은 로그인 사용자(meId) 소유 루틴에만 적용된다.
 */
@Service
public class RoutineService {

    private final UserRepository userRepository;
    private final RoutineRepository routineRepository;
    private final RoutineCompletionRepository completionRepository;

    public RoutineService(UserRepository userRepository,
                          RoutineRepository routineRepository,
                          RoutineCompletionRepository completionRepository) {
        this.userRepository = userRepository;
        this.routineRepository = routineRepository;
        this.completionRepository = completionRepository;
    }

    // MARK: - 루틴 CRUD

    @Transactional(readOnly = true)
    public List<RoutineResponse> listMyRoutines(Long meId) {
        User me = getUser(meId);
        return routineRepository.findByUserAndDeletedAtIsNullOrderByCreatedAtAsc(me).stream()
                .map(RoutineResponse::from)
                .toList();
    }

    @Transactional
    public RoutineResponse create(Long meId, RoutineRequest request) {
        User me = getUser(meId);
        UUID id = request.id() != null ? request.id() : UUID.randomUUID();
        Routine routine = new Routine(id, me, request.name(), request.typeOrDefault(),
                request.target(), request.unitOrEmpty(), request.reminder(), request.anytime(),
                request.repeatModeOrDefault(), request.repeatDaysOrEmpty(),
                request.createdAt(), request.endDate());
        return RoutineResponse.from(routineRepository.save(routine));
    }

    @Transactional
    public RoutineResponse update(Long meId, UUID routineId, RoutineRequest request) {
        Routine routine = getMyRoutine(meId, routineId);
        routine.update(request.name(), request.typeOrDefault(), request.target(),
                request.unitOrEmpty(), request.reminder(), request.anytime(),
                request.repeatModeOrDefault(), request.repeatDaysOrEmpty(),
                request.endDate());
        return RoutineResponse.from(routine);
    }

    @Transactional
    public void delete(Long meId, UUID routineId) {
        Routine routine = getMyRoutine(meId, routineId);
        routine.markDeleted();
    }

    // MARK: - 완료 카운트

    @Transactional(readOnly = true)
    public List<CompletionResponse> listMyCompletions(Long meId, LocalDate since) {
        User me = getUser(meId);
        List<RoutineCompletion> completions = (since != null)
                ? completionRepository.findByUserAndCompletionDateGreaterThanEqual(me, since)
                : completionRepository.findByUser(me);
        return completions.stream().map(CompletionResponse::from).toList();
    }

    @Transactional
    public void setCompletion(Long meId, UUID routineId, LocalDate date, int count) {
        Routine routine = getMyRoutine(meId, routineId);
        RoutineCompletion completion = completionRepository
                .findByRoutineAndCompletionDate(routine, date)
                .orElseGet(() -> new RoutineCompletion(routine.getUser(), routine, date, count));
        completion.updateCount(count);
        completionRepository.save(completion);
    }

    // MARK: - 헬퍼

    private Routine getMyRoutine(Long meId, UUID routineId) {
        User me = getUser(meId);
        return routineRepository.findByIdAndUserAndDeletedAtIsNull(routineId, me)
                .orElseThrow(() -> new BusinessException(ErrorCode.ROUTINE_NOT_FOUND));
    }

    private User getUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }
}
