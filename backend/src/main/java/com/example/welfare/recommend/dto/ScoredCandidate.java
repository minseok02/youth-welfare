package com.example.welfare.recommend.dto;

import com.example.welfare.policy.entity.WelfareService;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 추천 파이프라인 내부 전달 객체
 * Rule 점수 → AI 점수 → 최종 점수 순으로 채워짐
 */
@Getter
@Builder
public class ScoredCandidate {

    private WelfareService service;

    private double ruleBaseScore;
    private double ruleWeightedScore;

    @Setter
    private Double aiScore;     // NULL 가능 (AI 미실행 또는 실패)

    @Setter
    private String aiReason;

    @Setter
    private double finalScore;

    @Setter
    private boolean aiFallback; // true = AI 없이 rule만 사용

    @Setter
    private boolean hasSpecialTargetMismatch; // true = 특수 대상 불일치 페널티 적용됨
}
