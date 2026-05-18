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
        String rerankTraceMode,
        List<ServiceDiagnostic> services
) {

    public record ServiceDiagnostic(
            Long serviceId,
            String title,
            String sourceType,
            String category,
            List<String> gov24UserTypeTokens,
            List<String> gov24BenefitTypeTokens,
            List<String> youthEmploymentRequirementCodes,
            List<String> youthEmploymentRequirementLabels,
            List<String> youthEducationRequirementCodes,
            List<String> youthEducationRequirementLabels,
            List<String> youthSpecialRequirementCodes,
            List<String> youthSpecialRequirementLabels,
            String youthMaritalStatusCode,
            String youthMaritalStatusLabel,
            String youthIncomeConditionTypeCode,
            String youthIncomeConditionTypeLabel,
            boolean inBaseRetrieval,
            boolean inLatestRetrieval,
            boolean passedBaseFilters,
            boolean passedLatestFilters,
            boolean inMergedCandidates,
            boolean inPostScoringCandidates,
            boolean inLatestSavedBatch,
            String dropStage,
            Boolean youthRelevant,
            Boolean primaryAudienceRelevant,
            Boolean ageConstraintMatched,
            Double ruleBaseScore,
            Double ruleWeightedScore,
            Double aiScore,
            Double latestSavedAiScore,
            String latestSavedAiStatus,
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
