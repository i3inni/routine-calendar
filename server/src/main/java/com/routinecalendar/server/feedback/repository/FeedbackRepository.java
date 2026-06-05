package com.routinecalendar.server.feedback.repository;

import com.routinecalendar.server.feedback.domain.Feedback;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeedbackRepository extends JpaRepository<Feedback, Long> {
}
