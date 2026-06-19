package com.example.welfare.admin.dashboard.controller;

import com.example.welfare.admin.dashboard.dto.AdminCollectFailureResponse;
import com.example.welfare.admin.dashboard.dto.AdminDashboardAttentionResponse;
import com.example.welfare.admin.dashboard.dto.AdminRecommendationCandidateDiagnosticResponse;
import com.example.welfare.admin.dashboard.dto.AdminRecommendationBreakdownResponse;
import com.example.welfare.admin.dashboard.dto.AdminPolicyErrorReportResponse;
import com.example.welfare.admin.dashboard.dto.AdminPolicyDuplicateGroupResponse;
import com.example.welfare.admin.dashboard.dto.AdminPolicyDuplicateGroupReviewRequest;
import com.example.welfare.admin.dashboard.dto.AdminPolicyFieldCorrectionListResponse;
import com.example.welfare.admin.dashboard.dto.AdminPolicyFieldCorrectionRequest;
import com.example.welfare.admin.dashboard.dto.AdminPolicyFieldCorrectionResponse;
import com.example.welfare.admin.dashboard.dto.AdminPolicyLinkReviewResponse;
import com.example.welfare.admin.dashboard.dto.AdminPolicyRegionAuditResponse;
import com.example.welfare.admin.dashboard.dto.AdminPolicyRegionCorrectionListResponse;
import com.example.welfare.admin.dashboard.dto.AdminPolicyRegionCorrectionRequest;
import com.example.welfare.admin.dashboard.dto.AdminPolicyRegionCorrectionRevertRequest;
import com.example.welfare.admin.dashboard.dto.AdminPolicyRegionCorrectionResponse;
import com.example.welfare.admin.dashboard.dto.AdminQueueStatusFilter;
import com.example.welfare.admin.dashboard.dto.AdminRegionOptionResponse;
import com.example.welfare.admin.dashboard.dto.AdminReviewActionRequest;
import com.example.welfare.admin.dashboard.dto.AdminReviewActionResponse;
import com.example.welfare.admin.dashboard.dto.AdminRecommendationReviewGatePromotionApprovalRecordClearResponse;
import com.example.welfare.admin.dashboard.dto.AdminRecommendationReviewGatePromotionApprovalRecordRequest;
import com.example.welfare.admin.dashboard.dto.AdminRecommendationReviewGatePromotionApprovalRecordResponse;
import com.example.welfare.admin.dashboard.dto.AdminSearchFailureResponse;
import com.example.welfare.admin.dashboard.dto.AdminDashboardResponse;
import com.example.welfare.admin.dashboard.dto.AdminNotificationStaleHideRequest;
import com.example.welfare.admin.dashboard.dto.AdminNotificationStaleHideResponse;
import com.example.welfare.admin.dashboard.dto.AdminNotificationStaleTargetResponse;
import com.example.welfare.admin.dashboard.dto.AdminStandardCodeEffectObservationResponse;
import com.example.welfare.admin.dashboard.dto.AdminSupportInquiryResponse;
import com.example.welfare.admin.dashboard.dto.AdminUserProfileStandardCodeCoverageResponse;
import com.example.welfare.admin.dashboard.dto.AdminWrapperObservationResponse;
import com.example.welfare.admin.dashboard.service.AdminDashboardCollectService;
import com.example.welfare.admin.dashboard.service.AdminDashboardAttentionService;
import com.example.welfare.admin.dashboard.service.AdminDashboardRecommendationDiagnosticService;
import com.example.welfare.admin.dashboard.service.AdminDashboardRecommendationService;
import com.example.welfare.admin.dashboard.service.AdminRecommendationReviewGatePromotionApprovalRecordService;
import com.example.welfare.admin.dashboard.service.AdminDashboardSearchService;
import com.example.welfare.admin.dashboard.service.AdminDashboardStandardCodeObservationService;
import com.example.welfare.admin.dashboard.service.AdminDashboardSummaryService;
import com.example.welfare.admin.dashboard.service.AdminDashboardUserProfileService;
import com.example.welfare.admin.dashboard.service.AdminDashboardWrapperObservationService;
import com.example.welfare.admin.dashboard.service.AdminNotificationBacklogService;
import com.example.welfare.admin.dashboard.service.AdminNotificationStaleTargetService;
import com.example.welfare.admin.dashboard.service.AdminPolicyErrorReportService;
import com.example.welfare.admin.dashboard.service.AdminPolicyFieldCorrectionService;
import com.example.welfare.admin.dashboard.service.AdminPolicyDuplicateGroupService;
import com.example.welfare.admin.dashboard.service.AdminPolicyLinkReviewService;
import com.example.welfare.admin.dashboard.service.AdminPolicyRegionAuditService;
import com.example.welfare.admin.dashboard.service.AdminPolicyRegionCorrectionService;
import com.example.welfare.admin.dashboard.service.AdminSupportInquiryService;
import com.example.welfare.admin.service.AdminOperationRateLimitService;
import com.example.welfare.global.auth.AuthenticatedUser;
import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.example.welfare.global.response.ApiResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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
    private final AdminDashboardAttentionService adminDashboardAttentionService;
    private final AdminDashboardUserProfileService adminDashboardUserProfileService;
    private final AdminDashboardStandardCodeObservationService adminDashboardStandardCodeObservationService;
    private final AdminDashboardWrapperObservationService adminDashboardWrapperObservationService;
    private final AdminNotificationBacklogService adminNotificationBacklogService;
    private final AdminNotificationStaleTargetService adminNotificationStaleTargetService;
    private final AdminPolicyErrorReportService adminPolicyErrorReportService;
    private final AdminPolicyFieldCorrectionService adminPolicyFieldCorrectionService;
    private final AdminPolicyDuplicateGroupService adminPolicyDuplicateGroupService;
    private final AdminPolicyLinkReviewService adminPolicyLinkReviewService;
    private final AdminPolicyRegionAuditService adminPolicyRegionAuditService;
    private final AdminPolicyRegionCorrectionService adminPolicyRegionCorrectionService;
    private final AdminSupportInquiryService adminSupportInquiryService;
    private final AdminRecommendationReviewGatePromotionApprovalRecordService
            adminRecommendationReviewGatePromotionApprovalRecordService;
    private final AdminOperationRateLimitService adminOperationRateLimitService;

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
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @RequestParam(name = "userKey")
            @NotBlank(message = "userKey는 필수입니다.")
            @Size(max = 32, message = "userKey는 32자 이하여야 합니다.")
            @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "userKey 형식이 올바르지 않습니다.")
            String userKey,
            @RequestParam(name = "serviceId")
            @Size(min = 1, max = 20, message = "serviceId는 1개 이상 20개 이하여야 합니다.")
            List<@Min(value = 1, message = "serviceId는 1 이상이어야 합니다.") Long> serviceIds
    ) {
        if (userKey == null || userKey.isBlank() || serviceIds == null || serviceIds.isEmpty()) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        adminOperationRateLimitService.checkExpensiveReadLimit(actorKey(authenticatedUser), "dashboard:recommendation-diagnostics");
        log.info("[Admin] dashboard recommendation diagnostics 조회 userKey={} serviceIdCount={}",
                userKey, serviceIds.size());
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

    @GetMapping("/user-profile-standard-code-coverage")
    public ResponseEntity<ApiResponse<AdminUserProfileStandardCodeCoverageResponse>> getUserProfileStandardCodeCoverage() {
        log.info("[Admin] dashboard user profile standard code coverage 조회");
        return ResponseEntity.ok(ApiResponse.success(
                adminDashboardUserProfileService.getUserProfileStandardCodeCoverage()
        ));
    }

    @GetMapping("/attention-feed")
    public ResponseEntity<ApiResponse<AdminDashboardAttentionResponse>> getAttentionFeed() {
        log.info("[Admin] dashboard attention feed 조회");
        return ResponseEntity.ok(ApiResponse.success(
                adminDashboardAttentionService.getAttentionFeed()
        ));
    }

    @GetMapping("/standard-code-effect-observation")
    public ResponseEntity<ApiResponse<AdminStandardCodeEffectObservationResponse>> getStandardCodeEffectObservation() {
        log.info("[Admin] dashboard standard code effect observation 조회");
        return ResponseEntity.ok(ApiResponse.success(
                adminDashboardStandardCodeObservationService.getLatestObservation()
        ));
    }

    @GetMapping("/wrapper-observation")
    public ResponseEntity<ApiResponse<AdminWrapperObservationResponse>> getWrapperObservation() {
        log.info("[Admin] dashboard wrapper observation 조회");
        return ResponseEntity.ok(ApiResponse.success(
                adminDashboardWrapperObservationService.getLatestObservation()
        ));
    }

    @GetMapping("/policy-error-reports")
    public ResponseEntity<ApiResponse<AdminPolicyErrorReportResponse>> getPolicyErrorReports(
            @RequestParam(name = "limit", required = false)
            @Min(value = 1, message = "limit는 1 이상이어야 합니다.")
            @Max(value = 20, message = "limit는 20 이하여야 합니다.")
            Integer limit,
            @RequestParam(name = "status", required = false) String status
    ) {
        validateDashboardLimit(limit);
        AdminQueueStatusFilter statusFilter = AdminQueueStatusFilter.fromNullable(status);
        log.info("[Admin] dashboard policy error reports 조회 limit={} status={}", limit, statusFilter);
        return ResponseEntity.ok(ApiResponse.success(
                adminPolicyErrorReportService.getRecentReports(limit, statusFilter)
        ));
    }

    @GetMapping("/policy-duplicate-groups")
    public ResponseEntity<ApiResponse<AdminPolicyDuplicateGroupResponse>> getPolicyDuplicateGroups(
            @RequestParam(name = "limit", required = false)
            @Min(value = 1, message = "limit는 1 이상이어야 합니다.")
            @Max(value = 20, message = "limit는 20 이하여야 합니다.")
            Integer limit,
            @RequestParam(name = "status", required = false) String status
    ) {
        validateDashboardLimit(limit);
        AdminQueueStatusFilter statusFilter = AdminQueueStatusFilter.fromNullable(status);
        log.info("[Admin] dashboard policy duplicate groups 조회 limit={} status={}", limit, statusFilter);
        return ResponseEntity.ok(ApiResponse.success(
                adminPolicyDuplicateGroupService.getRecentGroups(limit, statusFilter)
        ));
    }

    @GetMapping("/policy-link-reviews")
    public ResponseEntity<ApiResponse<AdminPolicyLinkReviewResponse>> getPolicyLinkReviews(
            @RequestParam(name = "limit", required = false)
            @Min(value = 1, message = "limit는 1 이상이어야 합니다.")
            @Max(value = 20, message = "limit는 20 이하여야 합니다.")
            Integer limit,
            @RequestParam(name = "status", required = false) String status
    ) {
        validateDashboardLimit(limit);
        AdminQueueStatusFilter statusFilter = AdminQueueStatusFilter.fromNullable(status);
        log.info("[Admin] dashboard policy link reviews 조회 limit={} status={}", limit, statusFilter);
        return ResponseEntity.ok(ApiResponse.success(
                adminPolicyLinkReviewService.getRecentReviews(limit, statusFilter)
        ));
    }

    @GetMapping("/region-options")
    public ResponseEntity<ApiResponse<AdminRegionOptionResponse>> getRegionOptions() {
        log.info("[Admin] dashboard region options 조회");
        return ResponseEntity.ok(ApiResponse.success(
                adminPolicyRegionCorrectionService.getRegionOptions()
        ));
    }

    @GetMapping("/policy-region-corrections")
    public ResponseEntity<ApiResponse<AdminPolicyRegionCorrectionListResponse>> getPolicyRegionCorrections(
            @RequestParam(name = "limit", required = false)
            @Min(value = 1, message = "limit는 1 이상이어야 합니다.")
            @Max(value = 100, message = "limit는 100 이하여야 합니다.")
            Integer limit,
            @RequestParam(name = "activeOnly", required = false, defaultValue = "true") boolean activeOnly
    ) {
        log.info("[Admin] dashboard policy region corrections 조회 limit={} activeOnly={}", limit, activeOnly);
        return ResponseEntity.ok(ApiResponse.success(
                adminPolicyRegionCorrectionService.getCorrections(limit, activeOnly)
        ));
    }

    @GetMapping("/policy-field-corrections")
    public ResponseEntity<ApiResponse<AdminPolicyFieldCorrectionListResponse>> getPolicyFieldCorrections(
            @RequestParam(name = "limit", required = false)
            @Min(value = 1, message = "limit는 1 이상이어야 합니다.")
            @Max(value = 100, message = "limit는 100 이하여야 합니다.")
            Integer limit
    ) {
        log.info("[Admin] dashboard policy field corrections 조회 limit={}", limit);
        return ResponseEntity.ok(ApiResponse.success(
                adminPolicyFieldCorrectionService.getCorrections(limit)
        ));
    }

    @PostMapping("/policy-error-reports/{reportId}/review")
    public ResponseEntity<ApiResponse<AdminReviewActionResponse>> reviewPolicyErrorReport(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @org.springframework.web.bind.annotation.PathVariable Long reportId,
            @Valid @RequestBody(required = false) AdminReviewActionRequest request
    ) {
        adminOperationRateLimitService.checkMutationLimit(actorKey(authenticatedUser), "dashboard:policy-error-report-review");
        log.info("[Admin] dashboard policy error report review reportId={} actorUserKey={}",
                reportId, authenticatedUser != null ? authenticatedUser.userKey() : null);
        return ResponseEntity.ok(ApiResponse.success(
                adminPolicyErrorReportService.markReviewed(
                        reportId,
                        authenticatedUser != null ? authenticatedUser.userKey() : null,
                        request != null ? request.reviewNote() : null
                )
        ));
    }

    @PostMapping("/policy-duplicate-groups/review")
    public ResponseEntity<ApiResponse<AdminReviewActionResponse>> reviewPolicyDuplicateGroup(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @Valid @RequestBody AdminPolicyDuplicateGroupReviewRequest request
    ) {
        adminOperationRateLimitService.checkMutationLimit(actorKey(authenticatedUser), "dashboard:policy-duplicate-group-review");
        log.info("[Admin] dashboard policy duplicate group review sourceType={} hostOrgKey={} actorUserKey={}",
                request.sourceType(), request.hostOrgKey(), authenticatedUser != null ? authenticatedUser.userKey() : null);
        return ResponseEntity.ok(ApiResponse.success(
                adminPolicyDuplicateGroupService.markReviewed(
                        request,
                        authenticatedUser != null ? authenticatedUser.userKey() : null
                )
        ));
    }

    @PostMapping("/policy-link-reviews/{serviceId}/review")
    public ResponseEntity<ApiResponse<AdminReviewActionResponse>> reviewPolicyLink(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @org.springframework.web.bind.annotation.PathVariable Long serviceId,
            @Valid @RequestBody(required = false) AdminReviewActionRequest request
    ) {
        adminOperationRateLimitService.checkMutationLimit(actorKey(authenticatedUser), "dashboard:policy-link-review");
        log.info("[Admin] dashboard policy link review serviceId={} actorUserKey={}",
                serviceId, authenticatedUser != null ? authenticatedUser.userKey() : null);
        return ResponseEntity.ok(ApiResponse.success(
                adminPolicyLinkReviewService.markReviewed(
                        serviceId,
                        authenticatedUser != null ? authenticatedUser.userKey() : null,
                        request != null ? request.reviewNote() : null
                )
        ));
    }

    @PostMapping("/policy-region-corrections")
    public ResponseEntity<ApiResponse<AdminPolicyRegionCorrectionResponse>> applyPolicyRegionCorrection(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @Valid @RequestBody AdminPolicyRegionCorrectionRequest request
    ) {
        adminOperationRateLimitService.checkMutationLimit(actorKey(authenticatedUser), "dashboard:policy-region-correction");
        log.info("[Admin] dashboard policy region correction policyId={} reportId={} actorUserKey={}",
                request != null ? request.policyId() : null,
                request != null ? request.reportId() : null,
                authenticatedUser != null ? authenticatedUser.userKey() : null);
        return ResponseEntity.ok(ApiResponse.success(
                adminPolicyRegionCorrectionService.applyCorrection(
                        request,
                        authenticatedUser != null ? authenticatedUser.userKey() : null
                )
        ));
    }

    @PostMapping("/policy-region-corrections/{correctionId}/revert")
    public ResponseEntity<ApiResponse<AdminPolicyRegionCorrectionResponse>> revertPolicyRegionCorrection(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @org.springframework.web.bind.annotation.PathVariable Long correctionId,
            @Valid @RequestBody(required = false) AdminPolicyRegionCorrectionRevertRequest request
    ) {
        adminOperationRateLimitService.checkMutationLimit(actorKey(authenticatedUser), "dashboard:policy-region-correction-revert");
        log.info("[Admin] dashboard policy region correction revert correctionId={} actorUserKey={}",
                correctionId, authenticatedUser != null ? authenticatedUser.userKey() : null);
        return ResponseEntity.ok(ApiResponse.success(
                adminPolicyRegionCorrectionService.revertCorrection(
                        correctionId,
                        request,
                        authenticatedUser != null ? authenticatedUser.userKey() : null
                )
        ));
    }

    @PostMapping("/policy-region-audit/run")
    public ResponseEntity<ApiResponse<AdminPolicyRegionAuditResponse>> runPolicyRegionAudit(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @RequestParam(name = "limit", required = false)
            @Min(value = 1, message = "limit는 1 이상이어야 합니다.")
            @Max(value = 50000, message = "limit는 50000 이하여야 합니다.")
            Integer limit
    ) {
        adminOperationRateLimitService.checkMutationLimit(actorKey(authenticatedUser), "dashboard:policy-region-audit");
        log.info("[Admin] dashboard policy region audit 실행 limit={} actorUserKey={}",
                limit, authenticatedUser != null ? authenticatedUser.userKey() : null);
        return ResponseEntity.ok(ApiResponse.success(
                adminPolicyRegionAuditService.runRegionAudit(limit)
        ));
    }

    @PostMapping("/policy-field-corrections")
    public ResponseEntity<ApiResponse<AdminPolicyFieldCorrectionResponse>> applyPolicyFieldCorrection(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @Valid @RequestBody AdminPolicyFieldCorrectionRequest request
    ) {
        adminOperationRateLimitService.checkMutationLimit(actorKey(authenticatedUser), "dashboard:policy-field-correction");
        log.info("[Admin] dashboard policy field correction policyId={} reportId={} type={} actorUserKey={}",
                request != null ? request.policyId() : null,
                request != null ? request.reportId() : null,
                request != null ? request.correctionType() : null,
                authenticatedUser != null ? authenticatedUser.userKey() : null);
        return ResponseEntity.ok(ApiResponse.success(
                adminPolicyFieldCorrectionService.applyCorrection(
                        request,
                        authenticatedUser != null ? authenticatedUser.userKey() : null
                )
        ));
    }

    @GetMapping("/support-inquiries")
    public ResponseEntity<ApiResponse<AdminSupportInquiryResponse>> getSupportInquiries(
            @RequestParam(name = "limit", required = false)
            @Min(value = 1, message = "limit는 1 이상이어야 합니다.")
            @Max(value = 20, message = "limit는 20 이하여야 합니다.")
            Integer limit,
            @RequestParam(name = "status", required = false) String status
    ) {
        validateDashboardLimit(limit);
        AdminQueueStatusFilter statusFilter = AdminQueueStatusFilter.fromNullable(status);
        log.info("[Admin] dashboard support inquiries 조회 limit={} status={}", limit, statusFilter);
        return ResponseEntity.ok(ApiResponse.success(
                adminSupportInquiryService.getRecentInquiries(limit, statusFilter)
        ));
    }

    @PostMapping("/support-inquiries/{inquiryId}/review")
    public ResponseEntity<ApiResponse<AdminReviewActionResponse>> reviewSupportInquiry(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @org.springframework.web.bind.annotation.PathVariable Long inquiryId,
            @Valid @RequestBody(required = false) AdminReviewActionRequest request
    ) {
        adminOperationRateLimitService.checkMutationLimit(actorKey(authenticatedUser), "dashboard:support-inquiry-review");
        log.info("[Admin] dashboard support inquiry review inquiryId={} actorUserKey={}",
                inquiryId, authenticatedUser != null ? authenticatedUser.userKey() : null);
        return ResponseEntity.ok(ApiResponse.success(
                adminSupportInquiryService.markReviewed(
                        inquiryId,
                        authenticatedUser != null ? authenticatedUser.userKey() : null,
                        request != null ? request.reviewNote() : null
                )
        ));
    }

    @PostMapping("/notification-backlog/hide-stale")
    public ResponseEntity<ApiResponse<AdminNotificationStaleHideResponse>> hideStaleNotificationBacklog(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @Valid @RequestBody AdminNotificationStaleHideRequest request
    ) {
        adminOperationRateLimitService.checkMutationLimit(actorKey(authenticatedUser), "dashboard:notification-backlog-hide-stale");
        log.info("[Admin] dashboard notification backlog hide kind={} olderThanDays={} actorUserKey={}",
                request.kind(), request.olderThanDays(),
                authenticatedUser != null ? authenticatedUser.userKey() : null);
        return ResponseEntity.ok(ApiResponse.success(
                adminNotificationBacklogService.hideStaleAlerts(request)
        ));
    }

    @GetMapping("/notification-stale-targets")
    public ResponseEntity<ApiResponse<AdminNotificationStaleTargetResponse>> getNotificationStaleTargets(
            @RequestParam(name = "limit", required = false)
            @Min(value = 1, message = "limit는 1 이상이어야 합니다.")
            @Max(value = 20, message = "limit는 20 이하여야 합니다.")
            Integer limit,
            @RequestParam(name = "olderThanDays", required = false)
            @Min(value = 1, message = "olderThanDays는 1 이상이어야 합니다.")
            @Max(value = 365, message = "olderThanDays는 365 이하여야 합니다.")
            Integer olderThanDays
    ) {
        validateDashboardLimit(limit);
        log.info("[Admin] dashboard notification stale targets 조회 limit={} olderThanDays={}", limit, olderThanDays);
        return ResponseEntity.ok(ApiResponse.success(
                adminNotificationStaleTargetService.getRecentTargets(limit, olderThanDays)
        ));
    }

    @PostMapping("/recommendation-review-gate/promotion-approval-record")
    public ResponseEntity<ApiResponse<AdminRecommendationReviewGatePromotionApprovalRecordResponse>>
    recordRecommendationReviewGatePromotionApproval(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @Valid @RequestBody(required = false) AdminRecommendationReviewGatePromotionApprovalRecordRequest request
    ) {
        String actorUserKey = authenticatedUser != null ? authenticatedUser.userKey() : null;
        String approvalNote = request != null ? request.approvalNote() : null;
        adminOperationRateLimitService.checkMutationLimit(actorUserKey, "dashboard:recommendation-promotion-approval-record");
        log.info("[Admin] recommendation review gate promotion approval record upsert actorUserKey={}", actorUserKey);
        return ResponseEntity.ok(ApiResponse.success(
                adminRecommendationReviewGatePromotionApprovalRecordService.recordApproval(actorUserKey, approvalNote)
        ));
    }

    @DeleteMapping("/recommendation-review-gate/promotion-approval-record")
    public ResponseEntity<ApiResponse<AdminRecommendationReviewGatePromotionApprovalRecordClearResponse>>
    clearRecommendationReviewGatePromotionApproval(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser
    ) {
        adminOperationRateLimitService.checkMutationLimit(
                actorKey(authenticatedUser),
                "dashboard:recommendation-promotion-approval-clear"
        );
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

    private String actorKey(AuthenticatedUser authenticatedUser) {
        return authenticatedUser != null ? authenticatedUser.userKey() : "unknown";
    }
}
