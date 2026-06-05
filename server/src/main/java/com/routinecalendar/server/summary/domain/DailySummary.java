package com.routinecalendar.server.summary.domain;

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
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

/**
 * 하루치 루틴 요약. 친구에게 공유되는 데이터 (오늘 완료/남은 루틴 이름 + 진행률 + 연속일).
 * 루틴 원본은 기기에만 있고, 서버엔 이 '공유용 요약'만 올라온다.
 */
@Entity
@Table(name = "daily_summaries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DailySummary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "summary_date", nullable = false)
    private LocalDate summaryDate;

    @Column(name = "done_count", nullable = false)
    private int doneCount;

    @Column(name = "total_count", nullable = false)
    private int totalCount;

    @Column(nullable = false)
    private int streak;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "done_names", nullable = false)
    private List<String> doneNames = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "remaining_names", nullable = false)
    private List<String> remainingNames = new ArrayList<>();

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public DailySummary(User user, LocalDate summaryDate) {
        this.user = user;
        this.summaryDate = summaryDate;
    }

    /** 같은 (user, date) 행을 매번 갱신(upsert)하는 용도 */
    public void update(int doneCount, int totalCount, int streak,
                       List<String> doneNames, List<String> remainingNames) {
        this.doneCount = doneCount;
        this.totalCount = totalCount;
        this.streak = streak;
        this.doneNames = doneNames;
        this.remainingNames = remainingNames;
    }
}
