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

        return new AdminRecommendationBreakdownResponse(
                now,
                summaryWindowDays,
                recommendationSummary.sentInWindow(),
                recommendationSummary.clickedInWindow(),
                recommendationSummary.fallbackInWindow(),
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
