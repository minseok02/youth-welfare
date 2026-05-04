package com.example.welfare.admin.dashboard.service;

import com.example.welfare.admin.dashboard.dto.AdminSearchFailureResponse;
import com.example.welfare.admin.dashboard.repository.AdminDashboardReadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminDashboardSearchService {

    private final AdminDashboardReadRepository adminDashboardReadRepository;

    public AdminSearchFailureResponse getSearchFailures(Integer requestedSummaryWindowDays, Integer requestedLimit) {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        java.time.LocalDateTime dayAgo = now.minusDays(1);
        int summaryWindowDays = AdminDashboardQueryPolicy.resolveSummaryWindowDays(requestedSummaryWindowDays);
        int patternLimit = AdminDashboardQueryPolicy.resolveSearchFailurePatternLimit(requestedLimit);
        java.time.LocalDateTime summaryWindowAgo = now.minusDays(summaryWindowDays);

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
                        .toList(),
                adminDashboardReadRepository.fetchRecoveredSearchGroups(summaryWindowAgo, patternLimit).stream()
                        .map(row -> new AdminSearchFailureResponse.RecoveredSearchGroup(
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
                                row.zeroResultCount(),
                                row.recoveredResultCount(),
                                row.latestRecoveredAt()
                        ))
                        .toList()
        );
    }
}
