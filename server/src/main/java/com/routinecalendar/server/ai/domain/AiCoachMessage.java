package com.routinecalendar.server.ai.domain;

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

/** AI 루틴 코치 대화 한 줄(사람이 보는 user/assistant 텍스트만). */
@Entity
@Table(name = "ai_coach_messages")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiCoachMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 16)
    private String role;     // "user" | "assistant"

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public AiCoachMessage(User user, String role, String content) {
        this.user = user;
        this.role = role;
        this.content = content;
    }
}
