package com.example.welfare.admin.dashboard.service;

import com.example.welfare.admin.dashboard.dto.AdminDashboardResponse;
import com.example.welfare.admin.dashboard.repository.AdminDashboardReadRepository;
import com.example.welfare.recommend.entity.ScoreWeight;
import com.example.welfare.recommend.service.ScoreWeightService;
import com.example.welfare.user.dto.response.UserPiiSyncStatusResponse;
import com.example.welfare.user.service.UserPiiSyncStatusService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminDashboardService {

    private static final int FAILED_SAMPLE_LIMIT = 5;
    private static final int DEFAULT_SUMMARY_WINDOW_DAYS = 7;
    private static final List<Integer> DEFAULT_TREND_WINDOWS_DAYS = List.of(1, 7, 30);
    private static final int MAX_WINDOW_DAYS = 365;

    private final AdminDashboardReadRepository adminDashboardReadRepository;
    private final UserPiiSyncStatusService userPiiSyncStatusService;
    private final ScoreWeightService scoreWeightService;

    public AdminDashboardResponse getSummary() {
        return getSummary(null, null);
    }

    public AdminDashboardResponse getSummary(List<Integer> requestedTrendWindows) {
        return getSummary(null, requestedTrendWindows);
    }

    public AdminDashboardResponse getSummary(Integer requestedSummaryWindowDays, List<Integer> requestedTrendWindows) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime dayAgo = now.minusDays(1);
        int summaryWindowDays = resolveSummaryWindowDays(requestedSummaryWindowDays);
        LocalDateTime summaryWindowAgo = now.minusDays(summaryWindowDays);
        List<Integer> trendWindows = resolveTrendWindows(requestedTrendWindows);

        AdminDashboardReadRepository.CollectSummaryRow collectSummary =
                adminDashboardReadRepository.fetchCollectSummary(dayAgo);
        AdminDashboardReadRepository.RecommendationSummaryRow recommendationSummary =
                adminDashboardReadRepository.fetchRecommendationSummary(dayAgo, summaryWindowAgo);
        ScoreWeight activeWeight = scoreWeightService.getActiveWeight();
        AdminDashboardReadRepository.NotificationSummaryRow notificationSummary =
                adminDashboardReadRepository.fetchNotificationSummary(dayAgo, summaryWindowAgo);
        AdminDashboardReadRepository.SearchSummaryRow searchSummary =
                adminDashboardReadRepository.fetchSearchSummary(dayAgo, summaryWindowAgo);
        UserPiiSyncStatusResponse userPiiSyncStatus = userPiiSyncStatusService.getStatus(FAILED_SAMPLE_LIMIT);

        return new AdminDashboardResponse(
                now,
                new AdminDashboardResponse.CollectSection(
                        collectSummary.runningJobs(),
                        collectSummary.successJobsLast24h(),
                        collectSummary.partialSuccessJobsLast24h(),
                        collectSummary.failedJobsLast24h(),
                        summaryWindowDays,
                        adminDashboardReadRepository.fetchLatestCollectJobs().stream()
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
                        adminDashboardReadRepository.fetchLatestCollectFailures(summaryWindowAgo).stream()
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
                        recommendationSummary.totalLogs(),
                        recommendationSummary.sentLast24h(),
                        summaryWindowDays,
                        recommendationSummary.sentInWindow(),
                        recommendationSummary.clickedInWindow(),
                        recommendationSummary.fallbackInWindow(),
                        recommendationSummary.latestClickedAt(),
                        ratio(recommendationSummary.clickedInWindow(), recommendationSummary.sentInWindow()),
                        ratio(recommendationSummary.fallbackInWindow(), recommendationSummary.sentInWindow()),
                        adminDashboardReadRepository.fetchRecommendationWeightBuckets(summaryWindowAgo).stream()
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
                        notificationSummary.failedInWindow()
                ),
                new AdminDashboardResponse.SearchSection(
                        searchSummary.searchesLast24h(),
                        summaryWindowDays,
                        searchSummary.searchesInWindow(),
                        searchSummary.zeroResultSearchesInWindow(),
                        searchSummary.uniqueFingerprintsInWindow(),
                        searchSummary.averageResultCountInWindow().setScale(2, RoundingMode.HALF_UP),
                        adminDashboardReadRepository.fetchTopSearchKeywords(summaryWindowAgo).stream()
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

    private int resolveSummaryWindowDays(Integer requestedSummaryWindowDays) {
        if (requestedSummaryWindowDays == null) {
            return DEFAULT_SUMMARY_WINDOW_DAYS;
        }

        if (requestedSummaryWindowDays <= 0 || requestedSummaryWindowDays > MAX_WINDOW_DAYS) {
            return DEFAULT_SUMMARY_WINDOW_DAYS;
        }

        return requestedSummaryWindowDays;
    }

    private List<Integer> resolveTrendWindows(List<Integer> requestedTrendWindows) {
        if (requestedTrendWindows == null || requestedTrendWindows.isEmpty()) {
            return DEFAULT_TREND_WINDOWS_DAYS;
        }

        List<Integer> normalized = requestedTrendWindows.stream()
                .filter(java.util.Objects::nonNull)
                .filter(windowDays -> windowDays > 0 && windowDays <= MAX_WINDOW_DAYS)
                .collect(java.util.stream.Collectors.collectingAndThen(
                        java.util.stream.Collectors.toCollection(LinkedHashSet::new),
                        List::copyOf
                ));

        return normalized.isEmpty() ? DEFAULT_TREND_WINDOWS_DAYS : normalized;
    }

    private java.util.List<AdminDashboardResponse.CollectTrendPoint> buildCollectTrends(LocalDateTime now, List<Integer> trendWindows) {
        java.util.List<AdminDashboardResponse.CollectTrendPoint> points = new java.util.ArrayList<>();
        for (int windowDays : trendWindows) {
            AdminDashboardReadRepository.CollectTrendRow row =
                    adminDashboardReadRepository.fetchCollectTrend(now.minusDays(windowDays));
            points.add(new AdminDashboardResponse.CollectTrendPoint(
                    windowDays,
                    row.successJobs(),
                    row.partialSuccessJobs(),
                    row.failedJobs()
            ));
        }
        return points;
    }

    private java.util.List<AdminDashboardResponse.RecommendationTrendPoint> buildRecommendationTrends(LocalDateTime now, List<Integer> trendWindows) {
        java.util.List<AdminDashboardResponse.RecommendationTrendPoint> points = new java.util.ArrayList<>();
        for (int windowDays : trendWindows) {
            AdminDashboardReadRepository.RecommendationTrendRow row =
                    adminDashboardReadRepository.fetchRecommendationTrend(now.minusDays(windowDays));
            points.add(new AdminDashboardResponse.RecommendationTrendPoint(
                    windowDays,
                    row.sentCount(),
                    row.clickedCount(),
                    row.fallbackCount(),
                    ratio(row.clickedCount(), row.sentCount()),
                    ratio(row.fallbackCount(), row.sentCount())
            ));
        }
        return points;
    }

    private java.util.List<AdminDashboardResponse.SearchTrendPoint> buildSearchTrends(LocalDateTime now, List<Integer> trendWindows) {
        java.util.List<AdminDashboardResponse.SearchTrendPoint> points = new java.util.ArrayList<>();
        for (int windowDays : trendWindows) {
            AdminDashboardReadRepository.SearchTrendRow row =
                    adminDashboardReadRepository.fetchSearchTrend(now.minusDays(windowDays));
            points.add(new AdminDashboardResponse.SearchTrendPoint(
                    windowDays,
                    row.searches(),
                    row.zeroResultSearches()
            ));
        }
        return points;
    }

    private BigDecimal ratio(long numerator, long denominator) {
        if (denominator <= 0) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(numerator)
                .divide(BigDecimal.valueOf(denominator), 4, RoundingMode.HALF_UP);
    }
}
