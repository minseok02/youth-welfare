package com.example.welfare.admin.dashboard.service;

import com.example.welfare.admin.dashboard.dto.AdminDashboardResponse;
import com.example.welfare.admin.dashboard.repository.AdminDashboardCollectReadRepository;
import com.example.welfare.admin.dashboard.repository.AdminDashboardNotificationReadRepository;
import com.example.welfare.admin.dashboard.repository.AdminDashboardReadRows;
import com.example.welfare.admin.dashboard.repository.AdminDashboardRecommendationReadRepository;
import com.example.welfare.admin.dashboard.repository.AdminDashboardSearchReadRepository;
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
        ScoreWeightService.ScoreWeightProgress weightProgress =
                scoreWeightService.getProgress(recommendationSummary.totalLogs());
        ScoreWeight activeWeight = weightProgress.activeWeight();
        AdminDashboardReadRows.NotificationSummaryRow notificationSummary =
                adminDashboardNotificationReadRepository.fetchNotificationSummary(dayAgo, summaryWindowAgo);
        AdminDashboardReadRows.SearchSummaryRow searchSummary =
                adminDashboardSearchReadRepository.fetchSearchSummary(dayAgo, summaryWindowAgo);
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
                        notificationSummary.failedInWindow()
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
