package com.routinecalendar.server.ai.repository;

import com.routinecalendar.server.ai.domain.AiCoachMessage;
import com.routinecalendar.server.user.domain.User;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiCoachMessageRepository extends JpaRepository<AiCoachMessage, Long> {

    // 전체 히스토리(표시용) — 오래된 순
    List<AiCoachMessage> findByUserOrderByCreatedAtAsc(User user);

    // 최근 N개(LLM 문맥용) — 최신순으로 뽑아 서비스에서 뒤집어 쓴다
    List<AiCoachMessage> findByUserOrderByCreatedAtDesc(User user, Limit limit);

    // 대화 초기화(선택)
    void deleteByUser(User user);
}
