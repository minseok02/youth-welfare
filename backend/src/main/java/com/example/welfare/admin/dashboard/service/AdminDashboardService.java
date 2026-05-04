package com.example.welfare.admin.dashboard.service;

import com.example.welfare.admin.dashboard.dto.AdminCollectFailureResponse;
import com.example.welfare.admin.dashboard.dto.AdminDashboardResponse;
import com.example.welfare.admin.dashboard.dto.AdminRecommendationBreakdownResponse;
import com.example.welfare.admin.dashboard.dto.AdminSearchFailureResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminDashboardService {

    private final AdminDashboardSummaryService adminDashboardSummaryService;
    private final AdminDashboardSearchService adminDashboardSearchService;
    private final AdminDashboardRecommendationService adminDashboardRecommendationService;
    private final AdminDashboardCollectService adminDashboardCollectService;

    public AdminDashboardResponse getSummary() {
        return adminDashboardSummaryService.getSummary(null, null);
    }

    public AdminDashboardResponse getSummary(List<Integer> requestedTrendWindows) {
        return adminDashboardSummaryService.getSummary(null, requestedTrendWindows);
    }

    public AdminDashboardResponse getSummary(Integer requestedSummaryWindowDays, List<Integer> requestedTrendWindows) {
        return adminDashboardSummaryService.getSummary(requestedSummaryWindowDays, requestedTrendWindows);
    }

    public AdminSearchFailureResponse getSearchFailures(Integer requestedSummaryWindowDays, Integer requestedLimit) {
        return adminDashboardSearchService.getSearchFailures(requestedSummaryWindowDays, requestedLimit);
    }

    public AdminRecommendationBreakdownResponse getRecommendationBreakdowns(
            Integer requestedSummaryWindowDays,
            Integer requestedLimit
    ) {
        return adminDashboardRecommendationService.getRecommendationBreakdowns(requestedSummaryWindowDays, requestedLimit);
    }

    public AdminCollectFailureResponse getCollectFailures(Integer requestedSummaryWindowDays, Integer requestedLimit) {
        return adminDashboardCollectService.getCollectFailures(requestedSummaryWindowDays, requestedLimit);
    }
}
