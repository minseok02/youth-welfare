package com.example.welfare.admin.dashboard.controller;

import com.example.welfare.admin.dashboard.dto.AdminCollectFailureResponse;
import com.example.welfare.admin.dashboard.dto.AdminRecommendationCandidateDiagnosticResponse;
import com.example.welfare.admin.dashboard.dto.AdminRecommendationBreakdownResponse;
import com.example.welfare.admin.dashboard.dto.AdminRecommendationReviewGatePromotionApprovalRecordClearResponse;
import com.example.welfare.admin.dashboard.dto.AdminRecommendationReviewGatePromotionApprovalRecordRequest;
import com.example.welfare.admin.dashboard.dto.AdminRecommendationReviewGatePromotionApprovalRecordResponse;
import com.example.welfare.admin.dashboard.dto.AdminSearchFailureResponse;
import com.example.welfare.admin.dashboard.dto.AdminDashboardResponse;
import com.example.welfare.admin.dashboard.service.AdminDashboardCollectService;
import com.example.welfare.admin.dashboard.service.AdminDashboardRecommendationDiagnosticService;
import com.example.welfare.admin.dashboard.service.AdminDashboardRecommendationService;
import com.example.welfare.admin.dashboard.service.AdminRecommendationReviewGatePromotionApprovalRecordService;
import com.example.welfare.admin.dashboard.service.AdminDashboardSearchService;
import com.example.welfare.admin.dashboard.service.AdminDashboardSummaryService;
import com.example.welfare.global.auth.AuthenticatedUser;
import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.example.welfare.global.response.ApiResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
@Validated
public class AdminDashboardController {

    private final AdminDashboardSummaryService adminDashboardSummaryService;
    private final AdminDashboardSearchService adminDashboardSearchService;
    private final AdminDashboardRecommendationService adminDashboardRecommendationService;
    private final AdminDashboardRecommendationDiagnosticService adminDashboardRecommendationDiagnosticService;
    private final AdminDashboardCollectService adminDashboardCollectService;
    private final AdminRecommendationReviewGatePromotionApprovalRecordService
            adminRecommendationReviewGatePromotionApprovalRecordService;

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<AdminDashboardResponse>> getSummary(
            @RequestParam(name = "summaryWindowDays", required = false)
            @Min(value = 1, message = "summaryWindowDays는 1 이상이어야 합니다.")
            @Max(value = 365, message = "summaryWindowDays는 365 이하여야 합니다.")
            Integer summaryWindowDays,
            @RequestParam(name = "trendWindowDays", required = false)
            List<@Min(value = 1, message = "trendWindowDays는 1 이상이어야 합니다.")
                    @Max(value = 365, message = "trendWindowDays는 365 이하여야 합니다.")
                    Integer> trendWindowDays
    ) {
        validateSummaryWindowDays(summaryWindowDays);
        validateTrendWindowDays(trendWindowDays);
        log.info("[Admin] dashboard summary 조회 summaryWindowDays={} trendWindowDays={}", summaryWindowDays, trendWindowDays);
        return ResponseEntity.ok(ApiResponse.success(
                adminDashboardSummaryService.getSummary(summaryWindowDays, trendWindowDays)
        ));
    }

    @GetMapping("/search-failures")
    public ResponseEntity<ApiResponse<AdminSearchFailureResponse>> getSearchFailures(
            @RequestParam(name = "summaryWindowDays", required = false)
            @Min(value = 1, message = "summaryWindowDays는 1 이상이어야 합니다.")
            @Max(value = 365, message = "summaryWindowDays는 365 이하여야 합니다.")
            Integer summaryWindowDays,
            @RequestParam(name = "limit", required = false)
            @Min(value = 1, message = "limit는 1 이상이어야 합니다.")
            @Max(value = 20, message = "limit는 20 이하여야 합니다.")
            Integer limit
    ) {
        validateSummaryWindowDays(summaryWindowDays);
        validateDashboardLimit(limit);
        log.info("[Admin] dashboard search failures 조회 summaryWindowDays={} limit={}", summaryWindowDays, limit);
        return ResponseEntity.ok(ApiResponse.success(
                adminDashboardSearchService.getSearchFailures(summaryWindowDays, limit)
        ));
    }

