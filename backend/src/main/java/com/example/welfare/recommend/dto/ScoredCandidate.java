package com.example.welfare.recommend.dto;

import com.example.welfare.policy.entity.WelfareService;
import lombok.Builder;
import lombok.Getter;

/**
 * 추천 파이프라인 내부 전달 객체
 * Rule 점수 → AI 점수 → 최종 점수 순으로 채워짐
 */
@Getter
@Builder(toBuilder = true)
public class ScoredCandidate {

    private final WelfareService service;
    private final RecommendationCandidateProjection projection;

    private final double ruleBaseScore;
    private final double ruleWeightedScore;

    private final Double aiScore;     // NULL 가능 (AI 미실행 또는 실패)

    private final String aiReason;

    private final double finalScore;

    private final boolean aiFallback; // true = AI 없이 rule만 사용

    private final boolean hasSpecialTargetMismatch; // true = 특수 대상 불일치 페널티 적용됨

    public ScoredCandidate withAiResult(Double updatedAiScore, String updatedAiReason) {
        return this.toBuilder()
                .aiScore(updatedAiScore)
                .aiReason(updatedAiReason)
                .build();
    }

    public ScoredCandidate withFinalScore(double updatedFinalScore, boolean updatedAiFallback) {
        return this.toBuilder()
                .finalScore(updatedFinalScore)
                .aiFallback(updatedAiFallback)
                .build();
    }
}
