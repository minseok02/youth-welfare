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

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminDashboardService {

    private static final int FAILED_SAMPLE_LIMIT = 5;

    private final AdminDashboardReadRepository adminDashboardReadRepository;
    private final UserPiiSyncStatusService userPiiSyncStatusService;
    private final ScoreWeightService scoreWeightService;

    public AdminDashboardResponse getSummary() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime dayAgo = now.minusDays(1);
        LocalDateTime weekAgo = now.minusDays(7);

        AdminDashboardReadRepository.CollectSummaryRow collectSummary =
                adminDashboardReadRepository.fetchCollectSummary(dayAgo);
        AdminDashboardReadRepository.RecommendationSummaryRow recommendationSummary =
                adminDashboardReadRepository.fetchRecommendationSummary(dayAgo, weekAgo);
        ScoreWeight activeWeight = scoreWeightService.getActiveWeight();
        AdminDashboardReadRepository.NotificationSummaryRow notificationSummary =
                adminDashboardReadRepository.fetchNotificationSummary(dayAgo, weekAgo);
        AdminDashboardReadRepository.SearchSummaryRow searchSummary =
                adminDashboardReadRepository.fetchSearchSummary(dayAgo, weekAgo);
        UserPiiSyncStatusResponse userPiiSyncStatus = userPiiSyncStatusService.getStatus(FAILED_SAMPLE_LIMIT);

        return new AdminDashboardResponse(
                now,
                new AdminDashboardResponse.CollectSection(
                        collectSummary.runningJobs(),
                        collectSummary.successJobsLast24h(),
                        collectSummary.partialSuccessJobsLast24h(),
                        collectSummary.failedJobsLast24h(),
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
                        adminDashboardReadRepository.fetchLatestCollectFailures(weekAgo).stream()
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
                        recommendationSummary.sentLast7d(),
                        recommendationSummary.clickedLast7d(),
                        recommendationSummary.fallbackLast7d(),
                        recommendationSummary.latestClickedAt(),
                        ratio(recommendationSummary.clickedLast7d(), recommendationSummary.sentLast7d()),
                        ratio(recommendationSummary.fallbackLast7d(), recommendationSummary.sentLast7d()),
                        adminDashboardReadRepository.fetchRecommendationWeightBuckets(weekAgo).stream()
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
                        notificationSummary.sentLast7d(),
                        notificationSummary.failedLast7d()
                ),
                new AdminDashboardResponse.SearchSection(
                        searchSummary.searchesLast24h(),
                        searchSummary.searchesLast7d(),
                        searchSummary.zeroResultSearchesLast7d(),
                        searchSummary.uniqueFingerprintsLast7d(),
                        searchSummary.averageResultCountLast7d().setScale(2, RoundingMode.HALF_UP),
                        adminDashboardReadRepository.fetchTopSearchKeywords(weekAgo).stream()
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
                )
        );
    }

    private BigDecimal ratio(long numerator, long denominator) {
        if (denominator <= 0) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(numerator)
                .divide(BigDecimal.valueOf(denominator), 4, RoundingMode.HALF_UP);
    }
}
