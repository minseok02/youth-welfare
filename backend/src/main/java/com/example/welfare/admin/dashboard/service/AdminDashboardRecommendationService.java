package com.example.welfare.admin.dashboard.service;

import com.example.welfare.admin.dashboard.dto.AdminRecommendationBreakdownResponse;
import com.example.welfare.admin.dashboard.repository.AdminDashboardReadRows;
import com.example.welfare.admin.dashboard.repository.AdminDashboardRecommendationReadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminDashboardRecommendationService {

    private final AdminDashboardRecommendationReadRepository adminDashboardRecommendationReadRepository;

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
        String realUserTrafficGate =
                AdminDashboardQueryPolicy.resolveRealUserTrafficGate(recommendationSummary, recommendationTrafficMix);

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
                        recommendationConcentration.concentrationReadiness(),
                        recommendationConcentration.realUserCohortGate(),
                        recommendationConcentration.signalQuality()
                ),
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
}
