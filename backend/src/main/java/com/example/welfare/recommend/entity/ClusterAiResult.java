package com.example.welfare.recommend.entity;

import com.example.welfare.policy.entity.WelfareService;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "cluster_ai_results",
        uniqueConstraints = @UniqueConstraint(name = "uq_car", columnNames = {"cluster_id", "service_id"}))
@Getter
@NoArgsConstructor
public class ClusterAiResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", columnDefinition = "BIGINT")
    private Long id;

    @Column(name = "cluster_id", nullable = false, length = 50)
    private String clusterId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id", nullable = false)
    private WelfareService service;

    @Column(name = "ai_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal aiScore;

    @Column(name = "ai_reason", length = 500)
    private String aiReason;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Builder
    public ClusterAiResult(String clusterId, WelfareService service,
                            BigDecimal aiScore, String aiReason) {
        this.clusterId = clusterId;
        this.service = service;
        this.aiScore = aiScore;
        this.aiReason = aiReason;
        this.createdAt = LocalDateTime.now();
    }

    public void update(BigDecimal aiScore, String aiReason) {
        this.aiScore = aiScore;
        this.aiReason = aiReason;
        this.createdAt = LocalDateTime.now();
    }
}
