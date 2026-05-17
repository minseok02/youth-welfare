package com.example.welfare.admin.dashboard.dto;

import java.time.LocalDateTime;
import java.util.List;

public record AdminRecommendationCandidateDiagnosticResponse(
        LocalDateTime generatedAt,
        String userKey,
        String accountOrigin,
        String clusterId,
        int baseCandidateCount,
        int latestCandidateCount,
        int filteredBaseCandidateCount,
        int filteredLatestCandidateCount,
        int mergedCandidateCount,
        int scoredCandidateCount,
        int postScoringCandidateCount,
        int latestSavedCandidateCount,
        List<ServiceDiagnostic> services
) {

    public record ServiceDiagnostic(
            Long serviceId,
            String title,
            String sourceType,
            String category,
            boolean inBaseRetrieval,
            boolean inLatestRetrieval,
            boolean passedBaseFilters,
            boolean passedLatestFilters,
            boolean inMergedCandidates,
            boolean inPostScoringCandidates,
            boolean inLatestSavedBatch,
            String dropStage,
            Boolean youthRelevant,
            Double ruleBaseScore,
            Double ruleWeightedScore,
            Double aiScore,
            Double latestSavedFinalScore,
            Integer latestSavedRank,
            Double rerankNormRule,
            Double rerankNormAi,
            Double rerankBaseBlendScore,
            Double rerankPriorityMultiplier,
            Double rerankScoreAfterPriorityMultiplier,
            String rerankDiversityBucket,
            Double rerankDiversityPenalty,
            boolean rerankNoPriorityTopBandEligible,
            Double rerankNoPriorityAdjustment,
            Double rerankCurrentFinalScore,
            Integer rerankCurrentRank,
            boolean hasInterestMismatch,
            boolean hasPriorityMismatch,
            boolean hasSpecialTargetMismatch
    ) {
    }
}
