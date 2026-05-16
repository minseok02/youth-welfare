package com.example.welfare.recommend.dto;

import com.example.welfare.recommend.entity.AiScoreStatus;
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

    @Builder.Default
    private final AiScoreStatus aiStatus = AiScoreStatus.NOT_REQUESTED;

    private final String aiReason;

    private final double finalScore;

    private final boolean aiFallback; // true = AI 없이 rule만 사용

    private final boolean hasInterestMismatch; // true = 관심분야 불일치 페널티 적용됨

    private final boolean hasPriorityMismatch; // true = 우선순위 버킷 불일치 페널티 적용됨

    private final Integer matchedPriorityRank; // null = 카테고리 우선순위 매칭 없음

    private final boolean hasSpecialTargetMismatch; // true = 특수 대상 불일치 페널티 적용됨

    public ScoredCandidate withAiResult(Double updatedAiScore, String updatedAiReason, AiScoreStatus updatedAiStatus) {
        return this.toBuilder()
                .aiScore(updatedAiScore)
                .aiStatus(updatedAiStatus)
                .aiReason(updatedAiReason)
                .build();
    }

    public ScoredCandidate withAiStatus(AiScoreStatus updatedAiStatus) {
        return this.toBuilder()
                .aiStatus(updatedAiStatus)
                .build();
    }

    public ScoredCandidate withFinalScore(double updatedFinalScore, boolean updatedAiFallback) {
        return this.toBuilder()
                .finalScore(updatedFinalScore)
                .aiFallback(updatedAiFallback)
                .build();
    }
}
