package com.example.welfare.policy.entity;

import com.example.welfare.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "policy_error_reports",
        indexes = {
                @Index(name = "idx_per_policy_created", columnList = "policy_id, created_at"),
                @Index(name = "idx_per_status_created", columnList = "status, created_at"),
                @Index(name = "idx_per_user_key_created", columnList = "user_key, created_at")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class PolicyErrorReport extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "policy_id", nullable = false)
    private WelfareService policy;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "user_key", length = 32)
    private String userKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason_code", nullable = false, length = 40)
    private ReasonCode reasonCode;

    @Column(name = "note", length = 1000)
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private Status status = Status.OPEN;

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

    public enum ReasonCode {
        REGION_MISMATCH("지역 정보가 다릅니다"),
        PERIOD_MISMATCH("신청 기간이 다릅니다"),
        ELIGIBILITY_MISMATCH("자격조건 설명이 다릅니다"),
        BROKEN_LINK("링크나 원문이 열리지 않습니다"),
        DUPLICATE_POLICY("중복 정책 같습니다"),
        OTHER("기타");

        private final String label;

        ReasonCode(String label) {
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