    @GetMapping("/recommendation-breakdowns")
    public ResponseEntity<ApiResponse<AdminRecommendationBreakdownResponse>> getRecommendationBreakdowns(
            @RequestParam(name = "summaryWindowDays", required = false)
            @Min(value = 1, message = "summaryWindowDays는 1 이상이어야 합니다.")
            @Max(value = 365, message = "summaryWindowDays는 365 이하여야 합니다.")
            Integer summaryWindowDays,
            @RequestParam(name = "limit", required = false)
            @Min(value = 1, message = "limit는 1 이상이어야 합니다.")
            @Max(value = 20, message = "limit는 20 이하여야 합니다.")
            Integer limit
    ) {
        validateSummaryWindowDays(summaryWindowDays);
        validateDashboardLimit(limit);
        log.info("[Admin] dashboard recommendation breakdowns 조회 summaryWindowDays={} limit={}", summaryWindowDays, limit);
        return ResponseEntity.ok(ApiResponse.success(
                adminDashboardRecommendationService.getRecommendationBreakdowns(summaryWindowDays, limit)
        ));
    }

    @GetMapping("/recommendation-diagnostics")
    public ResponseEntity<ApiResponse<AdminRecommendationCandidateDiagnosticResponse>> getRecommendationDiagnostics(
            @RequestParam(name = "userKey") String userKey,
            @RequestParam(name = "serviceId")
            List<@Min(value = 1, message = "serviceId는 1 이상이어야 합니다.") Long> serviceIds
    ) {
        if (userKey == null || userKey.isBlank() || serviceIds == null || serviceIds.isEmpty()) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        log.info("[Admin] dashboard recommendation diagnostics 조회 userKey={} serviceIds={}", userKey, serviceIds);
        return ResponseEntity.ok(ApiResponse.success(
                adminDashboardRecommendationDiagnosticService.getRecommendationDiagnostics(userKey.trim(), serviceIds)
        ));
    }

    @GetMapping("/collect-failures")
    public ResponseEntity<ApiResponse<AdminCollectFailureResponse>> getCollectFailures(
            @RequestParam(name = "summaryWindowDays", required = false)
            @Min(value = 1, message = "summaryWindowDays는 1 이상이어야 합니다.")
            @Max(value = 365, message = "summaryWindowDays는 365 이하여야 합니다.")
            Integer summaryWindowDays,
            @RequestParam(name = "limit", required = false)
            @Min(value = 1, message = "limit는 1 이상이어야 합니다.")
            @Max(value = 20, message = "limit는 20 이하여야 합니다.")
            Integer limit
    ) {
        validateSummaryWindowDays(summaryWindowDays);
        validateDashboardLimit(limit);
        log.info("[Admin] dashboard collect failures 조회 summaryWindowDays={} limit={}", summaryWindowDays, limit);
        return ResponseEntity.ok(ApiResponse.success(
                adminDashboardCollectService.getCollectFailures(summaryWindowDays, limit)
        ));
    }

    @PostMapping("/recommendation-review-gate/promotion-approval-record")
    public ResponseEntity<ApiResponse<AdminRecommendationReviewGatePromotionApprovalRecordResponse>>
    recordRecommendationReviewGatePromotionApproval(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @RequestBody(required = false) AdminRecommendationReviewGatePromotionApprovalRecordRequest request
    ) {
        String actorUserKey = authenticatedUser != null ? authenticatedUser.userKey() : null;
        String approvalNote = request != null ? request.approvalNote() : null;
        log.info("[Admin] recommendation review gate promotion approval record upsert actorUserKey={}", actorUserKey);
        return ResponseEntity.ok(ApiResponse.success(
                adminRecommendationReviewGatePromotionApprovalRecordService.recordApproval(actorUserKey, approvalNote)
        ));
    }

    @DeleteMapping("/recommendation-review-gate/promotion-approval-record")
    public ResponseEntity<ApiResponse<AdminRecommendationReviewGatePromotionApprovalRecordClearResponse>>
    clearRecommendationReviewGatePromotionApproval() {
        log.info("[Admin] recommendation review gate promotion approval record clear");
        return ResponseEntity.ok(ApiResponse.success(
                adminRecommendationReviewGatePromotionApprovalRecordService.clearApproval()
        ));
    }

    private void validateSummaryWindowDays(Integer summaryWindowDays) {
        if (summaryWindowDays != null && (summaryWindowDays < 1 || summaryWindowDays > 365)) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
    }

    private void validateTrendWindowDays(List<Integer> trendWindowDays) {
        if (trendWindowDays == null) {
            return;
        }
        boolean hasInvalidWindow = trendWindowDays.stream()
                .anyMatch(windowDays -> windowDays == null || windowDays < 1 || windowDays > 365);
        if (hasInvalidWindow) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
    }

    private void validateDashboardLimit(Integer limit) {
        if (limit != null && (limit < 1 || limit > 20)) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
    }
}
