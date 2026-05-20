package com.example.welfare.admin.dashboard.service;

import com.example.welfare.admin.dashboard.dto.AdminRecommendationBreakdownResponse;
import com.example.welfare.admin.dashboard.repository.AdminDashboardReadRows;
import com.example.welfare.admin.dashboard.repository.AdminDashboardRecommendationReadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminDashboardRecommendationService {

    private final AdminDashboardRecommendationReadRepository adminDashboardRecommendationReadRepository;
    private static final List<String> YOUTH_OFFICIAL_FACET_ORDER = List.of(
            "YOUTH_INCOME_CONDITION_TYPE",
            "YOUTH_EMPLOYMENT_REQUIREMENT",
            "YOUTH_EDUCATION_REQUIREMENT",
            "YOUTH_SPECIAL_REQUIREMENT",
            "YOUTH_MARITAL_STATUS"
    );
    private static final List<String> GOV24_FACET_ORDER = List.of(
            "GOV24_SERVICE_FIELD",
            "GOV24_USER_TYPE_TOKEN",
            "GOV24_BENEFIT_TYPE_TOKEN"
    );
    private static final Map<String, String> YOUTH_OFFICIAL_FACET_LABELS = Map.of(
            "YOUTH_INCOME_CONDITION_TYPE", "소득조건 유형",
            "YOUTH_EMPLOYMENT_REQUIREMENT", "취업 요건",
            "YOUTH_EDUCATION_REQUIREMENT", "학력 요건",
            "YOUTH_SPECIAL_REQUIREMENT", "특화 요건",
            "YOUTH_MARITAL_STATUS", "결혼 상태"
    );
    private static final Map<String, String> GOV24_FACET_LABELS = Map.of(
            "GOV24_SERVICE_FIELD", "Gov24 서비스분야",
            "GOV24_USER_TYPE_TOKEN", "Gov24 사용자구분",
            "GOV24_BENEFIT_TYPE_TOKEN", "Gov24 지원유형"
    );

    public AdminRecommendationBreakdownResponse getRecommendationBreakdowns(
            Integer requestedSummaryWindowDays,
            Integer requestedLimit
    ) {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        java.time.LocalDateTime dayAgo = now.minusDays(1);
        int summaryWindowDays = AdminDashboardQueryPolicy.resolveSummaryWindowDays(requestedSummaryWindowDays);
        int breakdownLimit = AdminDashboardQueryPolicy.resolveRecommendationBreakdownLimit(requestedLimit);
        java.time.LocalDateTime summaryWindowAgo = now.minusDays(summaryWindowDays);

        AdminDashboardReadRows.RecommendationSummaryRow recommendationSummary =
                adminDashboardRecommendationReadRepository.fetchRecommendationSummary(dayAgo, summaryWindowAgo);
        AdminDashboardReadRows.RecommendationTrafficMixRow recommendationTrafficMix =
                adminDashboardRecommendationReadRepository.fetchRecommendationTrafficMix(summaryWindowAgo);
        AdminDashboardReadRows.RecommendationConcentrationRow recommendationConcentration =
                adminDashboardRecommendationReadRepository.fetchRecommendationConcentration();
        AdminDashboardReadRows.RecommendationRecentWindowRow recentWindowReviewSnapshot =
                adminDashboardRecommendationReadRepository.fetchRecommendationRecentWindowSnapshot(
                        AdminDashboardQueryPolicy.RECENT_REVIEW_WINDOW_HOURS,
                        AdminDashboardQueryPolicy.HISTORICAL_TARGET_TOP1_SERVICE_ID
                );
        AdminDashboardReadRows.RecommendationReviewGateStalenessRow reviewGateStalenessSnapshot =
                adminDashboardRecommendationReadRepository.fetchRecommendationReviewGateStalenessSnapshot(
                        AdminDashboardQueryPolicy.RECENT_REVIEW_WINDOW_HOURS,
                        AdminDashboardQueryPolicy.HISTORICAL_TARGET_TOP1_SERVICE_ID
                );
        String realUserTrafficGate =
                AdminDashboardQueryPolicy.resolveRealUserTrafficGate(recommendationSummary, recommendationTrafficMix);
        String recommendationReviewGate =
                AdminDashboardQueryPolicy.resolveRecommendationReviewGate(realUserTrafficGate, recommendationConcentration);
        String recentWindowRecommendationReviewReading =
                AdminDashboardQueryPolicy.resolveRecentWindowRecommendationReviewReading(recentWindowReviewSnapshot);
        boolean historicalExampleDominanceDetected =
                AdminDashboardQueryPolicy.resolveHistoricalExampleDominanceDetected(
                        recommendationReviewGate,
                        recentWindowRecommendationReviewReading
                );
        String reviewGatePolicyCandidateStatus =
                AdminDashboardQueryPolicy.resolveReviewGatePolicyCandidateStatus(
                        recommendationReviewGate,
                        recentWindowRecommendationReviewReading,
                        historicalExampleDominanceDetected,
                        reviewGateStalenessSnapshot
                );
        String reviewGatePolicyCandidateReason =
                AdminDashboardQueryPolicy.resolveReviewGatePolicyCandidateReason(
                        recommendationReviewGate,
                        recentWindowRecommendationReviewReading,
                        historicalExampleDominanceDetected,
                        reviewGateStalenessSnapshot
                );
        String reviewGatePolicyPromotionStatus =
                AdminDashboardQueryPolicy.resolveReviewGatePolicyPromotionStatus(
                        reviewGatePolicyCandidateStatus,
                        reviewGateStalenessSnapshot
                );
        String reviewGatePolicyPromotionReason =
                AdminDashboardQueryPolicy.resolveReviewGatePolicyPromotionReason(
                        reviewGatePolicyCandidateStatus,
                        reviewGateStalenessSnapshot
                );
        String reviewGatePolicyPromotionActionStatus =
                AdminDashboardQueryPolicy.resolveReviewGatePolicyPromotionActionStatus(
                        reviewGatePolicyPromotionStatus
                );
        String reviewGatePolicyPromotionActionReason =
                AdminDashboardQueryPolicy.resolveReviewGatePolicyPromotionActionReason(
                        reviewGatePolicyPromotionStatus
                );
        String reviewGatePolicyPromotionReadinessStatus =
                AdminDashboardQueryPolicy.resolveReviewGatePolicyPromotionReadinessStatus(
                        realUserTrafficGate,
                        recommendationConcentration,
                        reviewGatePolicyCandidateStatus,
                        reviewGatePolicyPromotionStatus
                );
        String reviewGatePolicyPromotionReadinessReason =
                AdminDashboardQueryPolicy.resolveReviewGatePolicyPromotionReadinessReason(
                        realUserTrafficGate,
                        recommendationConcentration,
                        reviewGatePolicyCandidateStatus,
                        reviewGatePolicyPromotionStatus
                );
        String reviewGatePolicyPromotionExecutionStatus =
                AdminDashboardQueryPolicy.resolveReviewGatePolicyPromotionExecutionStatus(
                        reviewGatePolicyPromotionReadinessStatus,
                        reviewGatePolicyPromotionStatus
                );
        String reviewGatePolicyPromotionExecutionReason =
                AdminDashboardQueryPolicy.resolveReviewGatePolicyPromotionExecutionReason(
                        reviewGatePolicyPromotionReadinessStatus,
                        reviewGatePolicyPromotionReadinessReason,
                        reviewGatePolicyPromotionStatus
                );
        String reviewGatePolicyPromotionApprovalCriteriaStatus =
                AdminDashboardQueryPolicy.resolveReviewGatePolicyPromotionApprovalCriteriaStatus(
                        reviewGatePolicyPromotionReadinessStatus,
                        reviewGatePolicyCandidateStatus,
                        reviewGatePolicyPromotionStatus,
                        reviewGatePolicyPromotionExecutionStatus
                );
        String reviewGatePolicyPromotionApprovalCriteriaReason =
                AdminDashboardQueryPolicy.resolveReviewGatePolicyPromotionApprovalCriteriaReason(
                        reviewGatePolicyPromotionReadinessStatus,
                        reviewGatePolicyPromotionReadinessReason,
                        reviewGatePolicyCandidateStatus,
                        reviewGatePolicyPromotionStatus,
                        reviewGatePolicyPromotionExecutionStatus
                );
        String reviewGatePolicyPromotionApprovalStatus =
                AdminDashboardQueryPolicy.resolveReviewGatePolicyPromotionApprovalStatus(
                        reviewGatePolicyPromotionExecutionStatus
                );
        String reviewGatePolicyPromotionApprovalReason =
                AdminDashboardQueryPolicy.resolveReviewGatePolicyPromotionApprovalReason(
                        reviewGatePolicyPromotionExecutionStatus,
                        reviewGatePolicyPromotionExecutionReason
                );
        String reviewGatePolicyPromotionApprovalDecisionStatus =
                AdminDashboardQueryPolicy.resolveReviewGatePolicyPromotionApprovalDecisionStatus(
                        reviewGatePolicyPromotionApprovalCriteriaStatus,
                        reviewGatePolicyPromotionApprovalStatus
                );
        String reviewGatePolicyPromotionApprovalDecisionReason =
                AdminDashboardQueryPolicy.resolveReviewGatePolicyPromotionApprovalDecisionReason(
                        reviewGatePolicyPromotionApprovalCriteriaStatus,
                        reviewGatePolicyPromotionApprovalCriteriaReason,
                        reviewGatePolicyPromotionApprovalStatus,
                        reviewGatePolicyPromotionApprovalReason
                );
        String reviewGatePolicyPromotionApprovalRecordStatus =
                AdminDashboardQueryPolicy.resolveReviewGatePolicyPromotionApprovalRecordStatus(
                        reviewGatePolicyPromotionApprovalDecisionStatus
                );
        String reviewGatePolicyPromotionApprovalRecordReason =
                AdminDashboardQueryPolicy.resolveReviewGatePolicyPromotionApprovalRecordReason(
                        reviewGatePolicyPromotionApprovalDecisionStatus,
                        reviewGatePolicyPromotionApprovalDecisionReason
                );
        List<AdminRecommendationBreakdownResponse.FacetGroup> youthOfficialFacetGroups = buildFacetGroups(
                adminDashboardRecommendationReadRepository.fetchLatestBatchYouthOfficialFacetRows(breakdownLimit),
                YOUTH_OFFICIAL_FACET_ORDER,
                YOUTH_OFFICIAL_FACET_LABELS
        );
        List<AdminRecommendationBreakdownResponse.FacetGroup> gov24FacetGroups = buildFacetGroups(
                adminDashboardRecommendationReadRepository.fetchLatestBatchGov24FacetRows(breakdownLimit),
                GOV24_FACET_ORDER,
                GOV24_FACET_LABELS
        );

        return new AdminRecommendationBreakdownResponse(
                now,
                summaryWindowDays,
                recommendationSummary.sentInWindow(),
                recommendationSummary.clickedInWindow(),
                recommendationSummary.fallbackInWindow(),
                new AdminRecommendationBreakdownResponse.RecommendationTrafficMixSnapshot(
                        recommendationTrafficMix.exampleLogsInWindow(),
                        recommendationTrafficMix.boundedLocalLogsInWindow(),
                        recommendationTrafficMix.localRealNonExampleSeedLogsInWindow(),
                        recommendationTrafficMix.realUserLogsInWindow(),
                        recommendationTrafficMix.realNonExampleLogsInWindow(),
                        recommendationTrafficMix.exampleUsersInWindow(),
                        recommendationTrafficMix.boundedLocalUsersInWindow(),
                        recommendationTrafficMix.localRealNonExampleSeedUsersInWindow(),
                        recommendationTrafficMix.realUserUsersInWindow(),
                        recommendationTrafficMix.realNonExampleUsersInWindow(),
                        recommendationTrafficMix.exampleClickedUsersInWindow(),
                        recommendationTrafficMix.boundedLocalClickedUsersInWindow(),
                        recommendationTrafficMix.localRealNonExampleSeedClickedUsersInWindow(),
                        recommendationTrafficMix.realUserClickedUsersInWindow(),
                        recommendationTrafficMix.realNonExampleClickedUsersInWindow()
                ),
                realUserTrafficGate,
                new AdminRecommendationBreakdownResponse.RecommendationConcentrationSnapshot(
                        recommendationConcentration.latestBatchRows(),
                        recommendationConcentration.latestBatchUsers(),
                        recommendationConcentration.latestBatchDistinctServices(),
                        recommendationConcentration.top1LeaderServiceId(),
                        recommendationConcentration.top1LeaderTitle(),
                        recommendationConcentration.top1LeaderSource(),
                        recommendationConcentration.top1LeaderCategory(),
                        recommendationConcentration.top1LeaderUsers(),
                        recommendationConcentration.top1LeaderSharePct(),
                        new AdminRecommendationBreakdownResponse.ServiceCohortMixSnapshot(
                                recommendationConcentration.top1LeaderExampleUsers(),
                                recommendationConcentration.top1LeaderBoundedLocalUsers(),
                                recommendationConcentration.top1LeaderLocalRealNonExampleSeedUsers(),
                                recommendationConcentration.top1LeaderRealUserUsers(),
                                recommendationConcentration.top1LeaderRealNonExampleUsers()
                        ),
                        AdminDashboardQueryPolicy.resolveTop1LeaderSignalSummary(recommendationConcentration),
                        recommendationConcentration.concentrationReadiness(),
                        recommendationConcentration.realUserCohortGate(),
                        recommendationConcentration.signalQuality()
                ),
                recommendationReviewGate,
                new AdminRecommendationBreakdownResponse.RecommendationRecentWindowSnapshot(
                        recentWindowReviewSnapshot.recentWindowHours(),
                        recentWindowReviewSnapshot.targetServiceId(),
                        recentWindowReviewSnapshot.recentLatestBatchUsers(),
                        recentWindowReviewSnapshot.recentExampleUsers(),
                        recentWindowReviewSnapshot.recentRealUserUsers(),
                        recentWindowReviewSnapshot.recentLocalRealNonExampleSeedUsers(),
                        recentWindowReviewSnapshot.recentTop1LeaderServiceId(),
                        recentWindowReviewSnapshot.recentTop1LeaderTitle(),
                        recentWindowReviewSnapshot.recentTop1LeaderUsers(),
                        recentWindowReviewSnapshot.recentTop1LeaderRealUserUsers(),
                        recentWindowReviewSnapshot.recentTop1LeaderSharePct(),
                        recentWindowReviewSnapshot.recentTargetTop1Users(),
                        recentWindowReviewSnapshot.recentTargetTop1RealUserUsers()
                ),
                new AdminRecommendationBreakdownResponse.RecommendationReviewGateStalenessSnapshot(
                        reviewGateStalenessSnapshot.targetServiceId(),
                        reviewGateStalenessSnapshot.primaryReferenceMode(),
                        reviewGateStalenessSnapshot.recentWindowHours(),
                        reviewGateStalenessSnapshot.exampleLatestUsers(),
                        reviewGateStalenessSnapshot.exampleLatestUsersLast24h(),
                        reviewGateStalenessSnapshot.exampleTargetTop1Users(),
                        reviewGateStalenessSnapshot.exampleTargetTop1Last24h(),
                        reviewGateStalenessSnapshot.exampleTargetOldestTop1At(),
                        reviewGateStalenessSnapshot.exampleTargetNewestTop1At(),
                        reviewGateStalenessSnapshot.realUserLatestUsers(),
                        reviewGateStalenessSnapshot.realUserLatestUsersLast24h(),
                        reviewGateStalenessSnapshot.realUserTargetTop1Users(),
                        reviewGateStalenessSnapshot.realUserTargetTop1Last24h()
                ),
                recentWindowRecommendationReviewReading,
                historicalExampleDominanceDetected,
                reviewGatePolicyCandidateStatus,
                reviewGatePolicyCandidateReason,
                reviewGatePolicyPromotionStatus,
                reviewGatePolicyPromotionReason,
                reviewGatePolicyPromotionActionStatus,
                reviewGatePolicyPromotionActionReason,
                reviewGatePolicyPromotionReadinessStatus,
                reviewGatePolicyPromotionReadinessReason,
                reviewGatePolicyPromotionExecutionStatus,
                reviewGatePolicyPromotionExecutionReason,
                reviewGatePolicyPromotionApprovalCriteriaStatus,
                reviewGatePolicyPromotionApprovalCriteriaReason,
                reviewGatePolicyPromotionApprovalStatus,
                reviewGatePolicyPromotionApprovalReason,
                reviewGatePolicyPromotionApprovalDecisionStatus,
                reviewGatePolicyPromotionApprovalDecisionReason,
                reviewGatePolicyPromotionApprovalRecordStatus,
                reviewGatePolicyPromotionApprovalRecordReason,
                adminDashboardRecommendationReadRepository.fetchTopRepeatedRecommendationServices(breakdownLimit).stream()
                        .map(row -> new AdminRecommendationBreakdownResponse.RepeatedServiceSnapshot(
                                row.serviceId(),
                                row.title(),
                                row.sourceType(),
                                row.category(),
                                row.rowCount(),
                                row.distinctUsers(),
                                new AdminRecommendationBreakdownResponse.ServiceCohortMixSnapshot(
                                        row.exampleUsers(),
                                        row.boundedLocalUsers(),
                                        row.localRealNonExampleSeedUsers(),
                                        row.realUserUsers(),
                                        row.realNonExampleUsers()
                                )
                        ))
                        .toList(),
                adminDashboardRecommendationReadRepository.fetchTop1RecommendationServices(breakdownLimit).stream()
                        .map(row -> new AdminRecommendationBreakdownResponse.Top1ServiceSnapshot(
                                row.serviceId(),
                                row.title(),
                                row.sourceType(),
                                row.category(),
                                row.usersAsTop1(),
                                new AdminRecommendationBreakdownResponse.ServiceCohortMixSnapshot(
                                        row.exampleUsers(),
                                        row.boundedLocalUsers(),
                                        row.localRealNonExampleSeedUsers(),
                                        row.realUserUsers(),
                                        row.realNonExampleUsers()
                                )
                        ))
                        .toList(),
                adminDashboardRecommendationReadRepository.fetchRecommendationSourceBreakdowns(summaryWindowAgo, breakdownLimit).stream()
                        .map(row -> new AdminRecommendationBreakdownResponse.SourceBreakdown(
                                row.sourceType(),
                                row.sentCount(),
                                row.clickedCount(),
                                row.fallbackCount(),
                                AdminDashboardQueryPolicy.ratio(row.clickedCount(), row.sentCount()),
                                AdminDashboardQueryPolicy.ratio(row.fallbackCount(), row.sentCount())
                        ))
                        .toList(),
                adminDashboardRecommendationReadRepository.fetchRecommendationCategoryBreakdowns(summaryWindowAgo, breakdownLimit).stream()
                        .map(row -> new AdminRecommendationBreakdownResponse.CategoryBreakdown(
                                row.category(),
                                row.sentCount(),
                                row.clickedCount(),
                                row.fallbackCount(),
                                AdminDashboardQueryPolicy.ratio(row.clickedCount(), row.sentCount()),
                                AdminDashboardQueryPolicy.ratio(row.fallbackCount(), row.sentCount())
                        ))
                        .toList(),
                adminDashboardRecommendationReadRepository.fetchRecommendationWeightBreakdowns(summaryWindowAgo, breakdownLimit).stream()
                        .map(row -> new AdminRecommendationBreakdownResponse.WeightBreakdown(
                                row.weightKey(),
                                row.ruleWeight(),
                                row.aiWeight(),
                                row.sentCount(),
                                row.clickedCount(),
                                row.fallbackCount(),
                                AdminDashboardQueryPolicy.ratio(row.clickedCount(), row.sentCount()),
                                AdminDashboardQueryPolicy.ratio(row.fallbackCount(), row.sentCount())
                        ))
                        .toList(),
                youthOfficialFacetGroups,
                gov24FacetGroups,
                adminDashboardRecommendationReadRepository.fetchRecentFallbackRecommendationSamples(summaryWindowAgo, breakdownLimit).stream()
                        .map(row -> new AdminRecommendationBreakdownResponse.RecommendationSample(
                                row.logId(),
                                row.serviceId(),
                                row.title(),
                                row.sourceType(),
                                row.category(),
                                row.userCohort(),
                                row.finalScore(),
                                row.fallback(),
                                row.clicked(),
                                row.sentAt(),
                                row.clickedAt()
                        ))
                        .toList(),
                adminDashboardRecommendationReadRepository.fetchRecentClickedRecommendationSamples(summaryWindowAgo, breakdownLimit).stream()
                        .map(row -> new AdminRecommendationBreakdownResponse.RecommendationSample(
                                row.logId(),
                                row.serviceId(),
                                row.title(),
                                row.sourceType(),
                                row.category(),
                                row.userCohort(),
                                row.finalScore(),
                                row.fallback(),
                                row.clicked(),
                                row.sentAt(),
                                row.clickedAt()
                        ))
                        .toList(),
                adminDashboardRecommendationReadRepository.fetchRecommendationRepeatExposureGroups(summaryWindowAgo, breakdownLimit).stream()
                        .map(row -> new AdminRecommendationBreakdownResponse.RepeatExposureGroup(
                                row.userKey(),
                                row.serviceId(),
                                row.title(),
                                row.sourceType(),
                                row.category(),
                                row.userCohort(),
                                row.exposureCount(),
                                row.clickedCount(),
                                row.fallbackCount(),
                                row.firstSentAt(),
                                row.latestSentAt(),
                                row.latestClickedAt()
                        ))
                        .toList()
        );
    }

    private List<AdminRecommendationBreakdownResponse.FacetGroup> buildFacetGroups(
            List<AdminDashboardReadRows.RecommendationFacetRow> rows,
            List<String> orderedKeys,
            Map<String, String> labelMap
    ) {
        Map<String, List<AdminRecommendationBreakdownResponse.FacetBucket>> grouped = new LinkedHashMap<>();
        for (String orderedKey : orderedKeys) {
            grouped.put(orderedKey, new java.util.ArrayList<>());
        }
        for (AdminDashboardReadRows.RecommendationFacetRow row : rows) {
            grouped.computeIfAbsent(row.facetKey(), ignored -> new java.util.ArrayList<>())
                    .add(new AdminRecommendationBreakdownResponse.FacetBucket(
                            row.bucketLabel(),
                            row.rowCount(),
                            row.distinctServices()
                    ));
        }
        return grouped.entrySet().stream()
                .filter(entry -> !entry.getValue().isEmpty())
                .map(entry -> new AdminRecommendationBreakdownResponse.FacetGroup(
                        entry.getKey(),
                        labelMap.getOrDefault(entry.getKey(), entry.getKey()),
                        List.copyOf(entry.getValue())
                ))
                .toList();
    }
}
