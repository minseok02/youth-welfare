package com.example.welfare.admin.dashboard.service;

import com.example.welfare.admin.dashboard.dto.AdminCollectFailureResponse;
import com.example.welfare.admin.dashboard.dto.AdminSearchFailureResponse;
import com.example.welfare.admin.dashboard.dto.AdminRecommendationBreakdownResponse;
import com.example.welfare.admin.dashboard.dto.AdminDashboardResponse;
import com.example.welfare.collect.entity.ApiSyncLog;
import com.example.welfare.collect.gateway.BokjiroLocalClient;
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
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminDashboardService {

    private static final int FAILED_SAMPLE_LIMIT = 5;
    private static final int COLLECT_FAILURE_PATTERN_LIMIT = 5;
    private static final int COLLECT_STREAK_RUN_LIMIT = 20;
    private static final int SEARCH_FAILURE_PATTERN_LIMIT = 5;
    private static final int RECOMMENDATION_BREAKDOWN_LIMIT = 5;
    private static final int DEFAULT_SUMMARY_WINDOW_DAYS = 7;
    private static final List<Integer> DEFAULT_TREND_WINDOWS_DAYS = List.of(1, 7, 30);
    private static final int MAX_WINDOW_DAYS = 365;
    private static final int MAX_COLLECT_FAILURE_PATTERN_LIMIT = 20;
    private static final int MAX_SEARCH_FAILURE_PATTERN_LIMIT = 20;
    private static final int MAX_RECOMMENDATION_BREAKDOWN_LIMIT = 20;

    private final AdminDashboardReadRepository adminDashboardReadRepository;
    private final UserPiiSyncStatusService userPiiSyncStatusService;
    private final ScoreWeightService scoreWeightService;
    private final BokjiroLocalClient bokjiroLocalClient;

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
        ScoreWeightService.ScoreWeightProgress weightProgress =
                scoreWeightService.getProgress(recommendationSummary.totalLogs());
        ScoreWeight activeWeight = weightProgress.activeWeight();
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
                                .toList(),
                        adminDashboardReadRepository.fetchTopZeroResultSearchKeywords(summaryWindowAgo).stream()
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

    public AdminSearchFailureResponse getSearchFailures(Integer requestedSummaryWindowDays, Integer requestedLimit) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime dayAgo = now.minusDays(1);
        int summaryWindowDays = resolveSummaryWindowDays(requestedSummaryWindowDays);
        int patternLimit = resolveSearchFailurePatternLimit(requestedLimit);
        LocalDateTime summaryWindowAgo = now.minusDays(summaryWindowDays);

        AdminDashboardReadRepository.SearchSummaryRow searchSummary =
                adminDashboardReadRepository.fetchSearchSummary(dayAgo, summaryWindowAgo);

        return new AdminSearchFailureResponse(
                now,
                summaryWindowDays,
                searchSummary.zeroResultSearchesInWindow(),
                adminDashboardReadRepository.fetchTopZeroResultSearchKeywords(summaryWindowAgo, patternLimit).stream()
                        .map(row -> new AdminSearchFailureResponse.KeywordCount(
                                row.keyword(),
                                row.searchCount()
                        ))
                        .toList(),
                adminDashboardReadRepository.fetchTopZeroResultRegions(summaryWindowAgo, patternLimit).stream()
                        .map(row -> new AdminSearchFailureResponse.RegionCount(
                                row.sido(),
                                row.sgg(),
                                row.searchCount()
                        ))
                        .toList(),
                adminDashboardReadRepository.fetchTopZeroResultFilterPatterns(summaryWindowAgo, patternLimit).stream()
                        .map(row -> new AdminSearchFailureResponse.FilterPatternCount(
                                row.statusFilter(),
                                row.category(),
                                row.sourceType(),
                                row.onlineApply(),
                                row.includeClosed(),
                                row.sortKey(),
                                row.searchCount()
                        ))
                        .toList(),
                adminDashboardReadRepository.fetchRecentZeroResultSearchSamples(summaryWindowAgo, patternLimit).stream()
                        .map(row -> new AdminSearchFailureResponse.SearchFailureSample(
                                row.keyword(),
                                row.sido(),
                                row.sgg(),
                                row.statusFilter(),
                                row.category(),
                                row.sourceType(),
                                row.onlineApply(),
                                row.includeClosed(),
                                row.sortKey(),
                                row.searchedAt()
                        ))
                        .toList(),
                adminDashboardReadRepository.fetchZeroResultRetryGroups(summaryWindowAgo, patternLimit).stream()
                        .map(row -> new AdminSearchFailureResponse.RetryGroup(
                                row.actorType(),
                                row.actorKey(),
                                row.keyword(),
                                row.sido(),
                                row.sgg(),
                                row.statusFilter(),
                                row.category(),
                                row.sourceType(),
                                row.onlineApply(),
                                row.includeClosed(),
                                row.sortKey(),
                                row.retryCount(),
                                row.firstSearchedAt(),
                                row.latestSearchedAt()
                        ))
                        .toList()
        );
    }

    public AdminRecommendationBreakdownResponse getRecommendationBreakdowns(Integer requestedSummaryWindowDays, Integer requestedLimit) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime dayAgo = now.minusDays(1);
        int summaryWindowDays = resolveSummaryWindowDays(requestedSummaryWindowDays);
        int breakdownLimit = resolveRecommendationBreakdownLimit(requestedLimit);
        LocalDateTime summaryWindowAgo = now.minusDays(summaryWindowDays);

        AdminDashboardReadRepository.RecommendationSummaryRow recommendationSummary =
                adminDashboardReadRepository.fetchRecommendationSummary(dayAgo, summaryWindowAgo);

        return new AdminRecommendationBreakdownResponse(
                now,
                summaryWindowDays,
                recommendationSummary.sentInWindow(),
                recommendationSummary.clickedInWindow(),
                recommendationSummary.fallbackInWindow(),
                adminDashboardReadRepository.fetchRecommendationSourceBreakdowns(summaryWindowAgo, breakdownLimit).stream()
                        .map(row -> new AdminRecommendationBreakdownResponse.SourceBreakdown(
                                row.sourceType(),
                                row.sentCount(),
                                row.clickedCount(),
                                row.fallbackCount(),
                                ratio(row.clickedCount(), row.sentCount()),
                                ratio(row.fallbackCount(), row.sentCount())
                        ))
                        .toList(),
                adminDashboardReadRepository.fetchRecommendationCategoryBreakdowns(summaryWindowAgo, breakdownLimit).stream()
                        .map(row -> new AdminRecommendationBreakdownResponse.CategoryBreakdown(
                                row.category(),
                                row.sentCount(),
                                row.clickedCount(),
                                row.fallbackCount(),
                                ratio(row.clickedCount(), row.sentCount()),
                                ratio(row.fallbackCount(), row.sentCount())
                        ))
                        .toList(),
                adminDashboardReadRepository.fetchRecommendationWeightBreakdowns(summaryWindowAgo, breakdownLimit).stream()
                        .map(row -> new AdminRecommendationBreakdownResponse.WeightBreakdown(
                                row.weightKey(),
                                row.ruleWeight(),
                                row.aiWeight(),
                                row.sentCount(),
                                row.clickedCount(),
                                row.fallbackCount(),
                                ratio(row.clickedCount(), row.sentCount()),
                                ratio(row.fallbackCount(), row.sentCount())
                        ))
                        .toList(),
                adminDashboardReadRepository.fetchRecentFallbackRecommendationSamples(summaryWindowAgo, breakdownLimit).stream()
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
                adminDashboardReadRepository.fetchRecentClickedRecommendationSamples(summaryWindowAgo, breakdownLimit).stream()
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
                adminDashboardReadRepository.fetchRecommendationRepeatExposureGroups(summaryWindowAgo, breakdownLimit).stream()
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

    public AdminCollectFailureResponse getCollectFailures(Integer requestedSummaryWindowDays, Integer requestedLimit) {
        LocalDateTime now = LocalDateTime.now();
        int summaryWindowDays = resolveSummaryWindowDays(requestedSummaryWindowDays);
        int patternLimit = resolveCollectFailurePatternLimit(requestedLimit);
        LocalDateTime summaryWindowAgo = now.minusDays(summaryWindowDays);

        AdminDashboardReadRepository.CollectFailureSummaryRow collectFailureSummary =
                adminDashboardReadRepository.fetchCollectFailureSummary(summaryWindowAgo);

        return new AdminCollectFailureResponse(
                now,
                summaryWindowDays,
                collectFailureSummary.totalFailedJobs(),
                collectFailureSummary.totalPartialSuccessJobs(),
                adminDashboardReadRepository.fetchCollectFailureJobBreakdowns(summaryWindowAgo, patternLimit).stream()
                        .map(row -> new AdminCollectFailureResponse.JobBreakdown(
                                row.jobName(),
                                row.failedCount(),
                                row.partialSuccessCount(),
                                row.latestStartedAt()
                        ))
                        .toList(),
                buildCollectJobStreaks(summaryWindowAgo, patternLimit),
                adminDashboardReadRepository.fetchCollectFailureErrorCodeBreakdowns(summaryWindowAgo, patternLimit).stream()
                        .map(row -> new AdminCollectFailureResponse.ErrorCodeBreakdown(
                                row.errorCode(),
                                row.failedCount()
                        ))
                        .toList(),
                adminDashboardReadRepository.fetchRecentCollectFailureSamples(summaryWindowAgo, patternLimit).stream()
                        .map(row -> new AdminCollectFailureResponse.FailureSample(
                                row.jobName(),
                                row.status(),
                                row.errorCode(),
                                row.errorMessage(),
                                row.startedAt(),
                                row.finishedAt(),
                                row.requestedCount(),
                                row.savedCount(),
                                row.failedCount()
                        ))
                        .toList(),
                List.of(toCircuitStatus("BOKJIRO_LOCAL", bokjiroLocalClient.getRateLimitCircuitStatus()))
        );
    }

    private List<AdminCollectFailureResponse.JobStreak> buildCollectJobStreaks(LocalDateTime windowAgo, int limit) {
        Map<String, List<AdminDashboardReadRepository.CollectJobRunRow>> runsByJob = adminDashboardReadRepository
                .fetchRecentCollectJobRuns(windowAgo, COLLECT_STREAK_RUN_LIMIT)
                .stream()
                .collect(Collectors.groupingBy(
                        AdminDashboardReadRepository.CollectJobRunRow::jobName,
                        java.util.LinkedHashMap::new,
                        Collectors.toList()
                ));

        List<AdminCollectFailureResponse.JobStreak> streaks = new ArrayList<>();
        for (Map.Entry<String, List<AdminDashboardReadRepository.CollectJobRunRow>> entry : runsByJob.entrySet()) {
            List<AdminDashboardReadRepository.CollectJobRunRow> runs = entry.getValue();
            if (runs.isEmpty()) {
                continue;
            }

            ApiSyncLog.SyncStatus latestStatus = ApiSyncLog.SyncStatus.valueOf(runs.get(0).status());
            if (latestStatus != ApiSyncLog.SyncStatus.FAILED && latestStatus != ApiSyncLog.SyncStatus.PARTIAL_SUCCESS) {
                continue;
            }

            long streakCount = 0;
            for (AdminDashboardReadRepository.CollectJobRunRow run : runs) {
                ApiSyncLog.SyncStatus status = ApiSyncLog.SyncStatus.valueOf(run.status());
                if (status != latestStatus) {
                    break;
                }
                streakCount++;
            }

            streaks.add(new AdminCollectFailureResponse.JobStreak(
                    entry.getKey(),
                    latestStatus.name(),
                    streakCount,
                    runs.get(0).startedAt()
            ));
        }

        return streaks.stream()
                .sorted(java.util.Comparator
                        .comparingLong(AdminCollectFailureResponse.JobStreak::streakCount).reversed()
                        .thenComparing(AdminCollectFailureResponse.JobStreak::latestStartedAt, java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder()))
                        .thenComparing(AdminCollectFailureResponse.JobStreak::jobName))
                .limit(limit)
                .toList();
    }

    private AdminCollectFailureResponse.CircuitStatus toCircuitStatus(
            String circuitKey,
            BokjiroLocalClient.RateLimitCircuitStatus status
    ) {
        return new AdminCollectFailureResponse.CircuitStatus(
                circuitKey,
                status.open(),
                status.remainingMs(),
                status.openUntil()
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

    private int resolveSearchFailurePatternLimit(Integer requestedLimit) {
        if (requestedLimit == null) {
            return SEARCH_FAILURE_PATTERN_LIMIT;
        }

        if (requestedLimit <= 0 || requestedLimit > MAX_SEARCH_FAILURE_PATTERN_LIMIT) {
            return SEARCH_FAILURE_PATTERN_LIMIT;
        }

        return requestedLimit;
    }

    private int resolveRecommendationBreakdownLimit(Integer requestedLimit) {
        if (requestedLimit == null) {
            return RECOMMENDATION_BREAKDOWN_LIMIT;
        }

        if (requestedLimit <= 0 || requestedLimit > MAX_RECOMMENDATION_BREAKDOWN_LIMIT) {
            return RECOMMENDATION_BREAKDOWN_LIMIT;
        }

        return requestedLimit;
    }

    private int resolveCollectFailurePatternLimit(Integer requestedLimit) {
        if (requestedLimit == null) {
            return COLLECT_FAILURE_PATTERN_LIMIT;
        }

        if (requestedLimit <= 0 || requestedLimit > MAX_COLLECT_FAILURE_PATTERN_LIMIT) {
            return COLLECT_FAILURE_PATTERN_LIMIT;
        }

        return requestedLimit;
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
