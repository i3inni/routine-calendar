package com.routinecalendar.server.user.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * 사용자. 신원은 Kakao 또는 Apple 로그인으로 잡고, 친구추가는 공개 {@code handle}로 검색한다.
 * (kakaoId / appleId 중 하나로 식별)
 */
@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // JPA 전용 기본 생성자
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 카카오가 발급하는 회원 번호 (애플 전용 유저면 null) */
    @Column(name = "kakao_id", unique = true)
    private Long kakaoId;

    /** 애플의 stable user id(sub) (카카오 전용 유저면 null) */
    @Column(name = "apple_id", unique = true, length = 255)
    private String appleId;

    /** 친구가 검색/추가에 쓰는 공개 ID */
    @Column(nullable = false, unique = true, length = 30)
    private String handle;

    @Column(nullable = false, length = 50)
    private String nickname;

    @Column(name = "profile_image_url", length = 500)
    private String profileImageUrl;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** 계정 삭제 예약 시각. null이면 정상 계정. (유예 기간 후 영구 삭제) */
    @Column(name = "deletion_requested_at")
    private Instant deletionRequestedAt;

    @Builder
    public User(Long kakaoId, String appleId, String handle, String nickname, String profileImageUrl) {
        this.kakaoId = kakaoId;
        this.appleId = appleId;
        this.handle = handle;
        this.nickname = nickname;
        this.profileImageUrl = profileImageUrl;
    }

    public void updateProfile(String nickname, String profileImageUrl) {
        this.nickname = nickname;
        this.profileImageUrl = profileImageUrl;
    }

    /** 친구에게 표시되는 이름(닉네임) 변경. */
    public void updateNickname(String nickname) {
        this.nickname = nickname;
    }

    /** 계정 삭제 예약(유예 시작). */
    public void requestDeletion() {
        this.deletionRequestedAt = Instant.now();
    }

    /** 삭제 예약 취소(재로그인 시). */
    public void cancelDeletion() {
        this.deletionRequestedAt = null;
    }
}
