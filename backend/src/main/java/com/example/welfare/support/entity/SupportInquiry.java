package com.example.welfare.support.entity;

import com.example.welfare.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "support_inquiries")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SupportInquiry extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "user_key", length = 100)
    private String userKey;

    @Column(name = "contact_email", nullable = false, length = 320)
    private String contactEmail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private Category category;

    @Column(nullable = false, length = 2000)
    private String message;

    @Column(name = "route_path", length = 255)
    private String routePath;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Status status;

    @Column(name = "review_note", length = 1000)
    private String reviewNote;

    @Column(name = "reviewed_by_user_key", length = 100)
    private String reviewedByUserKey;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    public void markReviewed(String reviewNote, String reviewedByUserKey, LocalDateTime reviewedAt) {
        this.status = Status.REVIEWED;
        this.reviewNote = reviewNote;
        this.reviewedByUserKey = reviewedByUserKey;
        this.reviewedAt = reviewedAt;
    }

    public enum Category {
        BUG_ERROR("오류/버그"),
        USAGE_QUESTION("사용법 질문"),
        IMPROVEMENT_SUGGESTION("개선 제안"),
        ACCOUNT_ISSUE("계정 문제"),
        GENERAL_FEEDBACK("일반 의견"),
        ETC("기타");

        private final String label;

        Category(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    public enum Status {
        OPEN,
        REVIEWED
    }
}
