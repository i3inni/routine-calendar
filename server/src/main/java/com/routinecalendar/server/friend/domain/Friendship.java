package com.routinecalendar.server.friend.domain;

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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

/**
 * 친구 관계. 중복(A-B / B-A)을 막기 위해 항상 id가 작은 쪽을 {@code userLow}로 정렬해
 * 한 행으로만 저장한다. (DB의 UNIQUE + CHECK 제약으로도 강제)
 */
@Entity
@Table(name = "friendships")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Friendship {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_low_id", nullable = false)
    private User userLow;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_high_id", nullable = false)
    private User userHigh;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    private Friendship(User userLow, User userHigh) {
        this.userLow = userLow;
        this.userHigh = userHigh;
    }

    /** 두 사용자로 친구 관계를 만든다. id 순서는 내부에서 정렬한다. */
    public static Friendship between(User a, User b) {
        if (a.getId() < b.getId()) {
            return new Friendship(a, b);
        }
        return new Friendship(b, a);
    }
}
