package com.example.welfare.policy.entity;

import com.example.welfare.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "policy_link_review_records")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class PolicyLinkReviewRecord extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_id", nullable = false, unique = true)
    private WelfareService policy;

    @Column(columnDefinition = "TEXT")
    private String reviewNote;

    @Column(nullable = false)
    private String reviewedByUserKey;

    @Column(nullable = false)
    private LocalDateTime reviewedAt;

    public void markReviewed(String reviewNote, String reviewedByUserKey, LocalDateTime reviewedAt) {
        this.reviewNote = reviewNote;
        this.reviewedByUserKey = reviewedByUserKey;
        this.reviewedAt = reviewedAt;
    }
}
