package com.example.welfare.admin.dashboard.service;

import com.example.welfare.admin.dashboard.dto.AdminDashboardResponse;
import com.example.welfare.admin.dashboard.repository.AdminDashboardCollectReadRepository;
import com.example.welfare.admin.dashboard.repository.AdminDashboardNotificationReadRepository;
import com.example.welfare.admin.dashboard.repository.AdminDashboardReadRows;
import com.example.welfare.admin.dashboard.repository.AdminDashboardRecommendationReadRepository;
import com.example.welfare.admin.dashboard.repository.AdminDashboardSearchReadRepository;
import com.example.welfare.admin.dashboard.repository.AdminPolicyDuplicateGroupReadRepository;
import com.example.welfare.admin.dashboard.repository.AdminPolicyLinkReviewReadRepository;
import com.example.welfare.recommend.entity.ScoreWeight;
import com.example.welfare.recommend.service.ScoreWeightService;
import com.example.welfare.user.dto.response.UserPiiSyncStatusResponse;
import com.example.welfare.user.service.UserPiiSyncStatusService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminDashboardSummaryService {

    private final AdminDashboardCollectReadRepository adminDashboardCollectReadRepository;
    private final AdminDashboardRecommendationReadRepository adminDashboardRecommendationReadRepository;
    private final AdminDashboardNotificationReadRepository adminDashboardNotificationReadRepository;
    private final AdminDashboardSearchReadRepository adminDashboardSearchReadRepository;
    private final AdminPolicyDuplicateGroupReadRepository adminPolicyDuplicateGroupReadRepository;
    private final AdminPolicyLinkReviewReadRepository adminPolicyLinkReviewReadRepository;
    private final UserPiiSyncStatusService userPiiSyncStatusService;
    private final ScoreWeightService scoreWeightService;

    public AdminDashboardResponse getSummary(Integer requestedSummaryWindowDays, List<Integer> requestedTrendWindows) {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        java.time.LocalDateTime dayAgo = now.minusDays(1);
        int summaryWindowDays = AdminDashboardQueryPolicy.resolveSummaryWindowDays(requestedSummaryWindowDays);
        java.time.LocalDateTime summaryWindowAgo = now.minusDays(summaryWindowDays);
        List<Integer> trendWindows = AdminDashboardQueryPolicy.resolveTrendWindows(requestedTrendWindows);

        AdminDashboardReadRows.CollectSummaryRow collectSummary =
                adminDashboardCollectReadRepository.fetchCollectSummary(dayAgo);
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
        boolean reviewGatePromotionApprovalRecorded = adminDashboardRecommendationReadRepository
                .fetchRecommendationReviewGatePromotionApprovalRecord(
                        AdminDashboardQueryPolicy.REVIEW_GATE_PROMOTION_APPROVAL_KEY
                )
                .map(AdminDashboardQueryPolicy::isReviewGatePromotionApprovalRecorded)
                .orElse(false);
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
                        reviewGatePolicyPromotionStatus,
                        reviewGatePromotionApprovalRecorded
                );
        String reviewGatePolicyPromotionActionReason =
                AdminDashboardQueryPolicy.resolveReviewGatePolicyPromotionActionReason(
                        reviewGatePolicyPromotionStatus,
                        reviewGatePromotionApprovalRecorded
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
                        reviewGatePolicyPromotionStatus,
                        reviewGatePromotionApprovalRecorded
                );
        String reviewGatePolicyPromotionExecutionReason =
                AdminDashboardQueryPolicy.resolveReviewGatePolicyPromotionExecutionReason(
                        reviewGatePolicyPromotionReadinessStatus,
                        reviewGatePolicyPromotionReadinessReason,
                        reviewGatePolicyPromotionStatus,
                        reviewGatePromotionApprovalRecorded
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
                        reviewGatePolicyPromotionExecutionStatus,
                        reviewGatePromotionApprovalRecorded
                );
        String reviewGatePolicyPromotionApprovalReason =
                AdminDashboardQueryPolicy.resolveReviewGatePolicyPromotionApprovalReason(
                        reviewGatePolicyPromotionExecutionStatus,
                        reviewGatePolicyPromotionExecutionReason,
                        reviewGatePromotionApprovalRecorded
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
        String reviewGatePolicyPromotionReviewRunStatus =
                AdminDashboardQueryPolicy.resolveReviewGatePolicyPromotionReviewRunStatus(
                        reviewGatePolicyPromotionApprovalRecordStatus
                );
        String reviewGatePolicyPromotionReviewRunReason =
                AdminDashboardQueryPolicy.resolveReviewGatePolicyPromotionReviewRunReason(
                        reviewGatePolicyPromotionApprovalRecordStatus,
                        reviewGatePolicyPromotionApprovalRecordReason
                );
        String reviewGatePolicyPromotionReviewRunCriteriaStatus =
                AdminDashboardQueryPolicy.resolveReviewGatePolicyPromotionReviewRunCriteriaStatus(
                        reviewGatePolicyPromotionApprovalCriteriaStatus,
                        reviewGatePolicyPromotionApprovalRecordStatus
                );
        String reviewGatePolicyPromotionReviewRunCriteriaReason =
                AdminDashboardQueryPolicy.resolveReviewGatePolicyPromotionReviewRunCriteriaReason(
                        reviewGatePolicyPromotionApprovalCriteriaStatus,
                        reviewGatePolicyPromotionApprovalCriteriaReason,
                        reviewGatePolicyPromotionApprovalRecordStatus,
                        reviewGatePolicyPromotionApprovalRecordReason
                );
        String reviewGatePolicyPromotionReviewRunDecisionStatus =
                AdminDashboardQueryPolicy.resolveReviewGatePolicyPromotionReviewRunDecisionStatus(
                        reviewGatePolicyPromotionReviewRunCriteriaStatus,
                        reviewGatePolicyPromotionReviewRunStatus
                );
        String reviewGatePolicyPromotionReviewRunDecisionReason =
                AdminDashboardQueryPolicy.resolveReviewGatePolicyPromotionReviewRunDecisionReason(
                        reviewGatePolicyPromotionReviewRunCriteriaStatus,
                        reviewGatePolicyPromotionReviewRunCriteriaReason,
                        reviewGatePolicyPromotionReviewRunStatus,
                        reviewGatePolicyPromotionReviewRunReason
                );
        String reviewGatePolicyPromotionReviewRunApprovalStatus =
                AdminDashboardQueryPolicy.resolveReviewGatePolicyPromotionReviewRunApprovalStatus(
                        reviewGatePolicyPromotionReviewRunDecisionStatus
                );
        String reviewGatePolicyPromotionReviewRunApprovalCriteriaStatus =
                AdminDashboardQueryPolicy.resolveReviewGatePolicyPromotionReviewRunApprovalCriteriaStatus(
                        reviewGatePolicyPromotionReviewRunDecisionStatus
                );
        String reviewGatePolicyPromotionReviewRunApprovalCriteriaReason =
                AdminDashboardQueryPolicy.resolveReviewGatePolicyPromotionReviewRunApprovalCriteriaReason(
                        reviewGatePolicyPromotionReviewRunDecisionStatus,
                        reviewGatePolicyPromotionReviewRunDecisionReason
                );
        String reviewGatePolicyPromotionReviewRunApprovalDecisionStatus =
                AdminDashboardQueryPolicy.resolveReviewGatePolicyPromotionReviewRunApprovalDecisionStatus(
                        reviewGatePolicyPromotionReviewRunApprovalCriteriaStatus,
                        reviewGatePolicyPromotionReviewRunApprovalStatus
                );
        String reviewGatePolicyPromotionReviewRunApprovalDecisionReason =
                AdminDashboardQueryPolicy.resolveReviewGatePolicyPromotionReviewRunApprovalDecisionReason(
                        reviewGatePolicyPromotionReviewRunApprovalCriteriaStatus,
                        reviewGatePolicyPromotionReviewRunApprovalCriteriaReason,
                        reviewGatePolicyPromotionReviewRunApprovalStatus
                );
        String reviewGatePolicyPromotionReviewRunApprovalReason =
                AdminDashboardQueryPolicy.resolveReviewGatePolicyPromotionReviewRunApprovalReason(
                        reviewGatePolicyPromotionReviewRunApprovalDecisionStatus,
                        reviewGatePolicyPromotionReviewRunApprovalDecisionReason
                );
        String reviewGatePolicyPromotionReviewRunApprovalRecordCriteriaStatus =
                AdminDashboardQueryPolicy.resolveReviewGatePolicyPromotionReviewRunApprovalRecordCriteriaStatus(
                        reviewGatePolicyPromotionReviewRunApprovalStatus
                );
        String reviewGatePolicyPromotionReviewRunApprovalRecordCriteriaReason =
                AdminDashboardQueryPolicy.resolveReviewGatePolicyPromotionReviewRunApprovalRecordCriteriaReason(
                        reviewGatePolicyPromotionReviewRunApprovalStatus,
                        reviewGatePolicyPromotionReviewRunApprovalReason
                );
        String reviewGatePolicyPromotionReviewRunApprovalRecordStatus =
                AdminDashboardQueryPolicy.resolveReviewGatePolicyPromotionReviewRunApprovalRecordStatus(
                        reviewGatePolicyPromotionReviewRunApprovalDecisionStatus
                );
        String reviewGatePolicyPromotionReviewRunApprovalRecordReason =
                AdminDashboardQueryPolicy.resolveReviewGatePolicyPromotionReviewRunApprovalRecordReason(
                        reviewGatePolicyPromotionReviewRunApprovalDecisionStatus,
                        reviewGatePolicyPromotionReviewRunApprovalDecisionReason
                );
        String reviewGatePolicyPromotionReviewRunApprovalRecordTransitionStatus =
                AdminDashboardQueryPolicy.resolveReviewGatePolicyPromotionReviewRunApprovalRecordTransitionStatus(
                        reviewGatePolicyPromotionReviewRunApprovalRecordCriteriaStatus,
                        reviewGatePolicyPromotionReviewRunApprovalRecordStatus
                );
        String reviewGatePolicyPromotionReviewRunApprovalRecordTransitionReason =
                AdminDashboardQueryPolicy.resolveReviewGatePolicyPromotionReviewRunApprovalRecordTransitionReason(
                        reviewGatePolicyPromotionReviewRunApprovalRecordCriteriaStatus,
                        reviewGatePolicyPromotionReviewRunApprovalRecordCriteriaReason,
                        reviewGatePolicyPromotionReviewRunApprovalRecordStatus,
                        reviewGatePolicyPromotionReviewRunApprovalRecordReason
                );
        String reviewGatePolicyPromotionReviewRunApprovalRecordWriteStatus =
                AdminDashboardQueryPolicy.resolveReviewGatePolicyPromotionReviewRunApprovalRecordWriteStatus(
                        reviewGatePolicyPromotionReviewRunApprovalRecordTransitionStatus
                );
        String reviewGatePolicyPromotionReviewRunApprovalRecordWriteReason =
                AdminDashboardQueryPolicy.resolveReviewGatePolicyPromotionReviewRunApprovalRecordWriteReason(
                        reviewGatePolicyPromotionReviewRunApprovalRecordTransitionStatus,
                        reviewGatePolicyPromotionReviewRunApprovalRecordTransitionReason
                );
        ScoreWeightService.ScoreWeightProgress weightProgress =
                scoreWeightService.getProgress(recommendationSummary.totalLogs());
        ScoreWeight activeWeight = weightProgress.activeWeight();
        AdminDashboardReadRows.NotificationSummaryRow notificationSummary =
                adminDashboardNotificationReadRepository.fetchNotificationSummary(dayAgo, summaryWindowAgo);
        AdminDashboardReadRows.SearchSummaryRow searchSummary =
                adminDashboardSearchReadRepository.fetchSearchSummary(dayAgo, summaryWindowAgo);
        long openDuplicateGroups = adminPolicyDuplicateGroupReadRepository.countOpenGroups();
        long exactDuplicateGroups = adminPolicyDuplicateGroupReadRepository
                .countOpenGroupsByReviewClass("exact_duplicate_candidate");
        long mirrorVariantGroups = adminPolicyDuplicateGroupReadRepository
                .countOpenGroupsByReviewClass("mirror_or_channel_variant_candidate");
        long openLinkReviews = adminPolicyLinkReviewReadRepository.countOpenReviews();
        long benefitSupportLinkReviews = 0L;
        long announcementRecruitmentLinkReviews = 0L;
        for (String reviewBucket : adminPolicyLinkReviewReadRepository.findOpenReviewBuckets()) {
            if ("benefit_support".equals(reviewBucket)) {
                benefitSupportLinkReviews++;
            } else if ("announcement_recruitment".equals(reviewBucket)) {
                announcementRecruitmentLinkReviews++;
            }
        }
        PolicyTriageSummary policyTriageSummary = resolvePolicyTriageSummary(
                openDuplicateGroups,
                exactDuplicateGroups,
                mirrorVariantGroups,
                openLinkReviews,
                benefitSupportLinkReviews,
                announcementRecruitmentLinkReviews
        );
        UserPiiSyncStatusResponse userPiiSyncStatus =
                userPiiSyncStatusService.getStatus(AdminDashboardQueryPolicy.FAILED_SAMPLE_LIMIT);

        return new AdminDashboardResponse(
                now,
                new AdminDashboardResponse.CollectSection(
                        collectSummary.runningJobs(),
                        collectSummary.successJobsLast24h(),
                        collectSummary.partialSuccessJobsLast24h(),
                        collectSummary.failedJobsLast24h(),
                        summaryWindowDays,
                        adminDashboardCollectReadRepository.fetchLatestCollectJobs().stream()
                                .map(row -> new AdminDashboardResponse.CollectJobSnapshot(
                                        row.jobName(),
                                        row.status(),
                                        row.startedAt(),
                                        row.finishedAt(),
                                        row.requestedCount(),
                                        row.savedCount(),
                                        row.failedCount()
                                ))
                                .toList(),
                        adminDashboardCollectReadRepository.fetchLatestCollectFailures(summaryWindowAgo).stream()
                                .map(row -> new AdminDashboardResponse.CollectFailureSnapshot(
                                        row.jobName(),
                                        row.status(),
                                        row.startedAt(),
                                        row.finishedAt(),
                                        row.errorCode(),
                                        row.errorMessage(),
                                        row.requestedCount(),
                                        row.savedCount(),
                                        row.failedCount()
                                ))
                                .toList()
                ),
                new AdminDashboardResponse.RecommendationSection(
                        activeWeight.getWeightKey(),
                        activeWeight.getRuleWeight(),
                        activeWeight.getAiWeight(),
                        weightProgress.nextWeightKey(),
                        weightProgress.nextWeightMinLogCount(),
                        weightProgress.remainingLogsUntilNextWeight(),
                        weightProgress.topWeightStage(),
                        recommendationSummary.totalLogs(),
                        recommendationSummary.sentLast24h(),
                        summaryWindowDays,
                        recommendationSummary.sentInWindow(),
                        recommendationSummary.clickedInWindow(),
                        recommendationSummary.fallbackInWindow(),
                        recommendationSummary.latestClickedAt(),
                        AdminDashboardQueryPolicy.ratio(
                                recommendationSummary.clickedInWindow(),
                                recommendationSummary.sentInWindow()
                        ),
                        AdminDashboardQueryPolicy.ratio(
                                recommendationSummary.fallbackInWindow(),
                                recommendationSummary.sentInWindow()
                        ),
                        new AdminDashboardResponse.RecommendationTrafficMixSnapshot(
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
                        new AdminDashboardResponse.RecommendationConcentrationSnapshot(
                                recommendationConcentration.latestBatchRows(),
                                recommendationConcentration.latestBatchUsers(),
                                recommendationConcentration.latestBatchDistinctServices(),
                                recommendationConcentration.top1LeaderServiceId(),
                                recommendationConcentration.top1LeaderTitle(),
                                recommendationConcentration.top1LeaderSource(),
                                recommendationConcentration.top1LeaderCategory(),
                                recommendationConcentration.top1LeaderUsers(),
                                recommendationConcentration.top1LeaderSharePct(),
                                new AdminDashboardResponse.ServiceCohortMixSnapshot(
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
                        new AdminDashboardResponse.RecommendationRecentWindowSnapshot(
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
                        new AdminDashboardResponse.RecommendationReviewGateStalenessSnapshot(
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
                        reviewGatePolicyPromotionReviewRunStatus,
                        reviewGatePolicyPromotionReviewRunReason,
                        reviewGatePolicyPromotionReviewRunCriteriaStatus,
                        reviewGatePolicyPromotionReviewRunCriteriaReason,
                        reviewGatePolicyPromotionReviewRunDecisionStatus,
                        reviewGatePolicyPromotionReviewRunDecisionReason,
                        reviewGatePolicyPromotionReviewRunApprovalCriteriaStatus,
                        reviewGatePolicyPromotionReviewRunApprovalCriteriaReason,
                        reviewGatePolicyPromotionReviewRunApprovalDecisionStatus,
                        reviewGatePolicyPromotionReviewRunApprovalDecisionReason,
                        reviewGatePolicyPromotionReviewRunApprovalStatus,
                        reviewGatePolicyPromotionReviewRunApprovalReason,
                        reviewGatePolicyPromotionReviewRunApprovalRecordCriteriaStatus,
                        reviewGatePolicyPromotionReviewRunApprovalRecordCriteriaReason,
                        reviewGatePolicyPromotionReviewRunApprovalRecordStatus,
                        reviewGatePolicyPromotionReviewRunApprovalRecordReason,
                        reviewGatePolicyPromotionReviewRunApprovalRecordTransitionStatus,
                        reviewGatePolicyPromotionReviewRunApprovalRecordTransitionReason,
                        reviewGatePolicyPromotionReviewRunApprovalRecordWriteStatus,
                        reviewGatePolicyPromotionReviewRunApprovalRecordWriteReason,
                        adminDashboardRecommendationReadRepository.fetchRecommendationWeightBuckets(summaryWindowAgo).stream()
                                .map(row -> new AdminDashboardResponse.RecommendationWeightSnapshot(
                                        row.weightKey(),
                                        row.ruleWeight(),
                                        row.aiWeight(),
                                        row.logCount()
                                ))
                                .toList()
                ),
                new AdminDashboardResponse.NotificationSection(
                        notificationSummary.sentLast24h(),
                        notificationSummary.failedLast24h(),
                        summaryWindowDays,
                        notificationSummary.sentInWindow(),
                        notificationSummary.failedInWindow(),
                        notificationSummary.unreadAlerts(),
                        notificationSummary.staleUnread7d(),
                        notificationSummary.staleUnread14d(),
                        notificationSummary.retryableFailedNotifications(),
                        notificationSummary.terminalFailedNotifications()
                ),
                new AdminDashboardResponse.PolicyTriageSection(
                        policyTriageSummary.decisionClass(),
                        policyTriageSummary.operatorReading(),
                        policyTriageSummary.nextAction(),
                        openDuplicateGroups,
                        exactDuplicateGroups,
                        mirrorVariantGroups,
                        openLinkReviews,
                        benefitSupportLinkReviews,
                        announcementRecruitmentLinkReviews
                ),
                new AdminDashboardResponse.SearchSection(
                        searchSummary.searchesLast24h(),
                        summaryWindowDays,
                        searchSummary.searchesInWindow(),
                        searchSummary.zeroResultSearchesInWindow(),
                        searchSummary.uniqueFingerprintsInWindow(),
                        searchSummary.averageResultCountInWindow().setScale(2, java.math.RoundingMode.HALF_UP),
                        adminDashboardSearchReadRepository.fetchTopSearchKeywords(summaryWindowAgo).stream()
                                .map(row -> new AdminDashboardResponse.SearchKeywordSnapshot(
                                        row.keyword(),
                                        row.searchCount()
                                ))
                                .toList(),
                        adminDashboardSearchReadRepository.fetchTopZeroResultSearchKeywords(summaryWindowAgo).stream()
                                .map(row -> new AdminDashboardResponse.SearchKeywordSnapshot(
                                        row.keyword(),
                                        row.searchCount()
                                ))
                                .toList()
                ),
                new AdminDashboardResponse.UserPiiSyncSection(
                        userPiiSyncStatus.pendingCount(),
                        userPiiSyncStatus.failedCount(),
                        userPiiSyncStatus.syncedCount(),
                        userPiiSyncStatus.latestSyncedAt()
                ),
                new AdminDashboardResponse.TrendSection(
                        buildCollectTrends(now, trendWindows),
                        buildRecommendationTrends(now, trendWindows),
                        buildSearchTrends(now, trendWindows)
                )
        );
    }

    private PolicyTriageSummary resolvePolicyTriageSummary(
            long openDuplicateGroups,
            long exactDuplicateGroups,
            long mirrorVariantGroups,
            long openLinkReviews,
            long benefitSupportLinkReviews,
            long announcementRecruitmentLinkReviews
    ) {
        if (exactDuplicateGroups > 0 || mirrorVariantGroups > 0) {
            return new PolicyTriageSummary(
                    "DUPLICATE_THEN_LINK_PRIORITY",
                    "YOUTH exact/mirror duplicate 후보가 남아 있어 duplicate queue를 exact -> mirror 순으로 먼저 줄이는 편이 맞습니다.",
                    "exact duplicate -> mirror variant -> benefit/support link review"
            );
        }
        if (benefitSupportLinkReviews > 0 || announcementRecruitmentLinkReviews > 0 || openLinkReviews > 0) {
            return new PolicyTriageSummary(
                    "LINK_REVIEW_PRIORITY",
                    "현재 backlog는 정책 링크 review가 중심이며, 급부형과 모집형 bucket을 먼저 줄이는 편이 맞습니다.",
                    "benefit/support -> announcement/recruitment -> program/event"
            );
        }
        if (openDuplicateGroups > 0) {
            return new PolicyTriageSummary(
                    "DRIFT_TAIL_PRIORITY",
                    "exact/mirror 우선 후보는 줄었고, 남은 duplicate tail은 drift/classification review 위주입니다.",
                    "date/contract drift tail review"
            );
        }
        return new PolicyTriageSummary(
                "LOW_BACKLOG_STEADY_STATE",
                "정책 backlog는 급한 exact/mirror/link 우선 항목이 줄어든 상태입니다.",
                "keep nightly observation and small-batch review"
        );
    }

    private record PolicyTriageSummary(
            String decisionClass,
            String operatorReading,
            String nextAction
    ) {
    }

    private List<AdminDashboardResponse.CollectTrendPoint> buildCollectTrends(
            java.time.LocalDateTime now,
            List<Integer> trendWindows
    ) {
        List<AdminDashboardResponse.CollectTrendPoint> points = new ArrayList<>();
        for (int windowDays : trendWindows) {
            AdminDashboardReadRows.CollectTrendRow row =
                    adminDashboardCollectReadRepository.fetchCollectTrend(now.minusDays(windowDays));
            points.add(new AdminDashboardResponse.CollectTrendPoint(
                    windowDays,
                    row.successJobs(),
                    row.partialSuccessJobs(),
                    row.failedJobs()
            ));
        }
        return points;
    }

    private List<AdminDashboardResponse.RecommendationTrendPoint> buildRecommendationTrends(
            java.time.LocalDateTime now,
            List<Integer> trendWindows
    ) {
        List<AdminDashboardResponse.RecommendationTrendPoint> points = new ArrayList<>();
        for (int windowDays : trendWindows) {
            AdminDashboardReadRows.RecommendationTrendRow row =
                    adminDashboardRecommendationReadRepository.fetchRecommendationTrend(now.minusDays(windowDays));
            points.add(new AdminDashboardResponse.RecommendationTrendPoint(
                    windowDays,
                    row.sentCount(),
                    row.clickedCount(),
                    row.fallbackCount(),
                    AdminDashboardQueryPolicy.ratio(row.clickedCount(), row.sentCount()),
                    AdminDashboardQueryPolicy.ratio(row.fallbackCount(), row.sentCount())
            ));
        }
        return points;
    }

    private List<AdminDashboardResponse.SearchTrendPoint> buildSearchTrends(
            java.time.LocalDateTime now,
            List<Integer> trendWindows
    ) {
        List<AdminDashboardResponse.SearchTrendPoint> points = new ArrayList<>();
        for (int windowDays : trendWindows) {
            AdminDashboardReadRows.SearchTrendRow row =
                    adminDashboardSearchReadRepository.fetchSearchTrend(now.minusDays(windowDays));
            points.add(new AdminDashboardResponse.SearchTrendPoint(
                    windowDays,
                    row.searches(),
                    row.zeroResultSearches()
            ));
        }
        return points;
    }
}
