package com.example.welfare.policy.entity;

import com.example.welfare.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "policy_duplicate_review_records")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class PolicyDuplicateReviewRecord extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WelfareService.SourceType sourceType;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    @Builder.Default
    private String hostOrgKey = "";

    private String hostOrgLabel;

    @Column(columnDefinition = "TEXT")
    private String reviewNote;

    @Column(nullable = false)
    private String reviewedByUserKey;

    @Column(nullable = false)
    private LocalDateTime reviewedAt;

    public void markReviewed(String hostOrgLabel,
                             String reviewNote,
                             String reviewedByUserKey,
                             LocalDateTime reviewedAt) {
        this.hostOrgLabel = hostOrgLabel;
        this.reviewNote = reviewNote;
        this.reviewedByUserKey = reviewedByUserKey;
        this.reviewedAt = reviewedAt;
    }
}
