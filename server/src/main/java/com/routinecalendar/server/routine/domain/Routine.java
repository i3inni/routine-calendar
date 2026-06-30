package com.routinecalendar.server.routine.domain;

import com.routinecalendar.server.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

/**
 * 루틴 원본. id는 클라이언트(iOS)가 만든 UUID를 그대로 쓴다(기기 간 동일 식별).
 * 삭제는 {@code deletedAt} soft delete — 다른 기기가 삭제를 인지할 수 있게 한다.
 */
@Entity
@Table(name = "routines")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Routine implements Persistable<UUID> {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 10)
    private String type;        // check / count

    @Column(nullable = false)
    private int target;

    @Column(nullable = false, length = 20)
    private String unit;

    @Column(length = 5)
    private String reminder;    // "HH:MM" or null

    @Column(nullable = false)
    private boolean anytime;

    @Column(name = "repeat_mode", nullable = false, length = 10)
    private String repeatMode;  // daily / weekdays / custom

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "repeat_days", nullable = false)
    private List<Integer> repeatDays = new ArrayList<>();

    // 시작일: 클라(iOS)가 캘린더에서 선택한 날짜. 이 날짜(당일 포함)부터 루틴이 노출된다.
    // 자동 타임스탬프(@CreationTimestamp)를 쓰지 않고 클라가 보낸 값을 그대로 영속화한다.
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // 종료일: 이 날짜(당일 포함)부터 루틴이 사라진다(nil = 무기한). 이전 기록은 보존.
    @Column(name = "end_date")
    private LocalDate endDate;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    /** 서버 푸시 리마인더로 오늘 평가/발송한 날짜 (하루 1회 보장). */
    @Column(name = "last_reminded_on")
    private LocalDate lastRemindedOn;

    // 할당된 UUID PK라 save() 시 insert/update를 구분하기 위한 플래그.
    @Transient
    private boolean isNew = true;

    public Routine(UUID id, User user, String name, String type, int target, String unit,
                   String reminder, boolean anytime, String repeatMode, List<Integer> repeatDays,
                   Instant createdAt, LocalDate endDate) {
        this.id = id;
        this.user = user;
        this.name = name;
        this.type = type;
        this.target = target;
        this.unit = unit;
        this.reminder = reminder;
        this.anytime = anytime;
        this.repeatMode = repeatMode;
        this.repeatDays = repeatDays != null ? repeatDays : new ArrayList<>();
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.endDate = endDate;
    }

    public void update(String name, String type, int target, String unit, String reminder,
                       boolean anytime, String repeatMode, List<Integer> repeatDays,
                       LocalDate endDate) {
        this.name = name;
        this.type = type;
        this.target = target;
        this.unit = unit;
        this.reminder = reminder;
        this.anytime = anytime;
        this.repeatMode = repeatMode;
        this.repeatDays = repeatDays != null ? repeatDays : new ArrayList<>();
        this.endDate = endDate;
    }

    public void markDeleted() {
        this.deletedAt = Instant.now();
    }

    /** 오늘 리마인더를 처리했다고 기록(중복 발송 방지). */
    public void markReminded(LocalDate date) {
        this.lastRemindedOn = date;
    }

    /** 이 루틴이 해당 요일(0=일 … 6=토)에 예정돼 있는지. (iOS isScheduled와 동일 규칙) */
    public boolean isScheduledOn(int weekday) {
        return switch (repeatMode) {
            case "weekdays" -> weekday >= 1 && weekday <= 5;
            case "custom"   -> repeatDays.contains(weekday);
            default          -> true;   // daily
        };
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        this.isNew = false;
    }
}
