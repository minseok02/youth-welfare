package com.example.welfare.recommend.entity;

import com.example.welfare.global.entity.BaseTimeEntity;
import com.example.welfare.policy.entity.WelfareService;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_recommendations",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_ur_user_key_service_time",
                        columnNames = {"user_key", "service_id", "recommended_at"})
        },
        indexes = {
                @Index(name = "idx_ur_user_key_score", columnList = "user_key, final_score DESC"),
                @Index(name = "idx_ur_recommended", columnList = "recommended_at"),
                @Index(name = "idx_ur_user_key_bookmark", columnList = "user_key, is_bookmarked")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class UserRecommendation extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_key", nullable = false, length = 32)
    private String userKey;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id", nullable = false)
    private WelfareService service;

    @Column(nullable = false)
    @Builder.Default
    private LocalDateTime recommendedAt = LocalDateTime.now();

    private BigDecimal ruleBaseScore;
    private BigDecimal ruleWeightedScore;

    // ai_score NULL 가능: NULL이면 final_score = normalize(ruleWeightedScore)
    private BigDecimal aiScore;

    @Column(length = 500)
    private String aiReason;

    private BigDecimal ruleWeightUsed;
    private BigDecimal aiWeightUsed;

    @Column(nullable = false)
    @Builder.Default
    private BigDecimal finalScore = BigDecimal.ZERO;

    @Column(nullable = false)
    @Builder.Default
    private boolean isBookmarked = false;

    public void updateAiScore(BigDecimal aiScore, String aiReason) {
        this.aiScore = aiScore;
        this.aiReason = aiReason;
    }

    public void updateFinalScore(BigDecimal finalScore, BigDecimal ruleWeight, BigDecimal aiWeight) {
        this.finalScore = finalScore;
        this.ruleWeightUsed = ruleWeight;
        this.aiWeightUsed = aiWeight;
    }

    public void toggleBookmark() {
        this.isBookmarked = !this.isBookmarked;
    }
}
