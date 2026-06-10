package com.routinecalendar.server.routine.domain;

import com.routinecalendar.server.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * 루틴의 날짜별 완료 카운트. (routine, date) 당 한 행으로 upsert.
 */
@Entity
@Table(name = "routine_completions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RoutineCompletion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "routine_id", nullable = false)
    private Routine routine;

    @Column(name = "completion_date", nullable = false)
    private LocalDate completionDate;

    @Column(nullable = false)
    private int count;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public RoutineCompletion(User user, Routine routine, LocalDate completionDate, int count) {
        this.user = user;
        this.routine = routine;
        this.completionDate = completionDate;
        this.count = count;
    }

    public void updateCount(int count) {
        this.count = count;
    }
}
