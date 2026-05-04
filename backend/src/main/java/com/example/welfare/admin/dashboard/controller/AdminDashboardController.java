package com.example.welfare.admin.dashboard.controller;

import com.example.welfare.admin.dashboard.dto.AdminCollectFailureResponse;
import com.example.welfare.admin.dashboard.dto.AdminRecommendationBreakdownResponse;
import com.example.welfare.admin.dashboard.dto.AdminSearchFailureResponse;
import com.example.welfare.admin.dashboard.dto.AdminDashboardResponse;
import com.example.welfare.admin.dashboard.service.AdminDashboardCollectService;
import com.example.welfare.admin.dashboard.service.AdminDashboardRecommendationService;
import com.example.welfare.admin.dashboard.service.AdminDashboardSearchService;
import com.example.welfare.admin.dashboard.service.AdminDashboardSummaryService;
import com.example.welfare.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final AdminDashboardSummaryService adminDashboardSummaryService;
    private final AdminDashboardSearchService adminDashboardSearchService;
    private final AdminDashboardRecommendationService adminDashboardRecommendationService;
    private final AdminDashboardCollectService adminDashboardCollectService;

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<AdminDashboardResponse>> getSummary(
            @RequestParam(name = "summaryWindowDays", required = false) Integer summaryWindowDays,
            @RequestParam(name = "trendWindowDays", required = false) List<Integer> trendWindowDays
    ) {
        log.info("[Admin] dashboard summary 조회 summaryWindowDays={} trendWindowDays={}", summaryWindowDays, trendWindowDays);
        return ResponseEntity.ok(ApiResponse.success(
                adminDashboardSummaryService.getSummary(summaryWindowDays, trendWindowDays)
        ));
    }

    @GetMapping("/search-failures")
    public ResponseEntity<ApiResponse<AdminSearchFailureResponse>> getSearchFailures(
            @RequestParam(name = "summaryWindowDays", required = false) Integer summaryWindowDays,
            @RequestParam(name = "limit", required = false) Integer limit
    ) {
        log.info("[Admin] dashboard search failures 조회 summaryWindowDays={} limit={}", summaryWindowDays, limit);
        return ResponseEntity.ok(ApiResponse.success(
                adminDashboardSearchService.getSearchFailures(summaryWindowDays, limit)
        ));
    }

    @GetMapping("/recommendation-breakdowns")
    public ResponseEntity<ApiResponse<AdminRecommendationBreakdownResponse>> getRecommendationBreakdowns(
            @RequestParam(name = "summaryWindowDays", required = false) Integer summaryWindowDays,
            @RequestParam(name = "limit", required = false) Integer limit
    ) {
        log.info("[Admin] dashboard recommendation breakdowns 조회 summaryWindowDays={} limit={}", summaryWindowDays, limit);
        return ResponseEntity.ok(ApiResponse.success(
                adminDashboardRecommendationService.getRecommendationBreakdowns(summaryWindowDays, limit)
        ));
    }

    @GetMapping("/collect-failures")
    public ResponseEntity<ApiResponse<AdminCollectFailureResponse>> getCollectFailures(
            @RequestParam(name = "summaryWindowDays", required = false) Integer summaryWindowDays,
            @RequestParam(name = "limit", required = false) Integer limit
    ) {
        log.info("[Admin] dashboard collect failures 조회 summaryWindowDays={} limit={}", summaryWindowDays, limit);
        return ResponseEntity.ok(ApiResponse.success(
                adminDashboardCollectService.getCollectFailures(summaryWindowDays, limit)
        ));
    }
}
