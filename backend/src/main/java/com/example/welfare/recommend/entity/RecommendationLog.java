package com.example.welfare.recommend.entity;

import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "recommendation_logs",
        indexes = {
                @Index(name = "idx_rl_user", columnList = "user_id"),
                @Index(name = "idx_rl_service", columnList = "service_id")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class RecommendationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id", nullable = false)
    private WelfareService service;

    private BigDecimal finalScore;
    private BigDecimal ruleWeightUsed;
    private BigDecimal aiWeightUsed;

    @Column(nullable = false)
    @Builder.Default
    private boolean isFallback = false;   // AI 실패 → rule만 사용 fallback 여부

    @Column(nullable = false)
    @Builder.Default
    private boolean isClicked = false;

    @Column(nullable = false)
    @Builder.Default
    private LocalDateTime sentAt = LocalDateTime.now();

    private LocalDateTime clickedAt;

    public void click() {
        this.isClicked = true;
        this.clickedAt = LocalDateTime.now();
    }
}
