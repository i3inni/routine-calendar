package com.routinecalendar.server.feedback.repository;

import com.routinecalendar.server.feedback.domain.Feedback;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface FeedbackRepository extends JpaRepository<Feedback, Long> {

    /** 관리자 조회용: 작성자까지 한 번에 로딩(N+1 방지), 최신순. */
    @Query("select f from Feedback f left join fetch f.user order by f.createdAt desc")
    List<Feedback> findAllWithUser();
}
