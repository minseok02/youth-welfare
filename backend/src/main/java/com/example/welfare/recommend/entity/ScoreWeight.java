package com.example.welfare.recommend.entity;

import com.example.welfare.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "score_weights")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class ScoreWeight extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String weightKey; // COLD_START / GROWTH / STABLE

    @Column(nullable = false)
    private BigDecimal ruleWeight;

    @Column(nullable = false)
    private BigDecimal aiWeight;

    @Column(nullable = false)
    private Integer minLogCount; // 이 단계 적용 최소 추천 이력 수

    @Column(nullable = false)
    @Builder.Default
    private boolean isActive = true;

    @Column(length = 200)
    private String description;
}
