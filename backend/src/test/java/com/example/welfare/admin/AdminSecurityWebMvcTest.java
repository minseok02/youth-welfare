package com.example.welfare.admin;

import com.example.welfare.admin.dashboard.controller.AdminDashboardController;
import com.example.welfare.admin.dashboard.dto.AdminCollectFailureResponse;
import com.example.welfare.admin.dashboard.dto.AdminDashboardAttentionResponse;
import com.example.welfare.admin.dashboard.dto.AdminRecommendationCandidateDiagnosticResponse;
import com.example.welfare.admin.dashboard.dto.AdminRecommendationBreakdownResponse;
import com.example.welfare.admin.dashboard.dto.AdminPolicyErrorReportResponse;
import com.example.welfare.admin.dashboard.dto.AdminPolicyDuplicateGroupResponse;
import com.example.welfare.admin.dashboard.dto.AdminPolicyDuplicateGroupReviewRequest;
import com.example.welfare.admin.dashboard.dto.AdminPolicyLinkReviewResponse;
import com.example.welfare.admin.dashboard.dto.AdminQueueStatusFilter;
import com.example.welfare.admin.dashboard.dto.AdminReviewActionResponse;
import com.example.welfare.admin.dashboard.dto.AdminNotificationStaleTargetResponse;
import com.example.welfare.admin.dashboard.dto.AdminRecommendationReviewGatePromotionApprovalRecordClearResponse;
import com.example.welfare.admin.dashboard.dto.AdminRecommendationReviewGatePromotionApprovalRecordResponse;
import com.example.welfare.admin.dashboard.dto.AdminSearchFailureResponse;
import com.example.welfare.admin.dashboard.dto.AdminStandardCodeEffectObservationResponse;
import com.example.welfare.admin.dashboard.dto.AdminSupportInquiryResponse;
import com.example.welfare.admin.dashboard.dto.AdminDashboardResponse;
import com.example.welfare.admin.dashboard.dto.AdminNotificationStaleHideResponse;
import com.example.welfare.admin.dashboard.dto.AdminUserProfileStandardCodeCoverageResponse;
import com.example.welfare.admin.dashboard.dto.AdminWrapperObservationResponse;
import com.example.welfare.admin.service.AdminOperationRateLimitService;
import com.example.welfare.collect.dto.InvertedAgeBackfillResponse;
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
import com.example.welfare.admin.dashboard.service.AdminNotificationAttemptSummaryService;
import com.example.welfare.admin.dashboard.service.AdminPolicyErrorReportService;
import com.example.welfare.admin.dashboard.service.AdminPolicyFieldCorrectionService;
import com.example.welfare.admin.dashboard.service.AdminPolicyDuplicateGroupService;
import com.example.welfare.admin.dashboard.service.AdminPolicyLinkReviewService;
import com.example.welfare.admin.dashboard.service.AdminPolicyRegionAuditService;
import com.example.welfare.admin.dashboard.service.AdminPolicyRegionCorrectionService;
import com.example.welfare.admin.dashboard.service.AdminSupportInquiryService;
import com.example.welfare.collect.controller.CollectAdminController;
import com.example.welfare.collect.normalization.NormalizedPolicySidecarBackfillService;
import com.example.welfare.collect.dto.AsyncCollectStatusResponse;
import com.example.welfare.collect.service.CollectAdminService;
import com.example.welfare.collect.service.CollectAsyncJobService;
import com.example.welfare.collect.service.CollectBatchService;
import com.example.welfare.collect.service.CollectBatchRunResult;
import com.example.welfare.collect.service.CollectResult;
import com.example.welfare.collect.service.CollectSource;
import com.example.welfare.collect.service.YouthDetailCollectService;
import com.example.welfare.global.auth.AuthenticatedUser;
import com.example.welfare.global.config.JacksonConfig;
import com.example.welfare.global.config.SecurityConfig;
import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.example.welfare.global.util.JwtUtil;
import com.example.welfare.global.web.ClientFingerprintService;
import com.example.welfare.policy.controller.PolicyAdminController;
import com.example.welfare.policy.dto.PolicyCategoryAuditResponse;
import com.example.welfare.policy.dto.PolicyEmbeddingRefreshResponse;
import com.example.welfare.policy.dto.PolicyReferenceUrlBackfillResponse;
import com.example.welfare.policy.dto.PolicyStatusSyncResponse;
import com.example.welfare.policy.dto.PolicyRetrievalEvaluationCompareResponse;
import com.example.welfare.policy.dto.PolicyRetrievalEvaluationResponse;
import com.example.welfare.policy.dto.PolicyRetrievalQualityGateResponse;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.service.PolicyCategoryAuditService;
import com.example.welfare.policy.service.PolicyEmbeddingAdminService;
import com.example.welfare.policy.service.PolicyReferenceUrlAdminService;
import com.example.welfare.policy.service.PolicyRetrievalEvaluationExportService;
import com.example.welfare.policy.service.PolicyRetrievalEvaluationService;
import com.example.welfare.policy.service.PolicyRetrievalQualityGateService;
import com.example.welfare.policy.service.SearchYouthRelevanceService;
import com.example.welfare.collect.service.StatusUpdateService;
import com.example.welfare.user.controller.UserAdminController;
import com.example.welfare.user.dto.response.UserMetadataUserKeyBackfillResponse;
import com.example.welfare.user.dto.response.UserPiiBackfillResponse;
import com.example.welfare.user.dto.response.UserPiiEncryptionRotationResponse;
import com.example.welfare.user.dto.response.UserPiiSyncReplayResponse;
import com.example.welfare.user.dto.response.UserPiiSyncStatusResponse;
import com.example.welfare.user.service.UserMetadataUserKeyBackfillService;
import com.example.welfare.user.service.UserPiiBackfillService;
import com.example.welfare.user.service.UserKeyLookupService;
import com.example.welfare.user.service.UserPiiSyncReplayService;
import com.example.welfare.user.service.UserPiiSyncStatusService;
import com.example.welfare.user.service.AdminAccessAuthorityService;
import com.example.welfare.user.service.UserSessionRevocationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

@WebMvcTest(controllers = {
        CollectAdminController.class,
        PolicyAdminController.class,
        UserAdminController.class,
        AdminDashboardController.class
})
@Import({SecurityConfig.class, JacksonConfig.class})
class AdminSecurityWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CollectBatchService collectBatchService;
    @MockitoBean
    private CollectAdminService collectAdminService;
    @MockitoBean
    private CollectAsyncJobService collectAsyncJobService;
    @MockitoBean
    private NormalizedPolicySidecarBackfillService normalizedPolicySidecarBackfillService;
    @MockitoBean
    private YouthDetailCollectService youthDetailCollectService;
    @MockitoBean
    private SearchYouthRelevanceService searchYouthRelevanceService;
    @MockitoBean
    private PolicyEmbeddingAdminService policyEmbeddingAdminService;
    @MockitoBean
    private PolicyReferenceUrlAdminService policyReferenceUrlAdminService;
    @MockitoBean
    private StatusUpdateService statusUpdateService;
    @MockitoBean
    private PolicyRetrievalEvaluationService policyRetrievalEvaluationService;
    @MockitoBean
    private PolicyRetrievalEvaluationExportService policyRetrievalEvaluationExportService;
    @MockitoBean
    private PolicyRetrievalQualityGateService policyRetrievalQualityGateService;
    @MockitoBean
    private PolicyCategoryAuditService policyCategoryAuditService;
    @MockitoBean
    private UserMetadataUserKeyBackfillService userMetadataUserKeyBackfillService;
    @MockitoBean
    private UserPiiBackfillService userPiiBackfillService;
    @MockitoBean
    private UserPiiSyncReplayService userPiiSyncReplayService;
    @MockitoBean
    private UserPiiSyncStatusService userPiiSyncStatusService;
    @MockitoBean
    private UserSessionRevocationService userSessionRevocationService;
    @MockitoBean
    private ClientFingerprintService clientFingerprintService;
    @MockitoBean
    private UserKeyLookupService userKeyLookupService;
    @MockitoBean
    private AdminDashboardSummaryService adminDashboardSummaryService;
    @MockitoBean
    private AdminDashboardSearchService adminDashboardSearchService;
    @MockitoBean
    private AdminDashboardRecommendationService adminDashboardRecommendationService;
    @MockitoBean
    private AdminDashboardRecommendationDiagnosticService adminDashboardRecommendationDiagnosticService;
    @MockitoBean
    private AdminRecommendationReviewGatePromotionApprovalRecordService adminRecommendationReviewGatePromotionApprovalRecordService;
    @MockitoBean
    private AdminDashboardCollectService adminDashboardCollectService;
    @MockitoBean
    private AdminDashboardAttentionService adminDashboardAttentionService;
    @MockitoBean
    private AdminDashboardUserProfileService adminDashboardUserProfileService;
    @MockitoBean
    private AdminDashboardStandardCodeObservationService adminDashboardStandardCodeObservationService;
    @MockitoBean
    private AdminDashboardWrapperObservationService adminDashboardWrapperObservationService;
    @MockitoBean
    private AdminNotificationBacklogService adminNotificationBacklogService;
    @MockitoBean
    private AdminNotificationStaleTargetService adminNotificationStaleTargetService;
    @MockitoBean
    private AdminNotificationAttemptSummaryService adminNotificationAttemptSummaryService;
    @MockitoBean
    private AdminPolicyErrorReportService adminPolicyErrorReportService;
    @MockitoBean
    private AdminPolicyFieldCorrectionService adminPolicyFieldCorrectionService;
    @MockitoBean
    private AdminPolicyDuplicateGroupService adminPolicyDuplicateGroupService;
    @MockitoBean
    private AdminPolicyLinkReviewService adminPolicyLinkReviewService;
    @MockitoBean
    private AdminPolicyRegionAuditService adminPolicyRegionAuditService;
    @MockitoBean
    private AdminPolicyRegionCorrectionService adminPolicyRegionCorrectionService;
    @MockitoBean
    private AdminSupportInquiryService adminSupportInquiryService;
    @MockitoBean
    private JwtUtil jwtUtil;
    @MockitoBean
    private AdminAccessAuthorityService adminAccessAuthorityService;
    @MockitoBean
    private AdminOperationRateLimitService adminOperationRateLimitService;
    @MockitoBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    @DisplayName("관리자 API는 인증 없이 호출하면 401을 반환한다")
    void adminEndpointRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/admin/collect/youth"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("A006"));
    }

    @Test
    @DisplayName("Swagger 문서 경로는 인증 없이 호출하면 401을 반환한다")
    void swaggerEndpointRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("A006"));
    }

    @Test
    @DisplayName("일반 사용자 토큰으로 관리자 API를 호출하면 403을 반환한다")
    void adminEndpointRejectsNonAdminUser() throws Exception {
        mockAuthenticatedToken("user-token", List.of(new SimpleGrantedAuthority("ROLE_USER")));

        mockMvc.perform(post("/api/admin/collect/youth")
                        .header("Authorization", "Bearer user-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("C003"));
    }

    @Test
    @DisplayName("관리자 토큰으로 관리자 API를 호출하면 수집 서비스를 실행한다")
    void adminEndpointAllowsAdminUser() throws Exception {
        mockAuthenticatedToken("admin-token", List.of(
                new SimpleGrantedAuthority("ROLE_USER"),
                new SimpleGrantedAuthority("ROLE_ADMIN")
        ));
        doNothing().when(collectAdminService).collect(CollectSource.YOUTH);

        mockMvc.perform(post("/api/admin/collect/youth")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value("온통청년 수집 완료"));

        then(collectAdminService).should().collect(CollectSource.YOUTH);
    }

    @Test
    @DisplayName("관리자 collect sourceId override는 제어문자와 query 조작 문자를 거부한다")
    void collectSourceRejectsUnsafeSourceIdOverride() throws Exception {
        mockAuthenticatedToken("admin-token", List.of(
                new SimpleGrantedAuthority("ROLE_USER"),
                new SimpleGrantedAuthority("ROLE_ADMIN")
        ));

        mockMvc.perform(post("/api/admin/collect/gov24-detail")
                        .queryParam("sourceId", "GOV24-1%0d%0aX-Injected:1")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("C001"));

        then(collectAdminService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("관리자 토큰으로 정책 상태 sync API를 호출할 수 있다")
    void adminEndpointAllowsPolicyStatusSync() throws Exception {
        mockAuthenticatedToken("admin-token", List.of(
                new SimpleGrantedAuthority("ROLE_USER"),
                new SimpleGrantedAuthority("ROLE_ADMIN")
        ));
        given(statusUpdateService.runStatusSync())
                .willReturn(new StatusUpdateService.StatusSyncResult(7, 2, 3, true));

        mockMvc.perform(post("/api/admin/policies/status-sync")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.closedCount").value(7))
                .andExpect(jsonPath("$.data.activatedCount").value(2))
                .andExpect(jsonPath("$.data.reopenedCount").value(3))
                .andExpect(jsonPath("$.data.clusterAiCacheCleanupExecuted").value(true));
    }

    @Test
    @DisplayName("관리자 토큰으로 정책 오류 제보 대시보드 API를 호출할 수 있다")
    void adminEndpointAllowsPolicyErrorReportsDashboard() throws Exception {
        mockAuthenticatedToken("admin-token", List.of(
                new SimpleGrantedAuthority("ROLE_USER"),
                new SimpleGrantedAuthority("ROLE_ADMIN")
        ));
        given(adminPolicyErrorReportService.getRecentReports(5, AdminQueueStatusFilter.OPEN))
                .willReturn(new AdminPolicyErrorReportResponse(2, 1, List.of()));

        mockMvc.perform(get("/api/admin/dashboard/policy-error-reports")
                        .param("limit", "5")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.openCount").value(2));
    }

    @Test
    @DisplayName("관리자 토큰으로 정책 중복 review queue API를 호출할 수 있다")
    void adminEndpointAllowsPolicyDuplicateGroupsDashboard() throws Exception {
        mockAuthenticatedToken("admin-token", List.of(
                new SimpleGrantedAuthority("ROLE_USER"),
                new SimpleGrantedAuthority("ROLE_ADMIN")
        ));
        given(adminPolicyDuplicateGroupService.getRecentGroups(5, AdminQueueStatusFilter.OPEN))
                .willReturn(new AdminPolicyDuplicateGroupResponse(3, 1, 12, List.of()));

        mockMvc.perform(get("/api/admin/dashboard/policy-duplicate-groups")
                        .param("limit", "5")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.openGroupCount").value(3));
    }

    @Test
    @DisplayName("관리자 토큰으로 정책 링크 review queue API를 호출할 수 있다")
    void adminEndpointAllowsPolicyLinkReviewsDashboard() throws Exception {
        mockAuthenticatedToken("admin-token", List.of(
                new SimpleGrantedAuthority("ROLE_USER"),
                new SimpleGrantedAuthority("ROLE_ADMIN")
        ));
        given(adminPolicyLinkReviewService.getRecentReviews(5, AdminQueueStatusFilter.OPEN))
                .willReturn(new AdminPolicyLinkReviewResponse(4, 2, List.of()));

        mockMvc.perform(get("/api/admin/dashboard/policy-link-reviews")
                        .param("limit", "5")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.openCount").value(4));
    }

    @Test
    @DisplayName("관리자 토큰으로 stale notification target 목록을 조회할 수 있다")
    void adminEndpointAllowsNotificationStaleTargetsDashboard() throws Exception {
        mockAuthenticatedToken("admin-token", List.of(
                new SimpleGrantedAuthority("ROLE_USER"),
                new SimpleGrantedAuthority("ROLE_ADMIN")
        ));
        given(adminNotificationStaleTargetService.getRecentTargets(5, 14))
                .willReturn(new AdminNotificationStaleTargetResponse(
                        14,
                        9,
                        3,
                        List.of(new AdminNotificationStaleTargetResponse.Item(
                                "DEADLINE_REMINDER",
                                "북마크한 정책 마감이 임박했어요",
                                "/policies/2622",
                                5,
                                5,
                                LocalDateTime.of(2026, 5, 16, 5, 55, 31),
                                LocalDateTime.of(2026, 5, 17, 4, 13, 16)
                        ))
                ));

        mockMvc.perform(get("/api/admin/dashboard/notification-stale-targets")
                        .param("limit", "5")
                        .param("olderThanDays", "14")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.staleRowCount").value(9))
                .andExpect(jsonPath("$.data.staleGroupCount").value(3))
                .andExpect(jsonPath("$.data.recentTargets[0].deeplinkUrl").value("/policies/2622"));
    }

    @Test
    @DisplayName("관리자 토큰으로 정책 중복 묶음을 처리완료 할 수 있다")
    void adminEndpointAllowsPolicyDuplicateGroupReview() throws Exception {
        mockAuthenticatedToken("admin-token", List.of(
                new SimpleGrantedAuthority("ROLE_USER"),
                new SimpleGrantedAuthority("ROLE_ADMIN")
        ));
        given(adminPolicyDuplicateGroupService.markReviewed(
                new AdminPolicyDuplicateGroupReviewRequest("YOUTH", "청년문화예술패스", "", null, "중앙 중복 묶음 확인"),
                "user-key-1"
        )).willReturn(new AdminReviewActionResponse(77L, "REVIEWED", "중앙 중복 묶음 확인", "user-key-1", LocalDateTime.now()));

        mockMvc.perform(post("/api/admin/dashboard/policy-duplicate-groups/review")
                        .header("Authorization", "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sourceType": "YOUTH",
                                  "title": "청년문화예술패스",
                                  "hostOrgKey": "",
                                  "reviewNote": "중앙 중복 묶음 확인"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("REVIEWED"));
    }

    @Test
    @DisplayName("정책 중복 묶음 review API는 긴 reviewNote에 400을 반환한다")
    void adminEndpointRejectsTooLongPolicyDuplicateGroupReviewNote() throws Exception {
        mockAuthenticatedToken("admin-token", List.of(
                new SimpleGrantedAuthority("ROLE_USER"),
                new SimpleGrantedAuthority("ROLE_ADMIN")
        ));

        mockMvc.perform(post("/api/admin/dashboard/policy-duplicate-groups/review")
                        .header("Authorization", "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sourceType": "YOUTH",
                                  "title": "청년문화예술패스",
                                  "reviewNote": "%s"
                                }
                                """.formatted("x".repeat(1001))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("C001"));
    }

    @Test
    @DisplayName("관리자 토큰으로 서비스 문의 대시보드 API를 호출할 수 있다")
    void adminEndpointAllowsSupportInquiriesDashboard() throws Exception {
        mockAuthenticatedToken("admin-token", List.of(
                new SimpleGrantedAuthority("ROLE_USER"),
                new SimpleGrantedAuthority("ROLE_ADMIN")
        ));
        given(adminSupportInquiryService.getRecentInquiries(5, AdminQueueStatusFilter.OPEN))
                .willReturn(new AdminSupportInquiryResponse(3, 1, List.of()));

        mockMvc.perform(get("/api/admin/dashboard/support-inquiries")
                        .param("limit", "5")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.openCount").value(3));
    }

    @Test
    @DisplayName("관리자 토큰으로 정책 링크 review API를 호출하면 review 상태를 기록한다")
    void adminEndpointAllowsPolicyLinkReview() throws Exception {
        mockAuthenticatedToken("admin-token", List.of(
                new SimpleGrantedAuthority("ROLE_USER"),
                new SimpleGrantedAuthority("ROLE_ADMIN")
        ));
        given(adminPolicyLinkReviewService.markReviewed(91L, "user-key-1", "대표 링크 없음 확인"))
                .willReturn(new AdminReviewActionResponse(
                        17L,
                        "REVIEWED",
                        "대표 링크 없음 확인",
                        "user-key-1",
                        LocalDateTime.of(2026, 6, 4, 11, 12)
                ));

        mockMvc.perform(post("/api/admin/dashboard/policy-link-reviews/91/review")
                        .header("Authorization", "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reviewNote":"대표 링크 없음 확인"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(17))
                .andExpect(jsonPath("$.data.status").value("REVIEWED"))
                .andExpect(jsonPath("$.data.reviewedByUserKey").value("user-key-1"));

        then(adminPolicyLinkReviewService).should().markReviewed(91L, "user-key-1", "대표 링크 없음 확인");
    }

    @Test
    @DisplayName("관리자 토큰으로 정책 오류 제보 review API를 호출하면 review 상태를 기록한다")
    void adminEndpointAllowsPolicyErrorReportReview() throws Exception {
        mockAuthenticatedToken("admin-token", List.of(
                new SimpleGrantedAuthority("ROLE_USER"),
                new SimpleGrantedAuthority("ROLE_ADMIN")
        ));
        given(adminPolicyErrorReportService.markReviewed(9L, "user-key-1", "링크 수정 확인"))
                .willReturn(new AdminReviewActionResponse(
                        9L,
                        "REVIEWED",
                        "링크 수정 확인",
                        "user-key-1",
                        LocalDateTime.of(2026, 6, 4, 11, 5)
                ));

        mockMvc.perform(post("/api/admin/dashboard/policy-error-reports/9/review")
                        .header("Authorization", "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reviewNote":"링크 수정 확인"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(9))
                .andExpect(jsonPath("$.data.status").value("REVIEWED"))
                .andExpect(jsonPath("$.data.reviewedByUserKey").value("user-key-1"));

        then(adminPolicyErrorReportService).should().markReviewed(9L, "user-key-1", "링크 수정 확인");
    }

    @Test
    @DisplayName("관리자 토큰으로 서비스 문의 review API를 호출하면 review 상태를 기록한다")
    void adminEndpointAllowsSupportInquiryReview() throws Exception {
        mockAuthenticatedToken("admin-token", List.of(
                new SimpleGrantedAuthority("ROLE_USER"),
                new SimpleGrantedAuthority("ROLE_ADMIN")
        ));
        given(adminSupportInquiryService.markReviewed(21L, "user-key-1", "추천 설정 안내 후 종료"))
                .willReturn(new AdminReviewActionResponse(
                        21L,
                        "REVIEWED",
                        "추천 설정 안내 후 종료",
                        "user-key-1",
                        LocalDateTime.of(2026, 6, 4, 11, 10)
                ));

        mockMvc.perform(post("/api/admin/dashboard/support-inquiries/21/review")
                        .header("Authorization", "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reviewNote":"추천 설정 안내 후 종료"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(21))
                .andExpect(jsonPath("$.data.status").value("REVIEWED"))
                .andExpect(jsonPath("$.data.reviewedByUserKey").value("user-key-1"));

        then(adminSupportInquiryService).should().markReviewed(21L, "user-key-1", "추천 설정 안내 후 종료");
    }

    @Test
    @DisplayName("관리자 토큰으로 inverted age backfill API를 호출하면 backfill service를 실행한다")
    void adminEndpointAllowsInvertedAgeBackfill() throws Exception {
        mockAuthenticatedToken("admin-token", List.of(
                new SimpleGrantedAuthority("ROLE_USER"),
                new SimpleGrantedAuthority("ROLE_ADMIN")
        ));
        given(collectAdminService.backfillInvertedAgeRanges(
                List.of(WelfareService.SourceType.YOUTH),
                25
        )).willReturn(new InvertedAgeBackfillResponse(
                "selected-source-types",
                List.of(WelfareService.SourceType.YOUTH),
                25,
                2,
                1,
                0,
                1,
                0
        ));

        mockMvc.perform(post("/api/admin/collect/inverted-age-backfill")
                        .param("sourceType", "YOUTH")
                        .param("limitPerSource", "25")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.repairedCount").value(1));

        then(collectAdminService).should().backfillInvertedAgeRanges(
                List.of(WelfareService.SourceType.YOUTH),
                25
        );
    }

    @Test
    @DisplayName("관리자 수집 API는 maxCallsPerRun 상한을 넘기면 400을 반환한다")
    void adminEndpointRejectsTooLargeMaxCallsPerRun() throws Exception {
        mockAuthenticatedToken("admin-token", List.of(
                new SimpleGrantedAuthority("ROLE_USER"),
                new SimpleGrantedAuthority("ROLE_ADMIN")
        ));

        mockMvc.perform(post("/api/admin/collect/youth")
                        .param("maxCallsPerRun", "5001")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("C001"));
    }

    @Test
    @DisplayName("관리자 mutation rate limit 초과 시 429와 C005를 반환한다")
    void adminEndpointRejectsMutationRateLimitExceeded() throws Exception {
        mockAuthenticatedToken("admin-token", List.of(
                new SimpleGrantedAuthority("ROLE_USER"),
                new SimpleGrantedAuthority("ROLE_ADMIN")
        ));
        org.mockito.BDDMockito.willThrow(new CustomException(ErrorCode.ADMIN_OPERATION_RATE_LIMIT_EXCEEDED))
                .given(adminOperationRateLimitService)
                .checkMutationLimit("user-key-1", "collect:source");

        mockMvc.perform(post("/api/admin/collect/youth")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("C005"));
    }

    @Test
    @DisplayName("관리자 비동기 수집 API는 인증 없이 호출하면 401을 반환한다")
    void adminAsyncCollectEndpointRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/admin/collect/gov24/async"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("A006"));
    }

    @Test
    @DisplayName("관리자 토큰으로 비동기 수집 API를 호출하면 accepted 응답을 반환한다")
    void adminAsyncCollectEndpointAllowsAdminUser() throws Exception {
        mockAuthenticatedToken("admin-token", List.of(
                new SimpleGrantedAuthority("ROLE_USER"),
                new SimpleGrantedAuthority("ROLE_ADMIN")
        ));
        given(collectAsyncJobService.trigger(CollectSource.GOV24)).willReturn(new AsyncCollectStatusResponse(
                "gov24",
                "GOV24",
                AsyncCollectStatusResponse.AsyncCollectState.QUEUED,
                true,
                "정부24 비동기 수집이 대기열에 등록되었습니다.",
                LocalDateTime.of(2026, 6, 3, 19, 0),
                null,
                null,
                null,
                null,
                null
        ));

        mockMvc.perform(post("/api/admin/collect/gov24/async")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.sourceKey").value("gov24"))
                .andExpect(jsonPath("$.data.state").value("QUEUED"));

        then(collectAsyncJobService).should().trigger(CollectSource.GOV24);
    }

    @Test
    @DisplayName("관리자 토큰으로 비동기 수집 상태 API를 호출하면 상태를 반환한다")
    void adminAsyncCollectStatusEndpointAllowsAdminUser() throws Exception {
        mockAuthenticatedToken("admin-token", List.of(
                new SimpleGrantedAuthority("ROLE_USER"),
                new SimpleGrantedAuthority("ROLE_ADMIN")
        ));
        given(collectAsyncJobService.getStatus(CollectSource.GOV24)).willReturn(new AsyncCollectStatusResponse(
                "gov24",
                "GOV24",
                AsyncCollectStatusResponse.AsyncCollectState.RUNNING,
                true,
                "정부24 비동기 수집이 실행 중입니다.",
                LocalDateTime.of(2026, 6, 3, 19, 0),
                LocalDateTime.of(2026, 6, 3, 19, 1),
                null,
                null,
                null,
                new AsyncCollectStatusResponse.LatestCollectLog(
                        1L,
                        "RUNNING",
                        500,
                        500,
                        0,
                        0,
                        0,
                        LocalDateTime.of(2026, 6, 3, 19, 1),
                        null,
                        "{\"chunkSize\":500}"
                )
        ));

        mockMvc.perform(get("/api/admin/collect/gov24/async-status")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.state").value("RUNNING"))
                .andExpect(jsonPath("$.data.latestLog.metadataJson").value("{\"chunkSize\":500}"));

        then(collectAsyncJobService).should().getStatus(CollectSource.GOV24);
    }

    @Test
    @DisplayName("관리자 inverted age backfill API는 limitPerSource 상한을 넘기면 400을 반환한다")
    void adminEndpointRejectsTooLargeInvertedAgeBackfillLimit() throws Exception {
        mockAuthenticatedToken("admin-token", List.of(
                new SimpleGrantedAuthority("ROLE_USER"),
                new SimpleGrantedAuthority("ROLE_ADMIN")
        ));

        mockMvc.perform(post("/api/admin/collect/inverted-age-backfill")
                        .param("limitPerSource", "1001")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("C001"));
    }

    @Test
    @DisplayName("관리자 토큰으로 recommendation promotion approval record write API를 호출하면 approval record service를 실행한다")
    void adminEndpointAllowsRecommendationPromotionApprovalRecordWrite() throws Exception {
        mockAuthenticatedToken("admin-token", List.of(
                new SimpleGrantedAuthority("ROLE_USER"),
                new SimpleGrantedAuthority("ROLE_ADMIN")
        ));
        given(adminRecommendationReviewGatePromotionApprovalRecordService.recordApproval(
                org.mockito.ArgumentMatchers.eq("user-key-1"),
                org.mockito.ArgumentMatchers.eq("bounded review approved")
        )).willReturn(new AdminRecommendationReviewGatePromotionApprovalRecordResponse(
                "RECENT_WINDOW_BOUNDED_PROMOTION_REVIEW",
                "APPROVED_FOR_BOUNDED_PROMOTION_REVIEW",
                "RECOMMENDATION_REVIEW_GATE_POLICY_PROMOTION",
                "bounded review approved",
                "user-key-1",
                LocalDateTime.of(2026, 5, 20, 10, 0),
                true
        ));

        mockMvc.perform(post("/api/admin/dashboard/recommendation-review-gate/promotion-approval-record")
                        .header("Authorization", "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"approvalNote":"bounded review approved"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.approvalKey").value("RECENT_WINDOW_BOUNDED_PROMOTION_REVIEW"))
                .andExpect(jsonPath("$.data.recorded").value(true));

        then(adminRecommendationReviewGatePromotionApprovalRecordService).should()
                .recordApproval("user-key-1", "bounded review approved");
    }

    @Test
    @DisplayName("관리자 토큰으로 recommendation promotion approval record clear API를 호출하면 approval record service를 실행한다")
    void adminEndpointAllowsRecommendationPromotionApprovalRecordClear() throws Exception {
        mockAuthenticatedToken("admin-token", List.of(
                new SimpleGrantedAuthority("ROLE_USER"),
                new SimpleGrantedAuthority("ROLE_ADMIN")
        ));
        given(adminRecommendationReviewGatePromotionApprovalRecordService.clearApproval())
                .willReturn(new AdminRecommendationReviewGatePromotionApprovalRecordClearResponse(
                        "RECENT_WINDOW_BOUNDED_PROMOTION_REVIEW",
                        true
                ));

        mockMvc.perform(delete("/api/admin/dashboard/recommendation-review-gate/promotion-approval-record")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.approvalKey").value("RECENT_WINDOW_BOUNDED_PROMOTION_REVIEW"))
                .andExpect(jsonPath("$.data.cleared").value(true));

        then(adminRecommendationReviewGatePromotionApprovalRecordService).should().clearApproval();
    }

    @Test
    @DisplayName("관리자 토큰으로 표준코드 coverage API를 호출하면 coverage service를 실행한다")
    void adminEndpointAllowsUserProfileStandardCodeCoverage() throws Exception {
        mockAuthenticatedToken("admin-token", List.of(
                new SimpleGrantedAuthority("ROLE_USER"),
                new SimpleGrantedAuthority("ROLE_ADMIN")
        ));
        given(adminDashboardUserProfileService.getUserProfileStandardCodeCoverage())
                .willReturn(new AdminUserProfileStandardCodeCoverageResponse(
                        LocalDateTime.of(2026, 6, 3, 12, 0),
                        796,
                        796,
                        0,
                        7,
                        0,
                        789,
                        7,
                        3,
                        0,
                        0,
                        7,
                        0,
                        789,
                        0,
                        0,
                        0,
                        0
                ));

        mockMvc.perform(get("/api/admin/dashboard/user-profile-standard-code-coverage")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalUsers").value(796))
                .andExpect(jsonPath("$.data.usersMissingAllStandardCodes").value(789))
                .andExpect(jsonPath("$.data.housingTypeFilled").value(3));

        then(adminDashboardUserProfileService).should().getUserProfileStandardCodeCoverage();
    }

    @Test
    @DisplayName("관리자 토큰으로 attention feed API를 호출하면 attention service를 실행한다")
    void adminEndpointAllowsAttentionFeed() throws Exception {
        mockAuthenticatedToken("admin-token", List.of(
                new SimpleGrantedAuthority("ROLE_USER"),
                new SimpleGrantedAuthority("ROLE_ADMIN")
        ));
        given(adminDashboardAttentionService.getAttentionFeed())
                .willReturn(new AdminDashboardAttentionResponse(
                        LocalDateTime.of(2026, 6, 3, 12, 30),
                        2,
                        List.of(
                                new AdminDashboardAttentionResponse.AttentionItem(
                                        "collect-drift",
                                        "warning",
                                        "수집 drift 확인",
                                        "실패 2건, 부분 성공 1건, 열린 회로 1개",
                                        "admin-collect-triage",
                                        "collect"
                                ),
                                new AdminDashboardAttentionResponse.AttentionItem(
                                        "standard-code-backlog",
                                        "warning",
                                        "표준코드 입력 backlog",
                                        "793명이 주거·복지 표준코드 4개를 모두 비워둔 상태입니다.",
                                        "admin-standard-code-coverage",
                                        "user-profile-standard-codes"
                                )
                        )
                ));

        mockMvc.perform(get("/api/admin/dashboard/attention-feed")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.itemCount").value(2))
                .andExpect(jsonPath("$.data.items[0].key").value("collect-drift"))
                .andExpect(jsonPath("$.data.items[1].key").value("standard-code-backlog"));

        then(adminDashboardAttentionService).should().getAttentionFeed();
    }

    @Test
    @DisplayName("관리자 토큰으로 표준코드 추천 효과 observation API를 호출하면 observation service를 실행한다")
    void adminEndpointAllowsStandardCodeEffectObservation() throws Exception {
        mockAuthenticatedToken("admin-token", List.of(
                new SimpleGrantedAuthority("ROLE_USER"),
                new SimpleGrantedAuthority("ROLE_ADMIN")
        ));
        given(adminDashboardStandardCodeObservationService.getLatestObservation())
                .willReturn(new AdminStandardCodeEffectObservationResponse(
                        true,
                        LocalDateTime.of(2026, 6, 3, 13, 0),
                        "/tmp/recommendation-observation/latest-recommendation-observation-summary.txt",
                        "SUPPLEMENTAL_REVIEW_ONLY",
                        "SUPPLEMENTAL_POLICY_REVIEW",
                        "ok",
                        2,
                        4,
                        24.0,
                        0.1344,
                        "3259:sample",
                        "ok",
                        4,
                        4,
                        4,
                        "basic_living_and_housing_combo",
                        54.0,
                        "basic_living_only",
                        0.23586,
                        "basic_living_only:5:30.00000"
                ));

        mockMvc.perform(get("/api/admin/dashboard/standard-code-effect-observation")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.available").value(true))
                .andExpect(jsonPath("$.data.housingPositiveRuleDeltaRows").value(2))
                .andExpect(jsonPath("$.data.welfareScenarioCount").value(4))
                .andExpect(jsonPath("$.data.welfareMaxRuleDelta").value(54.0));

        then(adminDashboardStandardCodeObservationService).should().getLatestObservation();
    }

    @Test
    @DisplayName("관리자 토큰으로 wrapper observation API를 호출하면 wrapper observation service를 실행한다")
    void adminEndpointAllowsWrapperObservation() throws Exception {
        mockAuthenticatedToken("admin-token", List.of(
                new SimpleGrantedAuthority("ROLE_USER"),
                new SimpleGrantedAuthority("ROLE_ADMIN")
        ));
        given(adminDashboardWrapperObservationService.getLatestObservation())
                .willReturn(new AdminWrapperObservationResponse(
                        true,
                        LocalDateTime.of(2026, 6, 3, 13, 20),
                        "/tmp/active-baseline-suite/latest-active-baseline-summary.txt",
                        "passed",
                        "passed",
                        "ok",
                        1,
                        1,
                        "standard-code-backlog",
                        "표준코드 입력 backlog",
                        52,
                        793,
                        "passed",
                        2,
                        4,
                        54.0,
                        true,
                        LocalDateTime.of(2026, 6, 3, 13, 21),
                        "/tmp/current-priority-suite/latest-current-priority-summary.txt",
                        "passed",
                        true,
                        "ok",
                        1,
                        1,
                        "standard-code-backlog",
                        "표준코드 입력 backlog",
                        52,
                        793,
                        "passed",
                        2,
                        4,
                        4,
                        54.0,
                        true,
                        LocalDateTime.of(2026, 6, 3, 13, 19),
                        "/tmp/current-priority-suite/20260603T041240Z/current-priority-summary.txt",
                        796,
                        -3,
                        "3 감소",
                        "skipped",
                        true,
                        "skipped -> passed",
                        new AdminWrapperObservationResponse.SnapshotAlert(
                                "success",
                                "개선 신호",
                                "표준코드 미입력 3 감소, priority 관측 skipped -> passed"
                        )
                ));

        mockMvc.perform(get("/api/admin/dashboard/wrapper-observation")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.activeBaselineAvailable").value(true))
                .andExpect(jsonPath("$.data.activeBaselineUsersWithAnyStandardCode").value(52))
                .andExpect(jsonPath("$.data.currentPriorityAvailable").value(true))
                .andExpect(jsonPath("$.data.currentPriorityActiveBaselineReused").value(true))
                .andExpect(jsonPath("$.data.currentPriorityRecommendationWelfareMaxRuleDelta").value(54.0))
                .andExpect(jsonPath("$.data.currentPriorityUsersMissingAllStandardCodesDelta").value(-3))
                .andExpect(jsonPath("$.data.currentPriorityUsersMissingAllStandardCodesDeltaLabel").value("3 감소"))
                .andExpect(jsonPath("$.data.currentPriorityRecommendationObservationStatusChanged").value(true))
                .andExpect(jsonPath("$.data.currentPriorityRecommendationObservationStatusTransitionLabel").value("skipped -> passed"))
                .andExpect(jsonPath("$.data.promotedAlert.severity").value("success"));

        then(adminDashboardWrapperObservationService).should().getLatestObservation();
    }

    @Test
    @DisplayName("관리자 토큰으로 정책 임베딩 재구축 API를 호출하면 embedding admin service를 실행한다")
    void adminEndpointAllowsPolicyEmbeddingRebuild() throws Exception {
        mockAuthenticatedToken("admin-token", List.of(
                new SimpleGrantedAuthority("ROLE_USER"),
                new SimpleGrantedAuthority("ROLE_ADMIN")
        ));
        given(policyEmbeddingAdminService.rebuildSearchablePolicyEmbeddings())
                .willReturn(new PolicyEmbeddingRefreshResponse("searchable", 12, 48, 12));

        mockMvc.perform(post("/api/admin/policies/embeddings/rebuild")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.scope").value("searchable"))
                .andExpect(jsonPath("$.data.requestedServiceCount").value(12))
                .andExpect(jsonPath("$.data.refreshedChunkCount").value(12));

        then(policyEmbeddingAdminService).should().rebuildSearchablePolicyEmbeddings();
    }

    @Test
    @DisplayName("관리자 토큰으로 정책 참고 URL 재구축 API를 호출하면 reference url admin service를 실행한다")
    void adminEndpointAllowsPolicyReferenceUrlRebuild() throws Exception {
        mockAuthenticatedToken("admin-token", List.of(
                new SimpleGrantedAuthority("ROLE_USER"),
                new SimpleGrantedAuthority("ROLE_ADMIN")
        ));
        given(policyReferenceUrlAdminService.rebuildReferenceUrls(List.of(
                com.example.welfare.policy.entity.WelfareService.SourceType.YOUTH,
                com.example.welfare.policy.entity.WelfareService.SourceType.BOKJIRO_LOCAL
        ), 50, true)).willReturn(new PolicyReferenceUrlBackfillResponse(
                "selected-detail-sources",
                List.of(
                        com.example.welfare.policy.entity.WelfareService.SourceType.YOUTH,
                        com.example.welfare.policy.entity.WelfareService.SourceType.BOKJIRO_LOCAL
                ),
                50,
                true,
                30,
                2,
                28,
                1,
                1
        ));

        mockMvc.perform(post("/api/admin/policies/reference-urls/rebuild")
                        .param("sourceType", "YOUTH", "BOKJIRO_LOCAL")
                        .param("limitPerSource", "50")
                        .param("missingOnly", "true")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.scope").value("selected-detail-sources"))
                .andExpect(jsonPath("$.data.limitPerSource").value(50))
                .andExpect(jsonPath("$.data.missingOnly").value(true))
                .andExpect(jsonPath("$.data.scannedCount").value(30))
                .andExpect(jsonPath("$.data.skippedCount").value(2))
                .andExpect(jsonPath("$.data.updatedCount").value(28))
                .andExpect(jsonPath("$.data.missingServiceCount").value(1))
                .andExpect(jsonPath("$.data.failedCount").value(1));

        then(policyReferenceUrlAdminService).should().rebuildReferenceUrls(List.of(
                com.example.welfare.policy.entity.WelfareService.SourceType.YOUTH,
                com.example.welfare.policy.entity.WelfareService.SourceType.BOKJIRO_LOCAL
        ), 50, true);
    }

    @Test
    @DisplayName("정책 참고 URL 재구축 API는 limitPerSource 상한 초과 시 400을 반환한다")
    void adminEndpointRejectsTooLargeReferenceUrlRebuildLimit() throws Exception {
        mockAuthenticatedToken("admin-token", List.of(
                new SimpleGrantedAuthority("ROLE_USER"),
                new SimpleGrantedAuthority("ROLE_ADMIN")
        ));

        mockMvc.perform(post("/api/admin/policies/reference-urls/rebuild")
                        .param("limitPerSource", "1001")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("C001"));
    }

    @Test
    @DisplayName("관리자 토큰으로 retrieval evaluation API를 호출하면 evaluation service를 실행한다")
    void adminEndpointAllowsRetrievalEvaluationRun() throws Exception {
        mockAuthenticatedToken("admin-token", List.of(
                new SimpleGrantedAuthority("ROLE_USER"),
                new SimpleGrantedAuthority("ROLE_ADMIN")
        ));
        given(policyRetrievalEvaluationService.evaluateBaseline())
                .willReturn(new PolicyRetrievalEvaluationResponse(
                        "retrieval-baseline-v2",
                        11,
                        9,
                        2,
                        7,
                        9,
                        2,
                        2,
                        1,
                        0,
                        0.77,
                        1.0,
                        1.0,
                        0.22,
                        0.11,
                        4.2,
                        3.1,
                        1.4,
                        0.4,
                        0.66,
                        1.4,
                        List.of()
                ));

        mockMvc.perform(post("/api/admin/policies/retrieval-evaluations/run")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.datasetKey").value("retrieval-baseline-v2"))
                .andExpect(jsonPath("$.data.scenarioCount").value(11))
                .andExpect(jsonPath("$.data.top3HitCount").value(9))
                .andExpect(jsonPath("$.data.fallbackCount").value(2));

        then(policyRetrievalEvaluationService).should().evaluateBaseline();
    }

    @Test
    @DisplayName("관리자 토큰으로 retrieval quality gate API를 호출하면 gate 결과를 반환한다")
    void adminEndpointAllowsRetrievalQualityGate() throws Exception {
        mockAuthenticatedToken("admin-token", List.of(
                new SimpleGrantedAuthority("ROLE_USER"),
                new SimpleGrantedAuthority("ROLE_ADMIN")
        ));
        given(policyRetrievalQualityGateService.evaluateGate())
                .willReturn(new PolicyRetrievalQualityGateResponse(
                        true,
                        "retrieval-baseline-v2",
                        new PolicyRetrievalQualityGateResponse.Thresholds(0.9, 0.9, 1.0, 0),
                        new PolicyRetrievalQualityGateResponse.ActualMetrics(1.0, 1.0, 1.0, 0),
                        List.of()
                ));

        mockMvc.perform(post("/api/admin/policies/retrieval-evaluations/gate")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.passed").value(true))
                .andExpect(jsonPath("$.data.datasetKey").value("retrieval-baseline-v2"))
                .andExpect(jsonPath("$.data.actualMetrics.top1HitRate").value(1.0));

        then(policyRetrievalQualityGateService).should().evaluateGate();
    }

    @Test
    @DisplayName("관리자 토큰으로 retrieval evaluation compare API를 호출하면 baseline 과 candidate 비교를 반환한다")
    void adminEndpointAllowsRetrievalEvaluationCompare() throws Exception {
        mockAuthenticatedToken("admin-token", List.of(
                new SimpleGrantedAuthority("ROLE_USER"),
                new SimpleGrantedAuthority("ROLE_ADMIN")
        ));
        given(policyRetrievalEvaluationService.compareBaseline(org.mockito.ArgumentMatchers.any()))
                .willReturn(new PolicyRetrievalEvaluationCompareResponse(
                        new PolicyRetrievalEvaluationCompareResponse.RetrievalTuning(3, 2, 3, 3),
                        new PolicyRetrievalEvaluationCompareResponse.RetrievalTuning(1, 1, 1, 2),
                        new PolicyRetrievalEvaluationResponse(
                                "retrieval-baseline-v2", 11, 9, 2, 7, 9, 2, 2, 1, 0,
                                0.77, 1.0, 1.0, 0.22, 0.11, 4.2, 3.1, 1.4, 0.4, 0.66, 1.4, List.of()
                        ),
                        new PolicyRetrievalEvaluationResponse(
                                "retrieval-compare-v2-min1-blend1-sem1-terms2", 11, 9, 2, 8, 9, 2, 1, 2, 0,
                                0.88, 1.0, 1.0, 0.11, 0.22, 3.6, 3.0, 1.1, 0.2, 0.66, 1.0, List.of()
                        ),
                        new PolicyRetrievalEvaluationCompareResponse.Delta(1, 0, -1, 1, 0.11, 0.0, -0.11, 0.11, -0.6, -0.2, -0.4)
                ));

        mockMvc.perform(post("/api/admin/policies/retrieval-evaluations/compare")
                        .header("Authorization", "Bearer admin-token")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "minResultCount": 1,
                                  "semanticBlendLimit": 1,
                                  "semanticOnlyLimit": 1,
                                  "maxPreferredTermsInSearchKeyword": 2
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.baseline.datasetKey").value("retrieval-baseline-v2"))
                .andExpect(jsonPath("$.data.candidate.datasetKey").value("retrieval-compare-v2-min1-blend1-sem1-terms2"))
                .andExpect(jsonPath("$.data.delta.fallbackCountDelta").value(-1));

        then(policyRetrievalEvaluationService).should().compareBaseline(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("retrieval evaluation compare API는 튜닝 파라미터 범위 초과 시 400을 반환한다")
    void adminEndpointRejectsInvalidRetrievalEvaluationCompareRequest() throws Exception {
        mockAuthenticatedToken("admin-token", List.of(
                new SimpleGrantedAuthority("ROLE_USER"),
                new SimpleGrantedAuthority("ROLE_ADMIN")
        ));

        mockMvc.perform(post("/api/admin/policies/retrieval-evaluations/compare")
                        .header("Authorization", "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "minResultCount": 201,
                                  "semanticBlendLimit": 1,
                                  "semanticOnlyLimit": 1,
                                  "maxPreferredTermsInSearchKeyword": 2
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("C001"));
    }

    @Test
    @DisplayName("관리자 토큰으로 retrieval evaluation compare export API를 호출하면 비교 csv를 반환한다")
    void adminEndpointAllowsRetrievalEvaluationCompareExport() throws Exception {
        mockAuthenticatedToken("admin-token", List.of(
                new SimpleGrantedAuthority("ROLE_USER"),
                new SimpleGrantedAuthority("ROLE_ADMIN")
        ));
        PolicyRetrievalEvaluationCompareResponse response = new PolicyRetrievalEvaluationCompareResponse(
                new PolicyRetrievalEvaluationCompareResponse.RetrievalTuning(3, 2, 3, 3),
                new PolicyRetrievalEvaluationCompareResponse.RetrievalTuning(1, 1, 1, 2),
                new PolicyRetrievalEvaluationResponse(
                        "retrieval-baseline-v2", 11, 9, 2, 7, 9, 2, 2, 1, 0,
                        0.77, 1.0, 1.0, 0.22, 0.11, 4.2, 3.1, 1.4, 0.4, 0.66, 1.4, List.of()
                ),
                new PolicyRetrievalEvaluationResponse(
                        "retrieval-compare-v2-min1-blend1-sem1-terms2", 11, 9, 2, 8, 9, 2, 1, 2, 0,
                        0.88, 1.0, 1.0, 0.11, 0.22, 3.6, 3.0, 1.1, 0.2, 0.66, 1.0, List.of()
                ),
                new PolicyRetrievalEvaluationCompareResponse.Delta(1, 0, -1, 1, 0.11, 0.0, -0.11, 0.11, -0.6, -0.2, -0.4)
        );
        given(policyRetrievalEvaluationService.compareBaseline(org.mockito.ArgumentMatchers.any())).willReturn(response);
        given(policyRetrievalEvaluationExportService.toCsv(response))
                .willReturn("profile,datasetKey\n\"baseline\",\"retrieval-baseline-v2\"\n");

        mockMvc.perform(post("/api/admin/policies/retrieval-evaluations/compare/export")
                        .header("Authorization", "Bearer admin-token")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "minResultCount": 1,
                                  "semanticBlendLimit": 1,
                                  "semanticOnlyLimit": 1,
                                  "maxPreferredTermsInSearchKeyword": 2
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .contentTypeCompatibleWith("text/csv"))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"retrieval-compare-v2-min1-blend1-sem1-terms2-compare.csv\""))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string("profile,datasetKey\n\"baseline\",\"retrieval-baseline-v2\"\n"));

        then(policyRetrievalEvaluationService).should().compareBaseline(org.mockito.ArgumentMatchers.any());
        then(policyRetrievalEvaluationExportService).should().toCsv(response);
    }

    @Test
    @DisplayName("retrieval evaluation export API는 datasetKey를 안전한 파일명으로 변환한다")
    void adminEndpointSanitizesRetrievalEvaluationExportFilename() throws Exception {
        mockAuthenticatedToken("admin-token", List.of(
                new SimpleGrantedAuthority("ROLE_USER"),
                new SimpleGrantedAuthority("ROLE_ADMIN")
        ));
        PolicyRetrievalEvaluationResponse response = new PolicyRetrievalEvaluationResponse(
                "retrieval\r\nbad\"name",
                1,
                1,
                1,
                1,
                1,
                0,
                0,
                0,
                0,
                1.0,
                1.0,
                1.0,
                0.0,
                0.0,
                1.0,
                1.0,
                1.0,
                0.0,
                1.0,
                1.0,
                List.of()
        );
        given(policyRetrievalEvaluationService.evaluateBaseline()).willReturn(response);
        given(policyRetrievalEvaluationExportService.toCsv(response)).willReturn("profile,datasetKey\n");

        mockMvc.perform(get("/api/admin/policies/retrieval-evaluations/export")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"retrieval_bad_name.csv\""));
    }

    @Test
    @DisplayName("관리자 토큰으로 retrieval evaluation export API를 호출하면 csv를 반환한다")
    void adminEndpointAllowsRetrievalEvaluationExport() throws Exception {
        mockAuthenticatedToken("admin-token", List.of(
                new SimpleGrantedAuthority("ROLE_USER"),
                new SimpleGrantedAuthority("ROLE_ADMIN")
        ));
        PolicyRetrievalEvaluationResponse response = new PolicyRetrievalEvaluationResponse(
                "retrieval-baseline-v2",
                11,
                9,
                2,
                7,
                9,
                2,
                2,
                1,
                0,
                0.77,
                1.0,
                1.0,
                0.22,
                0.11,
                4.2,
                3.1,
                1.4,
                0.4,
                0.66,
                1.4,
                List.of()
        );
        given(policyRetrievalEvaluationService.evaluateBaseline()).willReturn(response);
        given(policyRetrievalEvaluationExportService.toCsv(response))
                .willReturn("datasetKey,scenarioCount\n\"retrieval-baseline-v2\",11\n");

        mockMvc.perform(get("/api/admin/policies/retrieval-evaluations/export")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .contentTypeCompatibleWith("text/csv"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"retrieval-baseline-v2.csv\""))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string("datasetKey,scenarioCount\n\"retrieval-baseline-v2\",11\n"));

        then(policyRetrievalEvaluationService).should().evaluateBaseline();
        then(policyRetrievalEvaluationExportService).should().toCsv(response);
    }

    @Test
    @DisplayName("관리자 토큰으로 category audit API를 호출하면 카테고리 집계를 반환한다")
    void adminEndpointAllowsCategoryAudit() throws Exception {
        mockAuthenticatedToken("admin-token", List.of(
                new SimpleGrantedAuthority("ROLE_USER"),
                new SimpleGrantedAuthority("ROLE_ADMIN")
        ));
        given(policyCategoryAuditService.readAudit())
                .willReturn(new PolicyCategoryAuditResponse(
                        3925L,
                        2544L,
                        0.6481528662d,
                        List.of(
                                new PolicyCategoryAuditResponse.CategoryCount("일자리", 903L, 903L),
                                new PolicyCategoryAuditResponse.CategoryCount("건강·의료", 142L, 142L)
                        ),
                        List.of(
                                new PolicyCategoryAuditResponse.CategorySummary("일자리", 903L, 903L, 0.23d, 1.0d),
                                new PolicyCategoryAuditResponse.CategorySummary("건강·의료", 142L, 142L, 0.03d, 1.0d)
                        ),
                        List.of(
                                new PolicyCategoryAuditResponse.SourceCategoryMappingCount("복지문화", "문화·여가", 49L),
                                new PolicyCategoryAuditResponse.SourceCategoryMappingCount("금융·복지·문화", "건강·의료", 11L)
                        ),
                        List.of(
                                new PolicyCategoryAuditResponse.SourceCategorySummary(
                                        "금융·복지·문화",
                                        11L,
                                        "건강·의료",
                                        11L,
                                        1.0d,
                                        List.of(new PolicyCategoryAuditResponse.SourceCategoryMappingCount("금융·복지·문화", "건강·의료", 11L))
                                ),
                                new PolicyCategoryAuditResponse.SourceCategorySummary(
                                        "복지문화",
                                        49L,
                                        "문화·여가",
                                        49L,
                                        1.0d,
                                        List.of(new PolicyCategoryAuditResponse.SourceCategoryMappingCount("복지문화", "문화·여가", 49L))
                                )
                        )
                ));

        mockMvc.perform(get("/api/admin/policies/category-audit")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalPolicyCount").value(3925))
                .andExpect(jsonPath("$.data.searchablePolicyCount").value(2544))
                .andExpect(jsonPath("$.data.searchablePolicyRatio").value(0.6481528662d))
                .andExpect(jsonPath("$.data.unifiedCategoryCounts[0].unifiedCategory").value("일자리"))
                .andExpect(jsonPath("$.data.topUnifiedCategorySummaries[0].unifiedCategory").value("일자리"))
                .andExpect(jsonPath("$.data.youthBroadCategoryMappings[0].sourceCategory").value("복지문화"))
                .andExpect(jsonPath("$.data.youthBroadCategorySummaries[1].sourceCategory").value("복지문화"));

        then(policyCategoryAuditService).should().readAudit();
    }

    @Test
    @DisplayName("관리자 전체 수집 API는 부분 실패를 구조화된 결과로 노출한다")
    void collectAllEndpointExposesPartialFailures() throws Exception {
        mockAuthenticatedToken("admin-token", List.of(
                new SimpleGrantedAuthority("ROLE_USER"),
                new SimpleGrantedAuthority("ROLE_ADMIN")
        ));
        given(collectBatchService.collectAllNow()).willReturn(new CollectBatchRunResult(List.of(
                CollectBatchRunResult.SourceRunResult.success(CollectSource.YOUTH, CollectResult.of(10, 10, 0, 0, 0)),
                CollectBatchRunResult.SourceRunResult.failure(CollectSource.BOKJIRO_LOCAL, new CustomException(ErrorCode.COLLECT_API_FAILED))
        )));

        mockMvc.perform(post("/api/admin/collect/all")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.completedWithFailures").value(true))
                .andExpect(jsonPath("$.data.requestedSourceCount").value(2))
                .andExpect(jsonPath("$.data.failedSourceCount").value(1))
                .andExpect(jsonPath("$.data.sourceResults[1].sourceKey").value("bokjiro-local"))
                .andExpect(jsonPath("$.data.sourceResults[1].success").value(false))
                .andExpect(jsonPath("$.data.sourceResults[1].errorCode").value("COL001"));
    }

    @Test
    @DisplayName("127.0.0.1 Vite origin 도 CORS preflight 를 통과한다")
    void corsAllows127001ViteOrigin() throws Exception {
        mockMvc.perform(options("/api/admin/collect/youth")
                        .header("Origin", "http://127.0.0.1:5173")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "authorization,content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://127.0.0.1:5173"));
    }

    @Test
    @DisplayName("관리자 토큰으로 대시보드 요약 API를 호출하면 admin dashboard service를 실행한다")
    void adminEndpointAllowsDashboardSummary() throws Exception {
        mockAuthenticatedToken("admin-token", List.of(
                new SimpleGrantedAuthority("ROLE_USER"),
                new SimpleGrantedAuthority("ROLE_ADMIN")
        ));
        given(adminDashboardSummaryService.getSummary(
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.any()
        ))
                .willReturn(new AdminDashboardResponse(
                        LocalDateTime.of(2026, 5, 2, 10, 0),
                        new AdminDashboardResponse.CollectSection(
                                0,
                                3,
                                1,
                                0,
                                7,
                                List.of(),
                                List.of(
                                        new AdminDashboardResponse.CollectFailureSnapshot(
                                                "BOKJIRO_LOCAL",
                                                "FAILED",
                                                LocalDateTime.of(2026, 5, 2, 7, 0),
                                                LocalDateTime.of(2026, 5, 2, 7, 1),
                                                "COL001",
                                                "rate limited",
                                                0,
                                                0,
                                                1
                                        )
                                )
                        ),
                        new AdminDashboardResponse.RecommendationSection(
                                "GROWTH",
                                java.math.BigDecimal.valueOf(0.60),
                                java.math.BigDecimal.valueOf(0.40),
                                "STABLE",
                                500,
                                250L,
                                false,
                                250,
                                2,
                                7,
                                8,
                                3,
                                1,
                                LocalDateTime.of(2026, 5, 2, 9, 45),
                                java.math.BigDecimal.valueOf(0.3750),
                                java.math.BigDecimal.valueOf(0.1250),
                                new AdminDashboardResponse.RecommendationTrafficMixSnapshot(
                                        8,
                                        0,
                                        0,
                                        0,
                                        0,
                                        3,
                                        0,
                                        0,
                                        0,
                                        0,
                                        3,
                                        0,
                                        0,
                                        0,
                                        0
                                ),
                                "DEFERRED_NO_REAL_USER_TRAFFIC",
                                new AdminDashboardResponse.RecommendationConcentrationSnapshot(
                                        4071,
                                        454,
                                        113,
                                        2622L,
                                        "청년월세 지원사업",
                                        "BOKJIRO_CENTRAL",
                                        "주거",
                                        271,
                                        java.math.BigDecimal.valueOf(59.69),
                                new AdminDashboardResponse.ServiceCohortMixSnapshot(
                                        268,
                                        1,
                                        2,
                                        0,
                                        2
                                ),
                                "LOCAL_SEED_WITHOUT_REAL_USER_LEADER",
                                "CONCENTRATED_TOP1",
                                        "DEFERRED_NO_REAL_USER_COHORT",
                                        "LOCAL_REAL_NON_EXAMPLE_SEED_WITH_NON_REAL_BATCH"
                                ),
                                "DEFERRED_NO_REAL_USER_TRAFFIC",
                                new AdminDashboardResponse.RecommendationRecentWindowSnapshot(
                                        24,
                                        2622L,
                                        83,
                                        3,
                                        80,
                                        0,
                                        3284L,
                                        "인천 청년도약기지(취업아카데미)",
                                        6,
                                        5,
                                        java.math.BigDecimal.valueOf(7.23),
                                        0,
                                        0
                                ),
                                new AdminDashboardResponse.RecommendationReviewGateStalenessSnapshot(
                                        2622L,
                                        "ALL_TIME_LATEST_PER_USER",
                                        24,
                                        454,
                                        3,
                                        272,
                                        0,
                                        LocalDateTime.of(2026, 5, 13, 13, 39, 31),
                                        LocalDateTime.of(2026, 5, 17, 11, 49, 50),
                                        80,
                                        80,
                                        0,
                                        0
                                ),
                                "RECENT_WINDOW_CLEARS_HISTORICAL_2622_DOMINANCE",
                                true,
                                "NOT_A_CANDIDATE_PRIMARY_GATE_NOT_NON_REAL_BLOCKED",
                                "PRIMARY_REVIEW_GATE_IS_NOT_DEFERRED_NON_REAL_LEADER_SIGNAL",
                                "KEEP_PRIMARY_BASELINE",
                                "RECENT_WINDOW_CANDIDATE_HAS_NOT_CLEARED_PRIMARY_BASELINE_REQUIREMENTS",
                                "KEEP_PRIMARY_BASELINE",
                                "RECENT_WINDOW_POLICY_PROMOTION_CONDITIONS_NOT_MET",
                                "NOT_READY_FOR_BOUNDED_PROMOTION_REVIEW",
                                "REAL_USER_TRAFFIC_GATE_NOT_READY",
                                "DO_NOT_RUN_BOUNDED_PROMOTION_REVIEW",
                                "REAL_USER_TRAFFIC_GATE_NOT_READY",
                                "NOT_READY_FOR_EXPLICIT_PROMOTION_APPROVAL",
                                "REAL_USER_TRAFFIC_GATE_NOT_READY",
                                "PROMOTION_APPROVAL_NOT_APPLICABLE",
                                "REAL_USER_TRAFFIC_GATE_NOT_READY",
                                "APPROVAL_DECISION_NOT_READY",
                                "REAL_USER_TRAFFIC_GATE_NOT_READY",
                                "APPROVAL_RECORD_NOT_READY",
                                "REAL_USER_TRAFFIC_GATE_NOT_READY",
                                "BOUNDED_PROMOTION_REVIEW_RUN_NOT_READY",
                                "REAL_USER_TRAFFIC_GATE_NOT_READY",
                                "NOT_READY_FOR_BOUNDED_PROMOTION_REVIEW_RUN",
                                "REAL_USER_TRAFFIC_GATE_NOT_READY",
                                "BOUNDED_PROMOTION_REVIEW_RUN_DECISION_NOT_READY",
                                "REAL_USER_TRAFFIC_GATE_NOT_READY",
                                "NOT_READY_FOR_BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL",
                                "REAL_USER_TRAFFIC_GATE_NOT_READY",
                                "BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL_DECISION_NOT_READY",
                                "REAL_USER_TRAFFIC_GATE_NOT_READY",
                                "BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL_NOT_READY",
                                "REAL_USER_TRAFFIC_GATE_NOT_READY",
                                "NOT_READY_FOR_BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL_RECORD",
                                "REAL_USER_TRAFFIC_GATE_NOT_READY",
                                "BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL_RECORD_NOT_READY",
                                "REAL_USER_TRAFFIC_GATE_NOT_READY",
                                "BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL_RECORD_TRANSITION_NOT_READY",
                                "REAL_USER_TRAFFIC_GATE_NOT_READY",
                                "BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL_RECORD_WRITE_NOT_READY",
                                "REAL_USER_TRAFFIC_GATE_NOT_READY",
                                List.of(
                                        new AdminDashboardResponse.RecommendationWeightSnapshot(
                                                "GROWTH",
                                                java.math.BigDecimal.valueOf(0.60),
                                                java.math.BigDecimal.valueOf(0.40),
                                                8
                                        )
                                )
                        ),
                        new AdminDashboardResponse.NotificationSection(1, 0, 7, 5, 1, 4, 2, 0, 2, 1),
                        new AdminDashboardResponse.PolicyTriageSection(
                                "DUPLICATE_THEN_LINK_PRIORITY",
                                "duplicate queue를 exact -> mirror 순으로 먼저 줄이는 편이 맞습니다.",
                                "exact duplicate -> mirror variant -> benefit/support link review",
                                139,
                                12,
                                16,
                                164,
                                33,
                                12
                        ),
                        new AdminDashboardResponse.SearchSection(4, 7, 12, 2, 7, java.math.BigDecimal.valueOf(5.25), List.of(
                                new AdminDashboardResponse.SearchKeywordSnapshot("월세", 5)
                        ), List.of(
                                new AdminDashboardResponse.SearchKeywordSnapshot("대출", 2)
                        )),
                        new AdminDashboardResponse.UserPiiSyncSection(0, 1, 12, LocalDateTime.of(2026, 5, 2, 9, 30)),
                        new AdminDashboardResponse.TrendSection(
                                List.of(
                                        new AdminDashboardResponse.CollectTrendPoint(1, 3, 1, 0),
                                        new AdminDashboardResponse.CollectTrendPoint(7, 9, 2, 1),
                                        new AdminDashboardResponse.CollectTrendPoint(30, 18, 4, 2)
                                ),
                                List.of(
                                        new AdminDashboardResponse.RecommendationTrendPoint(
                                                1,
                                                8,
                                                3,
                                                1,
                                                java.math.BigDecimal.valueOf(0.3750),
                                                java.math.BigDecimal.valueOf(0.1250)
                                        ),
                                        new AdminDashboardResponse.RecommendationTrendPoint(
                                                7,
                                                30,
                                                9,
                                                6,
                                                java.math.BigDecimal.valueOf(0.3000),
                                                java.math.BigDecimal.valueOf(0.2000)
                                        ),
                                        new AdminDashboardResponse.RecommendationTrendPoint(
                                                30,
                                                90,
                                                18,
                                                20,
                                                java.math.BigDecimal.valueOf(0.2000),
                                                java.math.BigDecimal.valueOf(0.2222)
                                        )
                                ),
                                List.of(
                                        new AdminDashboardResponse.SearchTrendPoint(1, 4, 1),
                                        new AdminDashboardResponse.SearchTrendPoint(7, 12, 2),
                                        new AdminDashboardResponse.SearchTrendPoint(30, 40, 7)
                                )
                        )
                ));

        mockMvc.perform(get("/api/admin/dashboard/summary")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.collect.successJobsLast24h").value(3))
                .andExpect(jsonPath("$.data.collect.windowDays").value(7))
                .andExpect(jsonPath("$.data.collect.latestFailuresInWindow[0].jobName").value("BOKJIRO_LOCAL"))
                .andExpect(jsonPath("$.data.recommendation.windowDays").value(7))
                .andExpect(jsonPath("$.data.recommendation.nextWeightKey").value("STABLE"))
                .andExpect(jsonPath("$.data.recommendation.remainingLogsUntilNextWeight").value(250))
                .andExpect(jsonPath("$.data.recommendation.sentInWindow").value(8))
                .andExpect(jsonPath("$.data.recommendation.trafficMixInWindow.exampleLogsInWindow").value(8))
                .andExpect(jsonPath("$.data.recommendation.trafficMixInWindow.boundedLocalLogsInWindow").value(0))
                .andExpect(jsonPath("$.data.recommendation.trafficMixInWindow.localRealNonExampleSeedLogsInWindow").value(0))
                .andExpect(jsonPath("$.data.recommendation.trafficMixInWindow.realUserLogsInWindow").value(0))
                .andExpect(jsonPath("$.data.recommendation.trafficMixInWindow.realNonExampleLogsInWindow").value(0))
                .andExpect(jsonPath("$.data.recommendation.realUserTrafficGateInWindow").value("DEFERRED_NO_REAL_USER_TRAFFIC"))
                .andExpect(jsonPath("$.data.recommendation.recommendationReviewGate").value("DEFERRED_NO_REAL_USER_TRAFFIC"))
                .andExpect(jsonPath("$.data.recommendation.latestBatchConcentration.latestBatchRows").value(4071))
                .andExpect(jsonPath("$.data.recommendation.latestBatchConcentration.top1LeaderServiceId").value(2622))
                .andExpect(jsonPath("$.data.recommendation.latestBatchConcentration.top1LeaderSharePct").value(59.69))
                .andExpect(jsonPath("$.data.recommendation.latestBatchConcentration.top1LeaderUserMix.exampleUsers").value(268))
                .andExpect(jsonPath("$.data.recommendation.latestBatchConcentration.top1LeaderUserMix.boundedLocalUsers").value(1))
                .andExpect(jsonPath("$.data.recommendation.latestBatchConcentration.top1LeaderUserMix.localRealNonExampleSeedUsers").value(2))
                .andExpect(jsonPath("$.data.recommendation.latestBatchConcentration.top1LeaderUserMix.realUserUsers").value(0))
                .andExpect(jsonPath("$.data.recommendation.latestBatchConcentration.top1LeaderSignalSummary").value("LOCAL_SEED_WITHOUT_REAL_USER_LEADER"))
                .andExpect(jsonPath("$.data.recommendation.latestBatchConcentration.concentrationReadiness").value("CONCENTRATED_TOP1"))
                .andExpect(jsonPath("$.data.recommendation.latestBatchConcentration.realUserCohortGate").value("DEFERRED_NO_REAL_USER_COHORT"))
                .andExpect(jsonPath("$.data.recommendation.recentWindowLatestBatch.recentWindowHours").value(24))
                .andExpect(jsonPath("$.data.recommendation.recentWindowLatestBatch.top1LeaderServiceId").value(3284))
                .andExpect(jsonPath("$.data.recommendation.recentWindowLatestBatch.top1LeaderRealUserUsers").value(5))
                .andExpect(jsonPath("$.data.recommendation.recentWindowLatestBatch.targetTop1Users").value(0))
                .andExpect(jsonPath("$.data.recommendation.reviewGateStaleness.primaryReferenceMode").value("ALL_TIME_LATEST_PER_USER"))
                .andExpect(jsonPath("$.data.recommendation.reviewGateStaleness.exampleTargetTop1Users").value(272))
                .andExpect(jsonPath("$.data.recommendation.reviewGateStaleness.exampleTargetTop1Last24h").value(0))
                .andExpect(jsonPath("$.data.recommendation.reviewGateStaleness.realUserLatestUsers").value(80))
                .andExpect(jsonPath("$.data.recommendation.reviewGateStaleness.realUserTargetTop1Users").value(0))
                .andExpect(jsonPath("$.data.recommendation.recentWindowRecommendationReviewReading").value("RECENT_WINDOW_CLEARS_HISTORICAL_2622_DOMINANCE"))
                .andExpect(jsonPath("$.data.recommendation.historicalExampleDominanceDetected").value(true))
                .andExpect(jsonPath("$.data.recommendation.reviewGatePolicyCandidateStatus").value("NOT_A_CANDIDATE_PRIMARY_GATE_NOT_NON_REAL_BLOCKED"))
                .andExpect(jsonPath("$.data.recommendation.reviewGatePolicyCandidateReason").value("PRIMARY_REVIEW_GATE_IS_NOT_DEFERRED_NON_REAL_LEADER_SIGNAL"))
                .andExpect(jsonPath("$.data.recommendation.reviewGatePolicyPromotionStatus").value("KEEP_PRIMARY_BASELINE"))
                .andExpect(jsonPath("$.data.recommendation.reviewGatePolicyPromotionReason").value("RECENT_WINDOW_CANDIDATE_HAS_NOT_CLEARED_PRIMARY_BASELINE_REQUIREMENTS"))
                .andExpect(jsonPath("$.data.recommendation.reviewGatePolicyPromotionActionStatus").value("KEEP_PRIMARY_BASELINE"))
                .andExpect(jsonPath("$.data.recommendation.reviewGatePolicyPromotionActionReason").value("RECENT_WINDOW_POLICY_PROMOTION_CONDITIONS_NOT_MET"))
                .andExpect(jsonPath("$.data.recommendation.reviewGatePolicyPromotionReadinessStatus").value("NOT_READY_FOR_BOUNDED_PROMOTION_REVIEW"))
                .andExpect(jsonPath("$.data.recommendation.reviewGatePolicyPromotionReadinessReason").value("REAL_USER_TRAFFIC_GATE_NOT_READY"))
                .andExpect(jsonPath("$.data.recommendation.reviewGatePolicyPromotionExecutionStatus").value("DO_NOT_RUN_BOUNDED_PROMOTION_REVIEW"))
                .andExpect(jsonPath("$.data.recommendation.reviewGatePolicyPromotionExecutionReason").value("REAL_USER_TRAFFIC_GATE_NOT_READY"))
                .andExpect(jsonPath("$.data.recommendation.reviewGatePolicyPromotionApprovalCriteriaStatus").value("NOT_READY_FOR_EXPLICIT_PROMOTION_APPROVAL"))
                .andExpect(jsonPath("$.data.recommendation.reviewGatePolicyPromotionApprovalCriteriaReason").value("REAL_USER_TRAFFIC_GATE_NOT_READY"))
                .andExpect(jsonPath("$.data.recommendation.reviewGatePolicyPromotionApprovalStatus").value("PROMOTION_APPROVAL_NOT_APPLICABLE"))
                .andExpect(jsonPath("$.data.recommendation.reviewGatePolicyPromotionApprovalReason").value("REAL_USER_TRAFFIC_GATE_NOT_READY"))
                .andExpect(jsonPath("$.data.recommendation.reviewGatePolicyPromotionApprovalDecisionStatus").value("APPROVAL_DECISION_NOT_READY"))
                .andExpect(jsonPath("$.data.recommendation.reviewGatePolicyPromotionApprovalDecisionReason").value("REAL_USER_TRAFFIC_GATE_NOT_READY"))
                .andExpect(jsonPath("$.data.recommendation.reviewGatePolicyPromotionApprovalRecordStatus").value("APPROVAL_RECORD_NOT_READY"))
                .andExpect(jsonPath("$.data.recommendation.reviewGatePolicyPromotionApprovalRecordReason").value("REAL_USER_TRAFFIC_GATE_NOT_READY"))
                .andExpect(jsonPath("$.data.recommendation.reviewGatePolicyPromotionReviewRunStatus").value("BOUNDED_PROMOTION_REVIEW_RUN_NOT_READY"))
                .andExpect(jsonPath("$.data.recommendation.reviewGatePolicyPromotionReviewRunReason").value("REAL_USER_TRAFFIC_GATE_NOT_READY"))
                .andExpect(jsonPath("$.data.recommendation.reviewGatePolicyPromotionReviewRunCriteriaStatus").value("NOT_READY_FOR_BOUNDED_PROMOTION_REVIEW_RUN"))
                .andExpect(jsonPath("$.data.recommendation.reviewGatePolicyPromotionReviewRunCriteriaReason").value("REAL_USER_TRAFFIC_GATE_NOT_READY"))
                .andExpect(jsonPath("$.data.recommendation.reviewGatePolicyPromotionReviewRunDecisionStatus").value("BOUNDED_PROMOTION_REVIEW_RUN_DECISION_NOT_READY"))
                .andExpect(jsonPath("$.data.recommendation.reviewGatePolicyPromotionReviewRunDecisionReason").value("REAL_USER_TRAFFIC_GATE_NOT_READY"))
                .andExpect(jsonPath("$.data.recommendation.reviewGatePolicyPromotionReviewRunApprovalCriteriaStatus").value("NOT_READY_FOR_BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL"))
                .andExpect(jsonPath("$.data.recommendation.reviewGatePolicyPromotionReviewRunApprovalCriteriaReason").value("REAL_USER_TRAFFIC_GATE_NOT_READY"))
                .andExpect(jsonPath("$.data.recommendation.reviewGatePolicyPromotionReviewRunApprovalDecisionStatus").value("BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL_DECISION_NOT_READY"))
                .andExpect(jsonPath("$.data.recommendation.reviewGatePolicyPromotionReviewRunApprovalDecisionReason").value("REAL_USER_TRAFFIC_GATE_NOT_READY"))
                .andExpect(jsonPath("$.data.recommendation.reviewGatePolicyPromotionReviewRunApprovalStatus").value("BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL_NOT_READY"))
                .andExpect(jsonPath("$.data.recommendation.reviewGatePolicyPromotionReviewRunApprovalReason").value("REAL_USER_TRAFFIC_GATE_NOT_READY"))
                .andExpect(jsonPath("$.data.recommendation.reviewGatePolicyPromotionReviewRunApprovalRecordCriteriaStatus").value("NOT_READY_FOR_BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL_RECORD"))
                .andExpect(jsonPath("$.data.recommendation.reviewGatePolicyPromotionReviewRunApprovalRecordCriteriaReason").value("REAL_USER_TRAFFIC_GATE_NOT_READY"))
                .andExpect(jsonPath("$.data.recommendation.reviewGatePolicyPromotionReviewRunApprovalRecordStatus").value("BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL_RECORD_NOT_READY"))
                .andExpect(jsonPath("$.data.recommendation.reviewGatePolicyPromotionReviewRunApprovalRecordReason").value("REAL_USER_TRAFFIC_GATE_NOT_READY"))
                .andExpect(jsonPath("$.data.recommendation.reviewGatePolicyPromotionReviewRunApprovalRecordTransitionStatus").value("BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL_RECORD_TRANSITION_NOT_READY"))
                .andExpect(jsonPath("$.data.recommendation.reviewGatePolicyPromotionReviewRunApprovalRecordTransitionReason").value("REAL_USER_TRAFFIC_GATE_NOT_READY"))
                .andExpect(jsonPath("$.data.recommendation.reviewGatePolicyPromotionReviewRunApprovalRecordWriteStatus").value("BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL_RECORD_WRITE_NOT_READY"))
                .andExpect(jsonPath("$.data.recommendation.reviewGatePolicyPromotionReviewRunApprovalRecordWriteReason").value("REAL_USER_TRAFFIC_GATE_NOT_READY"))
                .andExpect(jsonPath("$.data.notification.windowDays").value(7))
                .andExpect(jsonPath("$.data.notification.sentInWindow").value(5))
                .andExpect(jsonPath("$.data.notification.unreadAlerts").value(4))
                .andExpect(jsonPath("$.data.notification.staleUnread7d").value(2))
                .andExpect(jsonPath("$.data.notification.staleUnread14d").value(0))
                .andExpect(jsonPath("$.data.notification.retryableFailedNotifications").value(2))
                .andExpect(jsonPath("$.data.notification.terminalFailedNotifications").value(1))
                .andExpect(jsonPath("$.data.search.windowDays").value(7))
                .andExpect(jsonPath("$.data.search.zeroResultSearchesInWindow").value(2))
                .andExpect(jsonPath("$.data.trend.collect[1].windowDays").value(7))
                .andExpect(jsonPath("$.data.trend.recommendation[1].clickThroughRate").value(0.3000))
                .andExpect(jsonPath("$.data.trend.search[2].zeroResultSearches").value(7))
                .andExpect(jsonPath("$.data.search.topKeywordsInWindow[0].keyword").value("월세"))
                .andExpect(jsonPath("$.data.search.zeroResultKeywordsInWindow[0].keyword").value("대출"))
                .andExpect(jsonPath("$.data.userPiiSync.failedCount").value(1));

        then(adminDashboardSummaryService).should().getSummary(
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    @DisplayName("관리자 토큰으로 대시보드 요약 API를 호출할 때 trendWindowDays를 전달하면 해당 창으로 조회한다")
    void adminEndpointAllowsDashboardSummaryWithCustomTrendWindows() throws Exception {
        mockAuthenticatedToken("admin-token", List.of(
                new SimpleGrantedAuthority("ROLE_USER"),
                new SimpleGrantedAuthority("ROLE_ADMIN")
        ));
        given(adminDashboardSummaryService.getSummary(14, List.of(3, 14)))
                .willReturn(new AdminDashboardResponse(
                        LocalDateTime.of(2026, 5, 3, 14, 0),
                        new AdminDashboardResponse.CollectSection(0, 0, 0, 0, 14, List.of(), List.of()),
                        new AdminDashboardResponse.RecommendationSection(
                                "GROWTH",
                                java.math.BigDecimal.valueOf(0.60),
                                java.math.BigDecimal.valueOf(0.40),
                                "STABLE",
                                500,
                                490L,
                                false,
                                10,
                                1,
                                14,
                                2,
                                1,
                                0,
                                null,
                                java.math.BigDecimal.valueOf(0.5000),
                                java.math.BigDecimal.valueOf(0.0000),
                                new AdminDashboardResponse.RecommendationTrafficMixSnapshot(
                                        2,
                                        0,
                                        0,
                                        0,
                                        0,
                                        1,
                                        0,
                                        0,
                                        0,
                                        0,
                                        1,
                                        0,
                                        0,
                                        0,
                                        0
                                ),
                                "DEFERRED_NO_REAL_USER_TRAFFIC",
                                new AdminDashboardResponse.RecommendationConcentrationSnapshot(
                                        0,
                                        0,
                                        0,
                                        null,
                                        null,
                                        null,
                                        null,
                                        0,
                                        java.math.BigDecimal.ZERO,
                                        new AdminDashboardResponse.ServiceCohortMixSnapshot(
                                                0,
                                                0,
                                                0,
                                                0,
                                                0
                                        ),
                                        "EMPTY_TOP1_LEADER",
                                        "DEFERRED_EMPTY_COHORT",
                                        "DEFERRED_EMPTY_COHORT",
                                        "EMPTY_COHORT"
                                ),
                                "DEFERRED_NO_REAL_USER_TRAFFIC",
                                new AdminDashboardResponse.RecommendationRecentWindowSnapshot(
                                        24,
                                        2622L,
                                        0,
                                        0,
                                        0,
                                        0,
                                        null,
                                        null,
                                        0,
                                        0,
                                        java.math.BigDecimal.ZERO,
                                        0,
                                        0
                                ),
                                new AdminDashboardResponse.RecommendationReviewGateStalenessSnapshot(
                                        2622L,
                                        "ALL_TIME_LATEST_PER_USER",
                                        24,
                                        0,
                                        0,
                                        0,
                                        0,
                                        null,
                                        null,
                                        0,
                                        0,
                                        0,
                                        0
                                ),
                                "DEFERRED_EMPTY_RECENT_WINDOW",
                                false,
                                "NOT_A_CANDIDATE_PRIMARY_GATE_NOT_NON_REAL_BLOCKED",
                                "PRIMARY_REVIEW_GATE_IS_NOT_DEFERRED_NON_REAL_LEADER_SIGNAL",
                                "KEEP_PRIMARY_BASELINE",
                                "RECENT_WINDOW_CANDIDATE_HAS_NOT_CLEARED_PRIMARY_BASELINE_REQUIREMENTS",
                                "KEEP_PRIMARY_BASELINE",
                                "RECENT_WINDOW_POLICY_PROMOTION_CONDITIONS_NOT_MET",
                                "NOT_READY_FOR_BOUNDED_PROMOTION_REVIEW",
                                "REAL_USER_TRAFFIC_GATE_NOT_READY",
                                "DO_NOT_RUN_BOUNDED_PROMOTION_REVIEW",
                                "REAL_USER_TRAFFIC_GATE_NOT_READY",
                                "NOT_READY_FOR_EXPLICIT_PROMOTION_APPROVAL",
                                "REAL_USER_TRAFFIC_GATE_NOT_READY",
                                "PROMOTION_APPROVAL_NOT_APPLICABLE",
                                "REAL_USER_TRAFFIC_GATE_NOT_READY",
                                "APPROVAL_DECISION_NOT_READY",
                                "REAL_USER_TRAFFIC_GATE_NOT_READY",
                                "APPROVAL_RECORD_NOT_READY",
                                "REAL_USER_TRAFFIC_GATE_NOT_READY",
                                "BOUNDED_PROMOTION_REVIEW_RUN_NOT_READY",
                                "REAL_USER_TRAFFIC_GATE_NOT_READY",
                                "NOT_READY_FOR_BOUNDED_PROMOTION_REVIEW_RUN",
                                "REAL_USER_TRAFFIC_GATE_NOT_READY",
                                "BOUNDED_PROMOTION_REVIEW_RUN_DECISION_NOT_READY",
                                "REAL_USER_TRAFFIC_GATE_NOT_READY",
                                "NOT_READY_FOR_BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL",
                                "REAL_USER_TRAFFIC_GATE_NOT_READY",
                                "BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL_DECISION_NOT_READY",
                                "REAL_USER_TRAFFIC_GATE_NOT_READY",
                                "BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL_NOT_READY",
                                "REAL_USER_TRAFFIC_GATE_NOT_READY",
                                "NOT_READY_FOR_BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL_RECORD",
                                "REAL_USER_TRAFFIC_GATE_NOT_READY",
                                "BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL_RECORD_NOT_READY",
                                "REAL_USER_TRAFFIC_GATE_NOT_READY",
                                "BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL_RECORD_TRANSITION_NOT_READY",
                                "REAL_USER_TRAFFIC_GATE_NOT_READY",
                                "BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL_RECORD_WRITE_NOT_READY",
                                "REAL_USER_TRAFFIC_GATE_NOT_READY",
                                List.of()
                        ),
                        new AdminDashboardResponse.NotificationSection(0, 0, 14, 0, 0, 0, 0, 0, 0, 0),
                        new AdminDashboardResponse.PolicyTriageSection(
                                "LOW_BACKLOG_STEADY_STATE",
                                "정책 backlog는 급한 exact/mirror/link 우선 항목이 줄어든 상태입니다.",
                                "keep nightly observation and small-batch review",
                                0,
                                0,
                                0,
                                0,
                                0,
                                0
                        ),
                        new AdminDashboardResponse.SearchSection(0, 14, 0, 0, 0, java.math.BigDecimal.ZERO, List.of(), List.of()),
                        new AdminDashboardResponse.UserPiiSyncSection(0, 0, 0, null),
                        new AdminDashboardResponse.TrendSection(
                                List.of(
                                        new AdminDashboardResponse.CollectTrendPoint(3, 1, 0, 0),
                                        new AdminDashboardResponse.CollectTrendPoint(14, 2, 0, 1)
                                ),
                                List.of(
                                        new AdminDashboardResponse.RecommendationTrendPoint(
                                                3,
                                                1,
                                                1,
                                                0,
                                                java.math.BigDecimal.valueOf(1.0000),
                                                java.math.BigDecimal.valueOf(0.0000)
                                        ),
                                        new AdminDashboardResponse.RecommendationTrendPoint(
                                                14,
                                                2,
                                                1,
                                                1,
                                                java.math.BigDecimal.valueOf(0.5000),
                                                java.math.BigDecimal.valueOf(0.5000)
                                        )
                                ),
                                List.of(
                                        new AdminDashboardResponse.SearchTrendPoint(3, 2, 0),
                                        new AdminDashboardResponse.SearchTrendPoint(14, 5, 1)
                                )
                        )
                ));

        mockMvc.perform(get("/api/admin/dashboard/summary")
                        .param("summaryWindowDays", "14")
                        .param("trendWindowDays", "3", "14")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.collect.windowDays").value(14))
                .andExpect(jsonPath("$.data.trend.collect[0].windowDays").value(3))
                .andExpect(jsonPath("$.data.trend.collect[1].windowDays").value(14))
                .andExpect(jsonPath("$.data.recommendation.windowDays").value(14))
                .andExpect(jsonPath("$.data.recommendation.nextWeightMinLogCount").value(500))
                .andExpect(jsonPath("$.data.recommendation.trafficMixInWindow.exampleUsersInWindow").value(1))
                .andExpect(jsonPath("$.data.recommendation.trafficMixInWindow.boundedLocalUsersInWindow").value(0))
                .andExpect(jsonPath("$.data.recommendation.trafficMixInWindow.localRealNonExampleSeedUsersInWindow").value(0))
                .andExpect(jsonPath("$.data.recommendation.trafficMixInWindow.realUserUsersInWindow").value(0))
                .andExpect(jsonPath("$.data.recommendation.trafficMixInWindow.realNonExampleUsersInWindow").value(0))
                .andExpect(jsonPath("$.data.recommendation.realUserTrafficGateInWindow").value("DEFERRED_NO_REAL_USER_TRAFFIC"))
                .andExpect(jsonPath("$.data.recommendation.recommendationReviewGate").value("DEFERRED_NO_REAL_USER_TRAFFIC"))
                .andExpect(jsonPath("$.data.recommendation.latestBatchConcentration.concentrationReadiness").value("DEFERRED_EMPTY_COHORT"))
                .andExpect(jsonPath("$.data.recommendation.reviewGateStaleness.primaryReferenceMode").value("ALL_TIME_LATEST_PER_USER"))
                .andExpect(jsonPath("$.data.recommendation.reviewGateStaleness.exampleTargetTop1Users").value(0))
                .andExpect(jsonPath("$.data.recommendation.recentWindowRecommendationReviewReading").value("DEFERRED_EMPTY_RECENT_WINDOW"))
                .andExpect(jsonPath("$.data.recommendation.historicalExampleDominanceDetected").value(false))
                .andExpect(jsonPath("$.data.recommendation.reviewGatePolicyCandidateStatus").value("NOT_A_CANDIDATE_PRIMARY_GATE_NOT_NON_REAL_BLOCKED"))
                .andExpect(jsonPath("$.data.recommendation.reviewGatePolicyCandidateReason").value("PRIMARY_REVIEW_GATE_IS_NOT_DEFERRED_NON_REAL_LEADER_SIGNAL"))
                .andExpect(jsonPath("$.data.recommendation.reviewGatePolicyPromotionStatus").value("KEEP_PRIMARY_BASELINE"))
                .andExpect(jsonPath("$.data.recommendation.reviewGatePolicyPromotionReason").value("RECENT_WINDOW_CANDIDATE_HAS_NOT_CLEARED_PRIMARY_BASELINE_REQUIREMENTS"))
                .andExpect(jsonPath("$.data.recommendation.reviewGatePolicyPromotionActionStatus").value("KEEP_PRIMARY_BASELINE"))
                .andExpect(jsonPath("$.data.recommendation.reviewGatePolicyPromotionActionReason").value("RECENT_WINDOW_POLICY_PROMOTION_CONDITIONS_NOT_MET"))
                .andExpect(jsonPath("$.data.recommendation.reviewGatePolicyPromotionReadinessStatus").value("NOT_READY_FOR_BOUNDED_PROMOTION_REVIEW"))
                .andExpect(jsonPath("$.data.recommendation.reviewGatePolicyPromotionReadinessReason").value("REAL_USER_TRAFFIC_GATE_NOT_READY"))
                .andExpect(jsonPath("$.data.recommendation.reviewGatePolicyPromotionExecutionStatus").value("DO_NOT_RUN_BOUNDED_PROMOTION_REVIEW"))
                .andExpect(jsonPath("$.data.recommendation.reviewGatePolicyPromotionExecutionReason").value("REAL_USER_TRAFFIC_GATE_NOT_READY"))
                .andExpect(jsonPath("$.data.recommendation.reviewGatePolicyPromotionApprovalCriteriaStatus").value("NOT_READY_FOR_EXPLICIT_PROMOTION_APPROVAL"))
                .andExpect(jsonPath("$.data.recommendation.reviewGatePolicyPromotionApprovalCriteriaReason").value("REAL_USER_TRAFFIC_GATE_NOT_READY"))
                .andExpect(jsonPath("$.data.recommendation.reviewGatePolicyPromotionApprovalStatus").value("PROMOTION_APPROVAL_NOT_APPLICABLE"))
                .andExpect(jsonPath("$.data.recommendation.reviewGatePolicyPromotionApprovalReason").value("REAL_USER_TRAFFIC_GATE_NOT_READY"))
                .andExpect(jsonPath("$.data.recommendation.reviewGatePolicyPromotionApprovalDecisionStatus").value("APPROVAL_DECISION_NOT_READY"))
                .andExpect(jsonPath("$.data.recommendation.reviewGatePolicyPromotionApprovalDecisionReason").value("REAL_USER_TRAFFIC_GATE_NOT_READY"))
                .andExpect(jsonPath("$.data.recommendation.reviewGatePolicyPromotionApprovalRecordStatus").value("APPROVAL_RECORD_NOT_READY"))
                .andExpect(jsonPath("$.data.recommendation.reviewGatePolicyPromotionApprovalRecordReason").value("REAL_USER_TRAFFIC_GATE_NOT_READY"))
                .andExpect(jsonPath("$.data.recommendation.reviewGatePolicyPromotionReviewRunStatus").value("BOUNDED_PROMOTION_REVIEW_RUN_NOT_READY"))
                .andExpect(jsonPath("$.data.recommendation.reviewGatePolicyPromotionReviewRunReason").value("REAL_USER_TRAFFIC_GATE_NOT_READY"))
                .andExpect(jsonPath("$.data.recommendation.reviewGatePolicyPromotionReviewRunCriteriaStatus").value("NOT_READY_FOR_BOUNDED_PROMOTION_REVIEW_RUN"))
                .andExpect(jsonPath("$.data.recommendation.reviewGatePolicyPromotionReviewRunCriteriaReason").value("REAL_USER_TRAFFIC_GATE_NOT_READY"))
                .andExpect(jsonPath("$.data.recommendation.reviewGatePolicyPromotionReviewRunDecisionStatus").value("BOUNDED_PROMOTION_REVIEW_RUN_DECISION_NOT_READY"))
                .andExpect(jsonPath("$.data.recommendation.reviewGatePolicyPromotionReviewRunDecisionReason").value("REAL_USER_TRAFFIC_GATE_NOT_READY"))
                .andExpect(jsonPath("$.data.recommendation.reviewGatePolicyPromotionReviewRunApprovalCriteriaStatus").value("NOT_READY_FOR_BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL"))
                .andExpect(jsonPath("$.data.recommendation.reviewGatePolicyPromotionReviewRunApprovalCriteriaReason").value("REAL_USER_TRAFFIC_GATE_NOT_READY"))
                .andExpect(jsonPath("$.data.recommendation.reviewGatePolicyPromotionReviewRunApprovalDecisionStatus").value("BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL_DECISION_NOT_READY"))
                .andExpect(jsonPath("$.data.recommendation.reviewGatePolicyPromotionReviewRunApprovalDecisionReason").value("REAL_USER_TRAFFIC_GATE_NOT_READY"))
                .andExpect(jsonPath("$.data.recommendation.reviewGatePolicyPromotionReviewRunApprovalStatus").value("BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL_NOT_READY"))
                .andExpect(jsonPath("$.data.recommendation.reviewGatePolicyPromotionReviewRunApprovalReason").value("REAL_USER_TRAFFIC_GATE_NOT_READY"))
                .andExpect(jsonPath("$.data.recommendation.reviewGatePolicyPromotionReviewRunApprovalRecordCriteriaStatus").value("NOT_READY_FOR_BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL_RECORD"))
                .andExpect(jsonPath("$.data.recommendation.reviewGatePolicyPromotionReviewRunApprovalRecordCriteriaReason").value("REAL_USER_TRAFFIC_GATE_NOT_READY"))
                .andExpect(jsonPath("$.data.recommendation.reviewGatePolicyPromotionReviewRunApprovalRecordStatus").value("BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL_RECORD_NOT_READY"))
                .andExpect(jsonPath("$.data.recommendation.reviewGatePolicyPromotionReviewRunApprovalRecordReason").value("REAL_USER_TRAFFIC_GATE_NOT_READY"))
                .andExpect(jsonPath("$.data.recommendation.reviewGatePolicyPromotionReviewRunApprovalRecordTransitionStatus").value("BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL_RECORD_TRANSITION_NOT_READY"))
                .andExpect(jsonPath("$.data.recommendation.reviewGatePolicyPromotionReviewRunApprovalRecordTransitionReason").value("REAL_USER_TRAFFIC_GATE_NOT_READY"))
                .andExpect(jsonPath("$.data.recommendation.reviewGatePolicyPromotionReviewRunApprovalRecordWriteStatus").value("BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL_RECORD_WRITE_NOT_READY"))
                .andExpect(jsonPath("$.data.recommendation.reviewGatePolicyPromotionReviewRunApprovalRecordWriteReason").value("REAL_USER_TRAFFIC_GATE_NOT_READY"))
                .andExpect(jsonPath("$.data.notification.windowDays").value(14))
                .andExpect(jsonPath("$.data.trend.recommendation[1].fallbackRate").value(0.5000))
                .andExpect(jsonPath("$.data.search.windowDays").value(14))
                .andExpect(jsonPath("$.data.trend.search[1].windowDays").value(14));

        then(adminDashboardSummaryService).should().getSummary(14, List.of(3, 14));
    }

    @Test
    @DisplayName("관리자 토큰으로 검색 실패 상세 API를 호출하면 search failure 응답을 반환한다")
    void adminEndpointAllowsSearchFailures() throws Exception {
        mockAuthenticatedToken("admin-token", List.of(
                new SimpleGrantedAuthority("ROLE_USER"),
                new SimpleGrantedAuthority("ROLE_ADMIN")
        ));
        given(adminDashboardSearchService.getSearchFailures(14, 3))
                .willReturn(new AdminSearchFailureResponse(
                        LocalDateTime.of(2026, 5, 3, 15, 0),
                        14,
                        6,
                        List.of(
                                new AdminSearchFailureResponse.KeywordCount("대출", 4),
                                new AdminSearchFailureResponse.KeywordCount("월세", 2)
                        ),
                        List.of(
                                new AdminSearchFailureResponse.RegionCount("서울", "관악구", 3)
                        ),
                        List.of(
                                new AdminSearchFailureResponse.FilterPatternCount(
                                        "UNEMPLOYED",
                                        "HOUSING",
                                        "YOUTH",
                                        true,
                                        false,
                                        "LATEST",
                                        5
                                )
                        ),
                        List.of(
                                new AdminSearchFailureResponse.SearchFailureSample(
                                        "대출",
                                        "서울",
                                        "관악구",
                                        "UNEMPLOYED",
                                        "HOUSING",
                                        "YOUTH",
                                        true,
                                        false,
                                        "LATEST",
                                        LocalDateTime.of(2026, 5, 3, 14, 45)
                                )
                        ),
                        List.of(
                                new AdminSearchFailureResponse.RetryGroup(
                                        "USER_KEY",
                                        "user-key-1",
                                        "대출",
                                        "서울",
                                        "관악구",
                                        "UNEMPLOYED",
                                        "HOUSING",
                                        "YOUTH",
                                        true,
                                        false,
                                        "LATEST",
                                        3,
                                        LocalDateTime.of(2026, 5, 3, 14, 0),
                                        LocalDateTime.of(2026, 5, 3, 14, 45)
                                )
                        ),
                        List.of(
                                new AdminSearchFailureResponse.RecoveredSearchGroup(
                                        "USER_KEY",
                                        "user-key-1",
                                        "대출",
                                        "서울",
                                        "관악구",
                                        "UNEMPLOYED",
                                        "HOUSING",
                                        "YOUTH",
                                        true,
                                        false,
                                        "LATEST",
                                        2,
                                        1,
                                        LocalDateTime.of(2026, 5, 3, 14, 55)
                                )
                        )
                ));

        mockMvc.perform(get("/api/admin/dashboard/search-failures")
                        .param("summaryWindowDays", "14")
                        .param("limit", "3")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.windowDays").value(14))
                .andExpect(jsonPath("$.data.zeroResultSearchesInWindow").value(6))
                .andExpect(jsonPath("$.data.zeroResultKeywords[0].keyword").value("대출"))
                .andExpect(jsonPath("$.data.zeroResultRegions[0].sido").value("서울"))
                .andExpect(jsonPath("$.data.zeroResultRegions[0].sgg").value("관악구"))
                .andExpect(jsonPath("$.data.zeroResultFilterPatterns[0].statusFilter").value("UNEMPLOYED"))
                .andExpect(jsonPath("$.data.zeroResultFilterPatterns[0].searchCount").value(5))
                .andExpect(jsonPath("$.data.recentSamples[0].keyword").value("대출"))
                .andExpect(jsonPath("$.data.retryGroups[0].actorType").value("USER_KEY"))
                .andExpect(jsonPath("$.data.retryGroups[0].actorKey").value("user-key-1"))
                .andExpect(jsonPath("$.data.retryGroups[0].retryCount").value(3))
                .andExpect(jsonPath("$.data.recoveredSearchGroups[0].actorType").value("USER_KEY"))
                .andExpect(jsonPath("$.data.recoveredSearchGroups[0].actorKey").value("user-key-1"))
                .andExpect(jsonPath("$.data.recoveredSearchGroups[0].zeroResultCount").value(2))
                .andExpect(jsonPath("$.data.recoveredSearchGroups[0].recoveredResultCount").value(1));

        then(adminDashboardSearchService).should().getSearchFailures(14, 3);
    }

    @Test
    @DisplayName("관리자 토큰으로 추천 breakdown API를 호출하면 recommendation 상세 응답을 반환한다")
    void adminEndpointAllowsRecommendationBreakdowns() throws Exception {
        mockAuthenticatedToken("admin-token", List.of(
                new SimpleGrantedAuthority("ROLE_USER"),
                new SimpleGrantedAuthority("ROLE_ADMIN")
        ));
        given(adminDashboardRecommendationService.getRecommendationBreakdowns(14, 3))
                .willReturn(new AdminRecommendationBreakdownResponse(
                        LocalDateTime.of(2026, 5, 3, 16, 0),
                        14,
                        30,
                        9,
                        6,
                        new AdminRecommendationBreakdownResponse.RecommendationTrafficMixSnapshot(
                                30,
                                0,
                                0,
                                0,
                                0,
                                18,
                                0,
                                0,
                                0,
                                0,
                                9,
                                0,
                                0,
                                0,
                                0
                        ),
                        "DEFERRED_NO_REAL_USER_TRAFFIC",
                        new AdminRecommendationBreakdownResponse.RecommendationConcentrationSnapshot(
                                4071,
                                454,
                                113,
                                2622L,
                                "청년월세 지원사업",
                                "BOKJIRO_CENTRAL",
                                "주거",
                                271,
                                java.math.BigDecimal.valueOf(59.69),
                                new AdminRecommendationBreakdownResponse.ServiceCohortMixSnapshot(
                                        268,
                                        1,
                                        2,
                                        0,
                                        2
                                ),
                                "LOCAL_SEED_WITHOUT_REAL_USER_LEADER",
                                "CONCENTRATED_TOP1",
                                "DEFERRED_NO_REAL_USER_COHORT",
                                "LOCAL_REAL_NON_EXAMPLE_SEED_WITH_NON_REAL_BATCH"
                        ),
                        "DEFERRED_NO_REAL_USER_TRAFFIC",
                        new AdminRecommendationBreakdownResponse.RecommendationRecentWindowSnapshot(
                                24,
                                2622L,
                                83,
                                3,
                                80,
                                0,
                                3284L,
                                "인천 청년도약기지(취업아카데미)",
                                6,
                                5,
                                java.math.BigDecimal.valueOf(7.23),
                                0,
                                0
                        ),
                        new AdminRecommendationBreakdownResponse.RecommendationReviewGateStalenessSnapshot(
                                2622L,
                                "ALL_TIME_LATEST_PER_USER",
                                24,
                                454,
                                3,
                                272,
                                0,
                                LocalDateTime.of(2026, 5, 13, 13, 39, 31),
                                LocalDateTime.of(2026, 5, 17, 11, 49, 50),
                                80,
                                80,
                        0,
                        0
                ),
                "RECENT_WINDOW_CLEARS_HISTORICAL_2622_DOMINANCE",
                true,
                "NOT_A_CANDIDATE_PRIMARY_GATE_NOT_NON_REAL_BLOCKED",
                "PRIMARY_REVIEW_GATE_IS_NOT_DEFERRED_NON_REAL_LEADER_SIGNAL",
                "KEEP_PRIMARY_BASELINE",
                "RECENT_WINDOW_CANDIDATE_HAS_NOT_CLEARED_PRIMARY_BASELINE_REQUIREMENTS",
                "KEEP_PRIMARY_BASELINE",
                "RECENT_WINDOW_POLICY_PROMOTION_CONDITIONS_NOT_MET",
                "NOT_READY_FOR_BOUNDED_PROMOTION_REVIEW",
                "REAL_USER_TRAFFIC_GATE_NOT_READY",
                "DO_NOT_RUN_BOUNDED_PROMOTION_REVIEW",
                "REAL_USER_TRAFFIC_GATE_NOT_READY",
                "NOT_READY_FOR_EXPLICIT_PROMOTION_APPROVAL",
                "REAL_USER_TRAFFIC_GATE_NOT_READY",
                "PROMOTION_APPROVAL_NOT_APPLICABLE",
                "REAL_USER_TRAFFIC_GATE_NOT_READY",
                "APPROVAL_DECISION_NOT_READY",
                "REAL_USER_TRAFFIC_GATE_NOT_READY",
                "APPROVAL_RECORD_NOT_READY",
                "REAL_USER_TRAFFIC_GATE_NOT_READY",
                "BOUNDED_PROMOTION_REVIEW_RUN_NOT_READY",
                "REAL_USER_TRAFFIC_GATE_NOT_READY",
                "NOT_READY_FOR_BOUNDED_PROMOTION_REVIEW_RUN",
                "REAL_USER_TRAFFIC_GATE_NOT_READY",
                "BOUNDED_PROMOTION_REVIEW_RUN_DECISION_NOT_READY",
                "REAL_USER_TRAFFIC_GATE_NOT_READY",
                "NOT_READY_FOR_BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL",
                "REAL_USER_TRAFFIC_GATE_NOT_READY",
                "BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL_DECISION_NOT_READY",
                "REAL_USER_TRAFFIC_GATE_NOT_READY",
                "BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL_NOT_READY",
                "REAL_USER_TRAFFIC_GATE_NOT_READY",
                "NOT_READY_FOR_BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL_RECORD",
                "REAL_USER_TRAFFIC_GATE_NOT_READY",
                "BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL_RECORD_NOT_READY",
                "REAL_USER_TRAFFIC_GATE_NOT_READY",
                "BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL_RECORD_TRANSITION_NOT_READY",
                "REAL_USER_TRAFFIC_GATE_NOT_READY",
                "BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL_RECORD_WRITE_NOT_READY",
                "REAL_USER_TRAFFIC_GATE_NOT_READY",
                List.of(
                        new AdminRecommendationBreakdownResponse.RepeatedServiceSnapshot(
                                        2622L,
                                        "청년월세 지원사업",
                                        "BOKJIRO_CENTRAL",
                                        "주거",
                                        449,
                                        449,
                                        new AdminRecommendationBreakdownResponse.ServiceCohortMixSnapshot(
                                                447,
                                                0,
                                                2,
                                                0,
                                                2
                                        )
                                )
                        ),
                        List.of(
                                new AdminRecommendationBreakdownResponse.Top1ServiceSnapshot(
                                        2622L,
                                        "청년월세 지원사업",
                                        "BOKJIRO_CENTRAL",
                                        "주거",
                                        271,
                                        new AdminRecommendationBreakdownResponse.ServiceCohortMixSnapshot(
                                                269,
                                                0,
                                                2,
                                                0,
                                                2
                                        )
                                )
                        ),
                        List.of(
                                new AdminRecommendationBreakdownResponse.SourceBreakdown(
                                        "YOUTH",
                                        18,
                                        6,
                                        3,
                                        java.math.BigDecimal.valueOf(0.3333),
                                        java.math.BigDecimal.valueOf(0.1667)
                                )
                        ),
                        List.of(
                                new AdminRecommendationBreakdownResponse.CategoryBreakdown(
                                        "HOUSING",
                                        10,
                                        4,
                                        1,
                                        java.math.BigDecimal.valueOf(0.4000),
                                        java.math.BigDecimal.valueOf(0.1000)
                                )
                        ),
                        List.of(
                                new AdminRecommendationBreakdownResponse.WeightBreakdown(
                                        "GROWTH",
                                        java.math.BigDecimal.valueOf(0.60),
                                        java.math.BigDecimal.valueOf(0.40),
                                        12,
                                        3,
                                        2,
                                        java.math.BigDecimal.valueOf(0.2500),
                                        java.math.BigDecimal.valueOf(0.1667)
                                )
                        ),
                        List.of(
                                new AdminRecommendationBreakdownResponse.FacetGroup(
                                        "YOUTH_INCOME_CONDITION_TYPE",
                                        "소득조건 유형",
                                        List.of(
                                                new AdminRecommendationBreakdownResponse.FacetBucket(
                                                        "기타",
                                                        12,
                                                        7
                                                )
                                        )
                                )
                        ),
                        List.of(
                                new AdminRecommendationBreakdownResponse.FacetGroup(
                                        "GOV24_SERVICE_FIELD",
                                        "정부24 서비스 분야",
                                        List.of(
                                                new AdminRecommendationBreakdownResponse.FacetBucket(
                                                        "주거·자립",
                                                        6,
                                                        4
                                                )
                                        )
                                ),
                                new AdminRecommendationBreakdownResponse.FacetGroup(
                                        "GOV24_USER_TYPE_TOKEN",
                                        "정부24 사용자 구분",
                                        List.of(
                                                new AdminRecommendationBreakdownResponse.FacetBucket(
                                                        "소상공인",
                                                        4,
                                                        4
                                                )
                                        )
                                )
                        ),
                        List.of(
                                new AdminRecommendationBreakdownResponse.RecommendationSample(
                                        101L,
                                        501L,
                                        "청년 월세 지원",
                                        "YOUTH",
                                        "HOUSING",
                                        "EXAMPLE_SMOKE",
                                        java.math.BigDecimal.valueOf(0.75231),
                                        true,
                                        false,
                                        LocalDateTime.of(2026, 5, 3, 9, 0),
                                        null
                                )
                        ),
                        List.of(
                                new AdminRecommendationBreakdownResponse.RecommendationSample(
                                        102L,
                                        502L,
                                        "청년 전세 지원",
                                        "BOKJIRO_LOCAL",
                                        "HOUSING",
                                        "EXAMPLE_SMOKE",
                                        java.math.BigDecimal.valueOf(0.88123),
                                        false,
                                        true,
                                        LocalDateTime.of(2026, 5, 3, 8, 0),
                                        LocalDateTime.of(2026, 5, 3, 8, 30)
                                )
                        ),
                        List.of(
                                new AdminRecommendationBreakdownResponse.RepeatExposureGroup(
                                        "user-key-1",
                                        501L,
                                        "청년 월세 지원",
                                        "YOUTH",
                                        "HOUSING",
                                        "EXAMPLE_SMOKE",
                                        3,
                                        1,
                                        1,
                                        LocalDateTime.of(2026, 5, 1, 8, 0),
                                        LocalDateTime.of(2026, 5, 3, 9, 0),
                                        LocalDateTime.of(2026, 5, 3, 9, 10)
                                )
                        )
                ));

        mockMvc.perform(get("/api/admin/dashboard/recommendation-breakdowns")
                        .param("summaryWindowDays", "14")
                        .param("limit", "3")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.windowDays").value(14))
                .andExpect(jsonPath("$.data.sentLogsInWindow").value(30))
                .andExpect(jsonPath("$.data.clickedLogsInWindow").value(9))
                .andExpect(jsonPath("$.data.fallbackLogsInWindow").value(6))
                .andExpect(jsonPath("$.data.trafficMixInWindow.exampleLogsInWindow").value(30))
                .andExpect(jsonPath("$.data.trafficMixInWindow.boundedLocalUsersInWindow").value(0))
                .andExpect(jsonPath("$.data.trafficMixInWindow.localRealNonExampleSeedUsersInWindow").value(0))
                .andExpect(jsonPath("$.data.trafficMixInWindow.realUserUsersInWindow").value(0))
                .andExpect(jsonPath("$.data.trafficMixInWindow.realNonExampleUsersInWindow").value(0))
                .andExpect(jsonPath("$.data.realUserTrafficGateInWindow").value("DEFERRED_NO_REAL_USER_TRAFFIC"))
                .andExpect(jsonPath("$.data.recommendationReviewGate").value("DEFERRED_NO_REAL_USER_TRAFFIC"))
                .andExpect(jsonPath("$.data.latestBatchConcentration.latestBatchRows").value(4071))
                .andExpect(jsonPath("$.data.latestBatchConcentration.top1LeaderServiceId").value(2622))
                .andExpect(jsonPath("$.data.latestBatchConcentration.top1LeaderSharePct").value(59.69))
                .andExpect(jsonPath("$.data.latestBatchConcentration.top1LeaderUserMix.exampleUsers").value(268))
                .andExpect(jsonPath("$.data.latestBatchConcentration.top1LeaderUserMix.boundedLocalUsers").value(1))
                .andExpect(jsonPath("$.data.latestBatchConcentration.top1LeaderUserMix.localRealNonExampleSeedUsers").value(2))
                .andExpect(jsonPath("$.data.latestBatchConcentration.top1LeaderUserMix.realUserUsers").value(0))
                .andExpect(jsonPath("$.data.latestBatchConcentration.top1LeaderSignalSummary").value("LOCAL_SEED_WITHOUT_REAL_USER_LEADER"))
                .andExpect(jsonPath("$.data.latestBatchConcentration.concentrationReadiness").value("CONCENTRATED_TOP1"))
                .andExpect(jsonPath("$.data.latestBatchConcentration.realUserCohortGate").value("DEFERRED_NO_REAL_USER_COHORT"))
                .andExpect(jsonPath("$.data.latestBatchConcentration.signalQuality").value("LOCAL_REAL_NON_EXAMPLE_SEED_WITH_NON_REAL_BATCH"))
                .andExpect(jsonPath("$.data.recentWindowLatestBatch.recentWindowHours").value(24))
                .andExpect(jsonPath("$.data.recentWindowLatestBatch.top1LeaderServiceId").value(3284))
                .andExpect(jsonPath("$.data.recentWindowLatestBatch.top1LeaderRealUserUsers").value(5))
                .andExpect(jsonPath("$.data.recentWindowLatestBatch.targetTop1Users").value(0))
                .andExpect(jsonPath("$.data.reviewGateStaleness.primaryReferenceMode").value("ALL_TIME_LATEST_PER_USER"))
                .andExpect(jsonPath("$.data.reviewGateStaleness.exampleTargetTop1Users").value(272))
                .andExpect(jsonPath("$.data.reviewGateStaleness.exampleTargetTop1Last24h").value(0))
                .andExpect(jsonPath("$.data.reviewGateStaleness.realUserLatestUsers").value(80))
                .andExpect(jsonPath("$.data.reviewGateStaleness.realUserTargetTop1Users").value(0))
                .andExpect(jsonPath("$.data.recentWindowRecommendationReviewReading").value("RECENT_WINDOW_CLEARS_HISTORICAL_2622_DOMINANCE"))
                .andExpect(jsonPath("$.data.historicalExampleDominanceDetected").value(true))
                .andExpect(jsonPath("$.data.reviewGatePolicyCandidateStatus").value("NOT_A_CANDIDATE_PRIMARY_GATE_NOT_NON_REAL_BLOCKED"))
                .andExpect(jsonPath("$.data.reviewGatePolicyCandidateReason").value("PRIMARY_REVIEW_GATE_IS_NOT_DEFERRED_NON_REAL_LEADER_SIGNAL"))
                .andExpect(jsonPath("$.data.reviewGatePolicyPromotionStatus").value("KEEP_PRIMARY_BASELINE"))
                .andExpect(jsonPath("$.data.reviewGatePolicyPromotionReason").value("RECENT_WINDOW_CANDIDATE_HAS_NOT_CLEARED_PRIMARY_BASELINE_REQUIREMENTS"))
                .andExpect(jsonPath("$.data.reviewGatePolicyPromotionActionStatus").value("KEEP_PRIMARY_BASELINE"))
                .andExpect(jsonPath("$.data.reviewGatePolicyPromotionActionReason").value("RECENT_WINDOW_POLICY_PROMOTION_CONDITIONS_NOT_MET"))
                .andExpect(jsonPath("$.data.reviewGatePolicyPromotionReadinessStatus").value("NOT_READY_FOR_BOUNDED_PROMOTION_REVIEW"))
                .andExpect(jsonPath("$.data.reviewGatePolicyPromotionReadinessReason").value("REAL_USER_TRAFFIC_GATE_NOT_READY"))
                .andExpect(jsonPath("$.data.reviewGatePolicyPromotionExecutionStatus").value("DO_NOT_RUN_BOUNDED_PROMOTION_REVIEW"))
                .andExpect(jsonPath("$.data.reviewGatePolicyPromotionExecutionReason").value("REAL_USER_TRAFFIC_GATE_NOT_READY"))
                .andExpect(jsonPath("$.data.reviewGatePolicyPromotionApprovalCriteriaStatus").value("NOT_READY_FOR_EXPLICIT_PROMOTION_APPROVAL"))
                .andExpect(jsonPath("$.data.reviewGatePolicyPromotionApprovalCriteriaReason").value("REAL_USER_TRAFFIC_GATE_NOT_READY"))
                .andExpect(jsonPath("$.data.reviewGatePolicyPromotionApprovalStatus").value("PROMOTION_APPROVAL_NOT_APPLICABLE"))
                .andExpect(jsonPath("$.data.reviewGatePolicyPromotionApprovalReason").value("REAL_USER_TRAFFIC_GATE_NOT_READY"))
                .andExpect(jsonPath("$.data.reviewGatePolicyPromotionApprovalDecisionStatus").value("APPROVAL_DECISION_NOT_READY"))
                .andExpect(jsonPath("$.data.reviewGatePolicyPromotionApprovalDecisionReason").value("REAL_USER_TRAFFIC_GATE_NOT_READY"))
                .andExpect(jsonPath("$.data.reviewGatePolicyPromotionApprovalRecordStatus").value("APPROVAL_RECORD_NOT_READY"))
                .andExpect(jsonPath("$.data.reviewGatePolicyPromotionApprovalRecordReason").value("REAL_USER_TRAFFIC_GATE_NOT_READY"))
                .andExpect(jsonPath("$.data.reviewGatePolicyPromotionReviewRunStatus").value("BOUNDED_PROMOTION_REVIEW_RUN_NOT_READY"))
                .andExpect(jsonPath("$.data.reviewGatePolicyPromotionReviewRunReason").value("REAL_USER_TRAFFIC_GATE_NOT_READY"))
                .andExpect(jsonPath("$.data.reviewGatePolicyPromotionReviewRunCriteriaStatus").value("NOT_READY_FOR_BOUNDED_PROMOTION_REVIEW_RUN"))
                .andExpect(jsonPath("$.data.reviewGatePolicyPromotionReviewRunCriteriaReason").value("REAL_USER_TRAFFIC_GATE_NOT_READY"))
                .andExpect(jsonPath("$.data.reviewGatePolicyPromotionReviewRunDecisionStatus").value("BOUNDED_PROMOTION_REVIEW_RUN_DECISION_NOT_READY"))
                .andExpect(jsonPath("$.data.reviewGatePolicyPromotionReviewRunDecisionReason").value("REAL_USER_TRAFFIC_GATE_NOT_READY"))
                .andExpect(jsonPath("$.data.reviewGatePolicyPromotionReviewRunApprovalCriteriaStatus").value("NOT_READY_FOR_BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL"))
                .andExpect(jsonPath("$.data.reviewGatePolicyPromotionReviewRunApprovalCriteriaReason").value("REAL_USER_TRAFFIC_GATE_NOT_READY"))
                .andExpect(jsonPath("$.data.reviewGatePolicyPromotionReviewRunApprovalDecisionStatus").value("BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL_DECISION_NOT_READY"))
                .andExpect(jsonPath("$.data.reviewGatePolicyPromotionReviewRunApprovalDecisionReason").value("REAL_USER_TRAFFIC_GATE_NOT_READY"))
                .andExpect(jsonPath("$.data.reviewGatePolicyPromotionReviewRunApprovalStatus").value("BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL_NOT_READY"))
                .andExpect(jsonPath("$.data.reviewGatePolicyPromotionReviewRunApprovalReason").value("REAL_USER_TRAFFIC_GATE_NOT_READY"))
                .andExpect(jsonPath("$.data.reviewGatePolicyPromotionReviewRunApprovalRecordCriteriaStatus").value("NOT_READY_FOR_BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL_RECORD"))
                .andExpect(jsonPath("$.data.reviewGatePolicyPromotionReviewRunApprovalRecordCriteriaReason").value("REAL_USER_TRAFFIC_GATE_NOT_READY"))
                .andExpect(jsonPath("$.data.reviewGatePolicyPromotionReviewRunApprovalRecordStatus").value("BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL_RECORD_NOT_READY"))
                .andExpect(jsonPath("$.data.reviewGatePolicyPromotionReviewRunApprovalRecordReason").value("REAL_USER_TRAFFIC_GATE_NOT_READY"))
                .andExpect(jsonPath("$.data.reviewGatePolicyPromotionReviewRunApprovalRecordTransitionStatus").value("BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL_RECORD_TRANSITION_NOT_READY"))
                .andExpect(jsonPath("$.data.reviewGatePolicyPromotionReviewRunApprovalRecordTransitionReason").value("REAL_USER_TRAFFIC_GATE_NOT_READY"))
                .andExpect(jsonPath("$.data.reviewGatePolicyPromotionReviewRunApprovalRecordWriteStatus").value("BOUNDED_PROMOTION_REVIEW_RUN_APPROVAL_RECORD_WRITE_NOT_READY"))
                .andExpect(jsonPath("$.data.reviewGatePolicyPromotionReviewRunApprovalRecordWriteReason").value("REAL_USER_TRAFFIC_GATE_NOT_READY"))
                .andExpect(jsonPath("$.data.topRepeatedServices[0].serviceId").value(2622))
                .andExpect(jsonPath("$.data.topRepeatedServices[0].rowCount").value(449))
                .andExpect(jsonPath("$.data.topRepeatedServices[0].userMix.exampleUsers").value(447))
                .andExpect(jsonPath("$.data.topRepeatedServices[0].userMix.localRealNonExampleSeedUsers").value(2))
                .andExpect(jsonPath("$.data.topRepeatedServices[0].userMix.realUserUsers").value(0))
                .andExpect(jsonPath("$.data.top1Services[0].serviceId").value(2622))
                .andExpect(jsonPath("$.data.top1Services[0].usersAsTop1").value(271))
                .andExpect(jsonPath("$.data.top1Services[0].userMix.exampleUsers").value(269))
                .andExpect(jsonPath("$.data.top1Services[0].userMix.localRealNonExampleSeedUsers").value(2))
                .andExpect(jsonPath("$.data.top1Services[0].userMix.realUserUsers").value(0))
                .andExpect(jsonPath("$.data.sourceBreakdowns[0].sourceType").value("YOUTH"))
                .andExpect(jsonPath("$.data.categoryBreakdowns[0].category").value("HOUSING"))
                .andExpect(jsonPath("$.data.weightBreakdowns[0].weightKey").value("GROWTH"))
                .andExpect(jsonPath("$.data.youthOfficialFacetGroups[0].facetKey").value("YOUTH_INCOME_CONDITION_TYPE"))
                .andExpect(jsonPath("$.data.youthOfficialFacetGroups[0].label").value("소득조건 유형"))
                .andExpect(jsonPath("$.data.youthOfficialFacetGroups[0].buckets[0].label").value("기타"))
                .andExpect(jsonPath("$.data.gov24FacetGroups[0].facetKey").value("GOV24_SERVICE_FIELD"))
                .andExpect(jsonPath("$.data.gov24FacetGroups[0].buckets[0].label").value("주거·자립"))
                .andExpect(jsonPath("$.data.gov24FacetGroups[1].facetKey").value("GOV24_USER_TYPE_TOKEN"))
                .andExpect(jsonPath("$.data.gov24FacetGroups[1].buckets[0].label").value("소상공인"))
                .andExpect(jsonPath("$.data.recentFallbackSamples[0].title").value("청년 월세 지원"))
                .andExpect(jsonPath("$.data.recentFallbackSamples[0].userCohort").value("EXAMPLE_SMOKE"))
                .andExpect(jsonPath("$.data.recentClickedSamples[0].title").value("청년 전세 지원"))
                .andExpect(jsonPath("$.data.recentClickedSamples[0].userCohort").value("EXAMPLE_SMOKE"))
                .andExpect(jsonPath("$.data.repeatExposureGroups[0].userKey").value("user-key-1"))
                .andExpect(jsonPath("$.data.repeatExposureGroups[0].userCohort").value("EXAMPLE_SMOKE"))
                .andExpect(jsonPath("$.data.repeatExposureGroups[0].serviceId").value(501))
                .andExpect(jsonPath("$.data.repeatExposureGroups[0].exposureCount").value(3));

        then(adminDashboardRecommendationService).should().getRecommendationBreakdowns(14, 3);
    }

    @Test
    @DisplayName("관리자 토큰으로 recommendation diagnostics API를 호출하면 단계별 후보 진단 응답을 반환한다")
    void adminEndpointAllowsRecommendationDiagnostics() throws Exception {
        mockAuthenticatedToken("admin-token", List.of(
                new SimpleGrantedAuthority("ROLE_USER"),
                new SimpleGrantedAuthority("ROLE_ADMIN")
        ));
        given(adminDashboardRecommendationDiagnosticService.getRecommendationDiagnostics(
                "user-key-1",
                List.of(3686L, 2736L)
        )).willReturn(new AdminRecommendationCandidateDiagnosticResponse(
                LocalDateTime.of(2026, 5, 17, 20, 30),
                "user-key-1",
                "REAL_USER",
                "youth_all",
                150,
                20,
                44,
                5,
                49,
                49,
                47,
                10,
                "PRE_AI_POST_SCORING",
                true,
                List.of(
                        new AdminRecommendationCandidateDiagnosticResponse.ServiceDiagnostic(
                                3686L,
                                "드림나래",
                                "BOKJIRO_LOCAL",
                                "기타",
                                "보육·교육",
                                List.of(),
                                List.of(),
                                List.of(),
                                List.of(),
                                List.of(),
                                List.of(),
                                List.of(),
                                List.of(),
                                null,
                                null,
                                null,
                                null,
                                true,
                                false,
                                true,
                                false,
                                1,
                                null,
                                true,
                                false,
                                1,
                                null,
                                true,
                                1,
                                true,
                                true,
                                "PRESENT_IN_SAVED_BATCH",
                                true,
                                true,
                                true,
                                23.0,
                                23.0,
                                90.0,
                                90.0,
                                "SCORED",
                                "청년 직무 경험과 직접 연결됨",
                                1.025,
                                1,
                                1.0,
                                null,
                                1.0,
                                1.0,
                                1.0,
                                "JOB",
                                0.0,
                                true,
                                0.085,
                                1.085,
                                1,
                                false,
                                false,
                                false
                        ),
                        new AdminRecommendationCandidateDiagnosticResponse.ServiceDiagnostic(
                                2736L,
                                "동구 청년 컬처페이 지원사업",
                                "BOKJIRO_LOCAL",
                                "기타",
                                "주거·자립",
                                List.of("개인", "가구"),
                                List.of("현금", "서비스(의료)"),
                                List.of("0013003", "0013006"),
                                List.of("미취업자", "(예비)창업자"),
                                List.of("0049005", "0049006"),
                                List.of("대학 재학", "대졸 예정"),
                                List.of("0014003", "0014008"),
                                List.of("기초생활수급자", "지역인재"),
                                "0055003",
                                "제한없음",
                                "0043002",
                                "연소득",
                                true,
                                false,
                                true,
                                false,
                                2,
                                null,
                                true,
                                false,
                                2,
                                null,
                                true,
                                2,
                                true,
                                false,
                                "SCORED_BUT_NOT_IN_SAVED_BATCH",
                                true,
                                true,
                                true,
                                10.0,
                                10.0,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                0.43,
                                null,
                                0.43,
                                1.0,
                                0.43,
                                "JOB",
                                0.03,
                                true,
                                0.03,
                                0.40,
                                8,
                                false,
                                false,
                                false
                        )
                )
        ));

        mockMvc.perform(post("/api/admin/dashboard/recommendation-diagnostics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                  {
                                    "userKey": "user-key-1",
                                    "serviceIds": [3686, 2736]
                                  }
                                  """)
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userKey").value("user-key-1"))
                .andExpect(jsonPath("$.data.accountOrigin").value("REAL_USER"))
                .andExpect(jsonPath("$.data.clusterId").value("youth_all"))
                .andExpect(jsonPath("$.data.rerankTraceMode").value("PRE_AI_POST_SCORING"))
                .andExpect(jsonPath("$.data.baseCandidateCount").value(150))
                .andExpect(jsonPath("$.data.postScoringCandidateCount").value(47))
                .andExpect(jsonPath("$.data.services[0].serviceId").value(3686))
                .andExpect(jsonPath("$.data.services[0].dropStage").value("PRESENT_IN_SAVED_BATCH"))
                .andExpect(jsonPath("$.data.services[0].latestSavedRank").value(1))
                .andExpect(jsonPath("$.data.services[0].latestSavedAiReason").value("청년 직무 경험과 직접 연결됨"))
                .andExpect(jsonPath("$.data.services[1].serviceId").value(2736))
                .andExpect(jsonPath("$.data.services[1].gov24ServiceFieldLabel").value("주거·자립"))
                .andExpect(jsonPath("$.data.services[1].gov24UserTypeTokens[0]").value("개인"))
                .andExpect(jsonPath("$.data.services[1].gov24BenefitTypeTokens[1]").value("서비스(의료)"))
                .andExpect(jsonPath("$.data.services[1].youthEmploymentRequirementCodes[0]").value("0013003"))
                .andExpect(jsonPath("$.data.services[1].youthEmploymentRequirementLabels[1]").value("(예비)창업자"))
                .andExpect(jsonPath("$.data.services[1].youthEducationRequirementCodes[0]").value("0049005"))
                .andExpect(jsonPath("$.data.services[1].youthEducationRequirementLabels[1]").value("대졸 예정"))
                .andExpect(jsonPath("$.data.services[1].youthSpecialRequirementCodes[0]").value("0014003"))
                .andExpect(jsonPath("$.data.services[1].youthSpecialRequirementLabels[1]").value("지역인재"))
                .andExpect(jsonPath("$.data.services[1].youthMaritalStatusCode").value("0055003"))
                .andExpect(jsonPath("$.data.services[1].youthMaritalStatusLabel").value("제한없음"))
                .andExpect(jsonPath("$.data.services[1].youthIncomeConditionTypeCode").value("0043002"))
                .andExpect(jsonPath("$.data.services[1].youthIncomeConditionTypeLabel").value("연소득"))
                .andExpect(jsonPath("$.data.services[1].inMergedCandidates").value(true))
                .andExpect(jsonPath("$.data.services[1].inLatestSavedBatch").value(false))
                .andExpect(jsonPath("$.data.services[1].dropStage").value("SCORED_BUT_NOT_IN_SAVED_BATCH"));

        then(adminDashboardRecommendationDiagnosticService).should()
                .getRecommendationDiagnostics("user-key-1", List.of(3686L, 2736L));
    }

    @Test
    @DisplayName("recommendation diagnostics API는 userKey 형식 위반 시 400을 반환한다")
    void adminEndpointRejectsInvalidRecommendationDiagnosticsUserKey() throws Exception {
        mockAuthenticatedToken("admin-token", List.of(
                new SimpleGrantedAuthority("ROLE_USER"),
                new SimpleGrantedAuthority("ROLE_ADMIN")
        ));

        mockMvc.perform(post("/api/admin/dashboard/recommendation-diagnostics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                  {
                                    "userKey": "user-key-1\\r\\nx",
                                    "serviceIds": [3686]
                                  }
                                  """)
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("C001"));
    }

    @Test
    @DisplayName("관리자 토큰으로 collect 실패 상세 API를 호출하면 collect failure 응답을 반환한다")
    void adminEndpointAllowsCollectFailures() throws Exception {
        mockAuthenticatedToken("admin-token", List.of(
                new SimpleGrantedAuthority("ROLE_USER"),
                new SimpleGrantedAuthority("ROLE_ADMIN")
        ));
        given(adminDashboardCollectService.getCollectFailures(14, 3))
                .willReturn(new AdminCollectFailureResponse(
                        LocalDateTime.of(2026, 5, 3, 16, 30),
                        14,
                        6,
                        2,
                        List.of(
                                new AdminCollectFailureResponse.JobBreakdown(
                                        "BOKJIRO_LOCAL",
                                        4,
                                        1,
                                        LocalDateTime.of(2026, 5, 3, 9, 0)
                                )
                        ),
                        List.of(
                                new AdminCollectFailureResponse.JobStreak(
                                        "BOKJIRO_LOCAL",
                                        "FAILED",
                                        2,
                                        LocalDateTime.of(2026, 5, 3, 9, 0)
                                )
                        ),
                        List.of(
                                new AdminCollectFailureResponse.ErrorCodeBreakdown("COL001", 5)
                        ),
                        List.of(
                                new AdminCollectFailureResponse.FailureSample(
                                        "BOKJIRO_LOCAL",
                                        "FAILED",
                                        "COL001",
                                        "rate limited",
                                        LocalDateTime.of(2026, 5, 3, 9, 0),
                                        LocalDateTime.of(2026, 5, 3, 9, 1),
                                        0,
                                        0,
                                        1
                                )
                        ),
                        List.of(
                                new AdminCollectFailureResponse.CircuitStatus(
                                        "BOKJIRO_LOCAL",
                                        true,
                                        60000L,
                                        LocalDateTime.of(2026, 5, 3, 17, 0)
                                )
                        ),
                        List.of(
                                new AdminCollectFailureResponse.CollectLane(
                                        "YOUTH",
                                        "온통청년",
                                        "SCHEDULED",
                                        "SNAPSHOT",
                                        "/api/admin/collect/youth",
                                        "매일 02:00 Asia/Seoul",
                                        "HEAVY",
                                        "핵심 청년 정책 목록입니다.",
                                        List.of(
                                                new AdminCollectFailureResponse.ConfigEntry("자동 실행 일정", "0 0 2 * * * @ Asia/Seoul"),
                                                new AdminCollectFailureResponse.ConfigEntry("목록 요청 간격", "300ms")
                                        ),
                                        new AdminCollectFailureResponse.LatestRun(
                                                "SUCCESS",
                                                LocalDateTime.of(2026, 5, 3, 8, 50),
                                                LocalDateTime.of(2026, 5, 3, 8, 55),
                                                2500,
                                                2490,
                                                5,
                                                0,
                                                5
                                        )
                                )
                        )
                ));

        mockMvc.perform(get("/api/admin/dashboard/collect-failures")
                        .param("summaryWindowDays", "14")
                        .param("limit", "3")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.windowDays").value(14))
                .andExpect(jsonPath("$.data.failedJobsInWindow").value(6))
                .andExpect(jsonPath("$.data.partialSuccessJobsInWindow").value(2))
                .andExpect(jsonPath("$.data.jobBreakdowns[0].jobName").value("BOKJIRO_LOCAL"))
                .andExpect(jsonPath("$.data.currentJobStreaks[0].streakStatus").value("FAILED"))
                .andExpect(jsonPath("$.data.currentJobStreaks[0].streakCount").value(2))
                .andExpect(jsonPath("$.data.errorCodeBreakdowns[0].errorCode").value("COL001"))
                .andExpect(jsonPath("$.data.recentSamples[0].errorMessage").value("rate limited"))
                .andExpect(jsonPath("$.data.circuitStatuses[0].circuitKey").value("BOKJIRO_LOCAL"))
                .andExpect(jsonPath("$.data.circuitStatuses[0].open").value(true))
                .andExpect(jsonPath("$.data.circuitStatuses[0].remainingMs").value(60000))
                .andExpect(jsonPath("$.data.collectSourceLanes[0].laneKey").value("YOUTH"))
                .andExpect(jsonPath("$.data.collectSourceLanes[0].executionMode").value("SCHEDULED"))
                .andExpect(jsonPath("$.data.collectSourceLanes[0].configEntries[0].label").value("자동 실행 일정"))
                .andExpect(jsonPath("$.data.collectSourceLanes[0].configEntries[1].value").value("300ms"))
                .andExpect(jsonPath("$.data.collectSourceLanes[0].latestRun.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.collectSourceLanes[0].latestRun.requestedCount").value(2500));

        then(adminDashboardCollectService).should().getCollectFailures(14, 3);
    }

    @Test
    @DisplayName("관리자 토큰으로 Gov24 detail sourceId override를 호출하면 단건 detail 수집을 실행한다")
    void adminEndpointAllowsGov24DetailForSourceId() throws Exception {
        mockAuthenticatedToken("admin-token", List.of(
                new SimpleGrantedAuthority("ROLE_USER"),
                new SimpleGrantedAuthority("ROLE_ADMIN")
        ));
        given(collectAdminService.collect(CollectSource.GOV24_DETAIL, "305000000168"))
                .willReturn(CollectResult.of(1, 1, 0, 0, 0));

        mockMvc.perform(post("/api/admin/collect/gov24-details")
                        .header("Authorization", "Bearer admin-token")
                        .param("sourceId", "305000000168"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value("정부24 상세 수집 완료 requested=1 saved=1 skipped=0 failed=0"));

        then(collectAdminService).should().collect(CollectSource.GOV24_DETAIL, "305000000168");
    }

    @Test
    @DisplayName("관리자 토큰으로 detail refresh 관리자 API를 호출하면 refresh 수집 서비스를 실행한다")
    void adminEndpointAllowsDetailRefresh() throws Exception {
        mockAuthenticatedToken("admin-token", List.of(
                new SimpleGrantedAuthority("ROLE_USER"),
                new SimpleGrantedAuthority("ROLE_ADMIN")
        ));
        doNothing().when(collectAdminService).collect(CollectSource.BOKJIRO_DETAIL_REFRESH);

        mockMvc.perform(post("/api/admin/collect/bokjiro-details-refresh")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value("복지로 상세 재수집 완료"));

        then(collectAdminService).should().collect(CollectSource.BOKJIRO_DETAIL_REFRESH);
    }

    @Test
    @DisplayName("관리자 토큰으로 detail refresh sourceId override를 호출하면 단건 refresh 수집을 실행한다")
    void adminEndpointAllowsDetailRefreshForSourceId() throws Exception {
        mockAuthenticatedToken("admin-token", List.of(
                new SimpleGrantedAuthority("ROLE_USER"),
                new SimpleGrantedAuthority("ROLE_ADMIN")
        ));
        given(collectAdminService.collect(CollectSource.BOKJIRO_DETAIL_REFRESH, "WLF00004717"))
                .willReturn(CollectResult.of(1, 1, 0, 0, 0));

        mockMvc.perform(post("/api/admin/collect/bokjiro-details-refresh")
                        .header("Authorization", "Bearer admin-token")
                        .param("sourceId", "WLF00004717"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value("복지로 상세 재수집 완료 requested=1 saved=1 skipped=0 failed=0"));

        then(collectAdminService).should().collect(CollectSource.BOKJIRO_DETAIL_REFRESH, "WLF00004717");
    }

    @Test
    @DisplayName("관리자 토큰으로 알 수 없는 collect source 를 호출하면 400을 반환한다")
    void adminEndpointRejectsUnknownCollectSource() throws Exception {
        mockAuthenticatedToken("admin-token", List.of(
                new SimpleGrantedAuthority("ROLE_USER"),
                new SimpleGrantedAuthority("ROLE_ADMIN")
        ));

        mockMvc.perform(post("/api/admin/collect/unknown-source")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("C001"));
    }

    @Test
    @DisplayName("관리자 토큰으로 복지로 sidecar backfill API를 호출하면 backfill 서비스를 실행한다")
    void adminEndpointAllowsBokjiroSidecarBackfill() throws Exception {
        mockAuthenticatedToken("admin-token", List.of(
                new SimpleGrantedAuthority("ROLE_USER"),
                new SimpleGrantedAuthority("ROLE_ADMIN")
        ));
        given(normalizedPolicySidecarBackfillService.backfillBokjiroListSidecars(25))
                .willReturn(new NormalizedPolicySidecarBackfillService.BackfillResult(10, 8, 1, 1));

        mockMvc.perform(post("/api/admin/collect/bokjiro-sidecars-backfill")
                        .param("scope", "list")
                        .param("limitPerSource", "25")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.scope").value("list"))
                .andExpect(jsonPath("$.data.limitPerSource").value(25))
                .andExpect(jsonPath("$.data.scannedCount").value(10))
                .andExpect(jsonPath("$.data.upsertedCount").value(8))
                .andExpect(jsonPath("$.data.missingServiceCount").value(1))
                .andExpect(jsonPath("$.data.failedCount").value(1));

        then(normalizedPolicySidecarBackfillService).should().backfillBokjiroListSidecars(25);
    }

    @Test
    @DisplayName("GOV24 sidecar backfill API는 limitPerSource=0을 전체 처리 값으로 전달한다")
    void adminEndpointAllowsGov24SidecarBackfillUnlimited() throws Exception {
        mockAuthenticatedToken("admin-token", List.of(
                new SimpleGrantedAuthority("ROLE_USER"),
                new SimpleGrantedAuthority("ROLE_ADMIN")
        ));
        given(normalizedPolicySidecarBackfillService.backfillGov24ListSidecars(0))
                .willReturn(new NormalizedPolicySidecarBackfillService.BackfillResult(10_945, 10_945, 0, 0));
        given(normalizedPolicySidecarBackfillService.backfillGov24ListRegions(0))
                .willReturn(new NormalizedPolicySidecarBackfillService.BackfillResult(10_945, 10_945, 0, 0));
        given(normalizedPolicySidecarBackfillService.backfillGov24SupportConditionSidecars(0))
                .willReturn(new NormalizedPolicySidecarBackfillService.BackfillResult(10_945, 10_945, 0, 0));

        mockMvc.perform(post("/api/admin/collect/gov24-sidecars-backfill")
                        .param("scope", "all")
                        .param("limitPerSource", "0")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.scope").value("gov24-all"))
                .andExpect(jsonPath("$.data.limitPerSource").value(0))
                .andExpect(jsonPath("$.data.scannedCount").value(32_835))
                .andExpect(jsonPath("$.data.upsertedCount").value(32_835))
                .andExpect(jsonPath("$.data.missingServiceCount").value(0))
                .andExpect(jsonPath("$.data.failedCount").value(0));

        then(normalizedPolicySidecarBackfillService).should().backfillGov24ListSidecars(0);
        then(normalizedPolicySidecarBackfillService).should().backfillGov24ListRegions(0);
        then(normalizedPolicySidecarBackfillService).should().backfillGov24SupportConditionSidecars(0);
    }

    @Test
    @DisplayName("GOV24 sidecar backfill API는 list raw 기반 지역 row만 보강할 수 있다")
    void adminEndpointAllowsGov24RegionBackfill() throws Exception {
        mockAuthenticatedToken("admin-token", List.of(
                new SimpleGrantedAuthority("ROLE_USER"),
                new SimpleGrantedAuthority("ROLE_ADMIN")
        ));
        given(normalizedPolicySidecarBackfillService.backfillGov24ListRegions(0))
                .willReturn(new NormalizedPolicySidecarBackfillService.BackfillResult(10_945, 8_000, 0, 0));

        mockMvc.perform(post("/api/admin/collect/gov24-sidecars-backfill")
                        .param("scope", "regions")
                        .param("limitPerSource", "0")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.scope").value("gov24-regions"))
                .andExpect(jsonPath("$.data.limitPerSource").value(0))
                .andExpect(jsonPath("$.data.scannedCount").value(10_945))
                .andExpect(jsonPath("$.data.upsertedCount").value(8_000))
                .andExpect(jsonPath("$.data.missingServiceCount").value(0))
                .andExpect(jsonPath("$.data.failedCount").value(0));

        then(normalizedPolicySidecarBackfillService).should().backfillGov24ListRegions(0);
    }

    @Test
    @DisplayName("GOV24 sidecar backfill API는 누락된 list summary slot만 보강할 수 있다")
    void adminEndpointAllowsGov24MissingListSidecarBackfill() throws Exception {
        mockAuthenticatedToken("admin-token", List.of(
                new SimpleGrantedAuthority("ROLE_USER"),
                new SimpleGrantedAuthority("ROLE_ADMIN")
        ));
        given(normalizedPolicySidecarBackfillService.backfillGov24MissingListSidecars(0))
                .willReturn(new NormalizedPolicySidecarBackfillService.BackfillResult(128, 128, 0, 0));

        mockMvc.perform(post("/api/admin/collect/gov24-sidecars-backfill")
                        .param("scope", "list")
                        .param("missingOnly", "true")
                        .param("limitPerSource", "0")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.scope").value("gov24-list-missing"))
                .andExpect(jsonPath("$.data.limitPerSource").value(0))
                .andExpect(jsonPath("$.data.scannedCount").value(128))
                .andExpect(jsonPath("$.data.upsertedCount").value(128))
                .andExpect(jsonPath("$.data.missingServiceCount").value(0))
                .andExpect(jsonPath("$.data.failedCount").value(0));

        then(normalizedPolicySidecarBackfillService).should().backfillGov24MissingListSidecars(0);
    }

    @Test
    @DisplayName("지원하지 않는 backfill scope는 400을 반환한다")
    void adminEndpointRejectsUnknownBackfillScope() throws Exception {
        mockAuthenticatedToken("admin-token", List.of(
                new SimpleGrantedAuthority("ROLE_USER"),
                new SimpleGrantedAuthority("ROLE_ADMIN")
        ));

        mockMvc.perform(post("/api/admin/collect/bokjiro-sidecars-backfill")
                        .param("scope", "unknown")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("C001"));
    }

    @Test
    @DisplayName("복지로 sidecar backfill API는 limitPerSource 상한 초과 시 400을 반환한다")
    void adminEndpointRejectsTooLargeSidecarBackfillLimit() throws Exception {
        mockAuthenticatedToken("admin-token", List.of(
                new SimpleGrantedAuthority("ROLE_USER"),
                new SimpleGrantedAuthority("ROLE_ADMIN")
        ));

        mockMvc.perform(post("/api/admin/collect/bokjiro-sidecars-backfill")
                        .param("scope", "list")
                        .param("limitPerSource", "1001")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("C001"));
    }

    @Test
    @DisplayName("관리자 토큰으로 복지로 detail gap fill API를 호출하면 여러 라운드 수집을 실행한다")
    void adminEndpointAllowsBokjiroDetailGapFill() throws Exception {
        mockAuthenticatedToken("admin-token", List.of(
                new SimpleGrantedAuthority("ROLE_USER"),
                new SimpleGrantedAuthority("ROLE_ADMIN")
        ));
        given(collectAdminService.collectBokjiroDetailGapFill(4, 190))
                .willReturn(new com.example.welfare.collect.service.BokjiroDetailCollectService.GapFillResult(
                        4, 3, 190, 150, 120, 30, 0, true
                ));

        mockMvc.perform(post("/api/admin/collect/bokjiro-details-gap-fill")
                        .param("rounds", "4")
                        .param("maxCallsPerRound", "190")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.roundsRequested").value(4))
                .andExpect(jsonPath("$.data.roundsExecuted").value(3))
                .andExpect(jsonPath("$.data.maxCallsPerRound").value(190))
                .andExpect(jsonPath("$.data.requestedCount").value(150))
                .andExpect(jsonPath("$.data.savedCount").value(120))
                .andExpect(jsonPath("$.data.skippedCount").value(30))
                .andExpect(jsonPath("$.data.failedCount").value(0))
                .andExpect(jsonPath("$.data.stoppedAfterNoSaves").value(true));

        then(collectAdminService).should().collectBokjiroDetailGapFill(4, 190);
    }

    @Test
    @DisplayName("복지로 detail gap fill API는 잘못된 rounds 또는 maxCallsPerRound 에 400을 반환한다")
    void adminEndpointRejectsInvalidBokjiroDetailGapFillParams() throws Exception {
        mockAuthenticatedToken("admin-token", List.of(
                new SimpleGrantedAuthority("ROLE_USER"),
                new SimpleGrantedAuthority("ROLE_ADMIN")
        ));

        mockMvc.perform(post("/api/admin/collect/bokjiro-details-gap-fill")
                        .param("rounds", "0")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("C001"));

        mockMvc.perform(post("/api/admin/collect/bokjiro-details-gap-fill")
                        .param("rounds", "11")
                        .param("maxCallsPerRound", "100")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("C001"));

        mockMvc.perform(post("/api/admin/collect/bokjiro-details-gap-fill")
                        .param("rounds", "1")
                        .param("maxCallsPerRound", "1001")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("C001"));
    }

    @Test
    @DisplayName("복지로 detail gap fill 이 연속 429로 진전 없이 중단되면 500/COL001 을 반환한다")
    void adminEndpointSurfacesBokjiroDetailGapFillRateLimitFailure() throws Exception {
        mockAuthenticatedToken("admin-token", List.of(
                new SimpleGrantedAuthority("ROLE_USER"),
                new SimpleGrantedAuthority("ROLE_ADMIN")
        ));
        given(collectAdminService.collectBokjiroDetailGapFill(1, 95))
                .willThrow(new CustomException(ErrorCode.COLLECT_API_FAILED));

        mockMvc.perform(post("/api/admin/collect/bokjiro-details-gap-fill")
                        .param("rounds", "1")
                        .param("maxCallsPerRound", "95")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("COL001"));
    }

    @Test
    @DisplayName("관리자 토큰으로 PII 백필 API를 호출하면 백필 서비스를 실행한다")
    void adminEndpointAllowsPiiBackfill() throws Exception {
        mockAuthenticatedToken("admin-token", List.of(
                new SimpleGrantedAuthority("ROLE_USER"),
                new SimpleGrantedAuthority("ROLE_ADMIN")
        ));
        given(userPiiBackfillService.backfillMissingEncryptedFields())
                .willReturn(new UserPiiBackfillResponse(3, 2, 2, 1, 1, 1));

        mockMvc.perform(post("/api/admin/users/pii-backfill")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.processedCount").value(3))
                .andExpect(jsonPath("$.data.updatedUserCount").value(2))
                .andExpect(jsonPath("$.data.skippedCount").value(1));

        then(userPiiBackfillService).should().backfillMissingEncryptedFields();
    }

    @Test
    @DisplayName("관리자 토큰으로 forced logout API를 호출하면 user session revoke를 실행한다")
    void adminEndpointAllowsForcedLogout() throws Exception {
        mockAuthenticatedToken("admin-token", List.of(
                new SimpleGrantedAuthority("ROLE_USER"),
                new SimpleGrantedAuthority("ROLE_ADMIN")
        ));
        given(userKeyLookupService.requireExistingUserIdByUserKey("user-key-1")).willReturn(1L);

        mockMvc.perform(post("/api/admin/users/forced-logout")
                        .contentType("application/json")
                        .content("""
                                {
                                  "userKey": "user-key-1"
                                }
                                """)
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userKey").value("user-key-1"))
                .andExpect(jsonPath("$.data.accepted").value(true));

        then(userSessionRevocationService).should().revokeUserSessions(org.mockito.BDDMockito.eq("user-key-1"), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    @DisplayName("forced logout API는 빈 userKey에 400을 반환한다")
    void adminEndpointRejectsBlankForcedLogoutUserKey() throws Exception {
        mockAuthenticatedToken("admin-token", List.of(
                new SimpleGrantedAuthority("ROLE_USER"),
                new SimpleGrantedAuthority("ROLE_ADMIN")
        ));

        mockMvc.perform(post("/api/admin/users/forced-logout")
                        .contentType("application/json")
                        .content("""
                                {
                                  "userKey": "   "
                                }
                                """)
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("C001"));
    }

    @Test
    @DisplayName("forced logout API는 없는 userKey에 404를 반환한다")
    void adminEndpointRejectsMissingForcedLogoutUser() throws Exception {
        mockAuthenticatedToken("admin-token", List.of(
                new SimpleGrantedAuthority("ROLE_USER"),
                new SimpleGrantedAuthority("ROLE_ADMIN")
        ));
        given(userKeyLookupService.requireExistingUserIdByUserKey("missing-user"))
                .willThrow(new CustomException(ErrorCode.USER_NOT_FOUND));

        mockMvc.perform(post("/api/admin/users/forced-logout")
                        .contentType("application/json")
                        .content("""
                                {
                                  "userKey": "missing-user"
                                }
                                """)
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("U002"));
    }

    @Test
    @DisplayName("forced logout API는 32자를 넘는 userKey에 400을 반환한다")
    void adminEndpointRejectsTooLongForcedLogoutUserKey() throws Exception {
        mockAuthenticatedToken("admin-token", List.of(
                new SimpleGrantedAuthority("ROLE_USER"),
                new SimpleGrantedAuthority("ROLE_ADMIN")
        ));

        mockMvc.perform(post("/api/admin/users/forced-logout")
                        .contentType("application/json")
                        .content("""
                                {
                                  "userKey": "123456789012345678901234567890123"
                                }
                                """)
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("C001"));
    }

    @Test
    @DisplayName("관리자 토큰으로 metadata user_key 백필 API를 호출하면 백필 서비스를 실행한다")
    void adminEndpointAllowsMetadataUserKeyBackfill() throws Exception {
        mockAuthenticatedToken("admin-token", List.of(
                new SimpleGrantedAuthority("ROLE_USER"),
                new SimpleGrantedAuthority("ROLE_ADMIN")
        ));
        given(userMetadataUserKeyBackfillService.backfillMissingUserKeys())
                .willReturn(new UserMetadataUserKeyBackfillResponse(5, 5, 3, 2));

        mockMvc.perform(post("/api/admin/users/metadata-user-key-backfill")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.processedCount").value(5))
                .andExpect(jsonPath("$.data.updatedRowCount").value(5))
                .andExpect(jsonPath("$.data.attributeUpdatedCount").value(3))
                .andExpect(jsonPath("$.data.priorityUpdatedCount").value(2));

        then(userMetadataUserKeyBackfillService).should().backfillMissingUserKeys();
    }

    @Test
    @DisplayName("관리자 토큰으로 pii sync replay API를 호출하면 replay 서비스를 실행한다")
    void adminEndpointAllowsPiiSyncReplay() throws Exception {
        mockAuthenticatedToken("admin-token", List.of(
                new SimpleGrantedAuthority("ROLE_USER"),
                new SimpleGrantedAuthority("ROLE_ADMIN")
        ));
        given(userPiiSyncReplayService.replay("user-key-1", 25))
                .willReturn(new UserPiiSyncReplayResponse(1, 1, 0, 0));

        mockMvc.perform(post("/api/admin/users/pii-sync-replay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                  {
                                    "userKey": "user-key-1",
                                    "limit": 25
                                  }
                                  """)
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.attemptedCount").value(1))
                .andExpect(jsonPath("$.data.syncedCount").value(1))
                .andExpect(jsonPath("$.data.failedCount").value(0))
                .andExpect(jsonPath("$.data.missingCount").value(0));

        then(userPiiSyncReplayService).should().replay("user-key-1", 25);
    }

    @Test
    @DisplayName("관리자 토큰으로 pii encryption rotation API를 호출하면 legacy 암호문 회전을 실행한다")
    void adminEndpointAllowsPiiEncryptionRotation() throws Exception {
        mockAuthenticatedToken("admin-token", List.of(
                new SimpleGrantedAuthority("ROLE_USER"),
                new SimpleGrantedAuthority("ROLE_ADMIN")
        ));
        given(userPiiBackfillService.rotateLegacyEncryptedFields())
                .willReturn(new UserPiiEncryptionRotationResponse(
                        10,
                        2,
                        0,
                        10,
                        1,
                        0
                ));

        mockMvc.perform(post("/api/admin/users/pii-encryption-rotation")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userPiiProcessedCount").value(10))
                .andExpect(jsonPath("$.data.userPiiUpdatedCount").value(2))
                .andExpect(jsonPath("$.data.queueUpdatedCount").value(10));

        then(userPiiBackfillService).should().rotateLegacyEncryptedFields();
    }

    @Test
    @DisplayName("pii sync replay API는 범위를 벗어난 limit에 400을 반환한다")
    void adminEndpointRejectsInvalidPiiSyncReplayLimit() throws Exception {
        mockAuthenticatedToken("admin-token", List.of(
                new SimpleGrantedAuthority("ROLE_USER"),
                new SimpleGrantedAuthority("ROLE_ADMIN")
        ));

        mockMvc.perform(post("/api/admin/users/pii-sync-replay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                  {
                                    "limit": 0
                                  }
                                  """)
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("C001"));
    }

    @Test
    @DisplayName("관리자 토큰으로 pii sync status API를 호출하면 queue 상태 요약을 반환한다")
    void adminEndpointAllowsPiiSyncStatus() throws Exception {
        mockAuthenticatedToken("admin-token", List.of(
                new SimpleGrantedAuthority("ROLE_USER"),
                new SimpleGrantedAuthority("ROLE_ADMIN")
        ));
        given(userPiiSyncStatusService.getStatus(3))
                .willReturn(new UserPiiSyncStatusResponse(
                        2,
                        1,
                        9,
                        "pending-user",
                        LocalDateTime.of(2026, 4, 28, 20, 0, 0),
                        "failed-user",
                        LocalDateTime.of(2026, 4, 28, 20, 5, 0),
                        LocalDateTime.of(2026, 4, 28, 20, 10, 0),
                        List.of()
                ));

        mockMvc.perform(get("/api/admin/users/pii-sync-status")
                        .param("failedSampleLimit", "3")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.pendingCount").value(2))
                .andExpect(jsonPath("$.data.failedCount").value(1))
                .andExpect(jsonPath("$.data.oldestPendingUserKeyHash").value("pending-user"))
                .andExpect(jsonPath("$.data.oldestFailedUserKeyHash").value("failed-user"))
                .andExpect(jsonPath("$.data.oldestPendingUserKey").doesNotExist())
                .andExpect(jsonPath("$.data.oldestFailedUserKey").doesNotExist())
                .andExpect(jsonPath("$.data.failedSamples").isArray());

        then(userPiiSyncStatusService).should().getStatus(3);
    }

    @Test
    @DisplayName("pii sync status API는 범위를 벗어난 failedSampleLimit에 400을 반환한다")
    void adminEndpointRejectsInvalidPiiSyncStatusLimit() throws Exception {
        mockAuthenticatedToken("admin-token", List.of(
                new SimpleGrantedAuthority("ROLE_USER"),
                new SimpleGrantedAuthority("ROLE_ADMIN")
        ));

        mockMvc.perform(get("/api/admin/users/pii-sync-status")
                        .param("failedSampleLimit", "21")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("C001"));
    }

    @Test
    @DisplayName("대시보드 요약 API는 범위를 벗어난 summaryWindowDays와 trendWindowDays에 400을 반환한다")
    void adminEndpointRejectsInvalidDashboardWindowParams() throws Exception {
        mockAuthenticatedToken("admin-token", List.of(
                new SimpleGrantedAuthority("ROLE_USER"),
                new SimpleGrantedAuthority("ROLE_ADMIN")
        ));

        mockMvc.perform(get("/api/admin/dashboard/summary")
                        .param("summaryWindowDays", "366")
                        .param("trendWindowDays", "0", "30")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("C001"));
    }

    @Test
    @DisplayName("관리자 토큰으로 stale notification backlog hide API를 호출할 수 있다")
    void adminEndpointAllowsStaleNotificationHide() throws Exception {
        mockAuthenticatedToken("admin-token", List.of(
                new SimpleGrantedAuthority("ROLE_USER"),
                new SimpleGrantedAuthority("ROLE_ADMIN")
        ));
        given(adminNotificationBacklogService.hideStaleAlerts(org.mockito.ArgumentMatchers.any()))
                .willReturn(new AdminNotificationStaleHideResponse(
                        5,
                        "DEADLINE_REMINDER",
                        "북마크한 정책 마감이 임박했어요",
                        "/policies/2622",
                        14
                ));

        mockMvc.perform(post("/api/admin/dashboard/notification-backlog/hide-stale")
                        .header("Authorization", "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "kind": "DEADLINE_REMINDER",
                                  "title": "북마크한 정책 마감이 임박했어요",
                                  "deeplinkUrl": "/policies/2622",
                                  "olderThanDays": 14
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.hiddenCount").value(5))
                .andExpect(jsonPath("$.data.kind").value("DEADLINE_REMINDER"))
                .andExpect(jsonPath("$.data.deeplinkUrl").value("/policies/2622"));
    }

    @Test
    @DisplayName("stale notification backlog hide API는 외부 deeplinkUrl에 400을 반환한다")
    void adminEndpointRejectsExternalStaleNotificationHideDeeplink() throws Exception {
        mockAuthenticatedToken("admin-token", List.of(
                new SimpleGrantedAuthority("ROLE_USER"),
                new SimpleGrantedAuthority("ROLE_ADMIN")
        ));

        mockMvc.perform(post("/api/admin/dashboard/notification-backlog/hide-stale")
                        .header("Authorization", "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "kind": "DEADLINE_REMINDER",
                                  "title": "북마크한 정책 마감이 임박했어요",
                                  "deeplinkUrl": "https://evil.example/policies/2622",
                                  "olderThanDays": 14
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("C001"));
    }

    @Test
    @DisplayName("stale notification backlog hide API는 protocol-relative deeplinkUrl에 400을 반환한다")
    void adminEndpointRejectsProtocolRelativeStaleNotificationHideDeeplink() throws Exception {
        mockAuthenticatedToken("admin-token", List.of(
                new SimpleGrantedAuthority("ROLE_USER"),
                new SimpleGrantedAuthority("ROLE_ADMIN")
        ));

        mockMvc.perform(post("/api/admin/dashboard/notification-backlog/hide-stale")
                        .header("Authorization", "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "kind": "DEADLINE_REMINDER",
                                  "title": "북마크한 정책 마감이 임박했어요",
                                  "deeplinkUrl": "//evil.example/policies/2622",
                                  "olderThanDays": 14
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("C001"));
    }

    private void mockAuthenticatedToken(String token,
                                        List<SimpleGrantedAuthority> authorities) {
        doNothing().when(jwtUtil).validate(token);
        given(userSessionRevocationService.isAccessAllowed(token)).willReturn(true);
        given(jwtUtil.getAuthenticatedUser(token)).willReturn(new AuthenticatedUser(1L, "user-key-1"));
        given(jwtUtil.getAuthorities(token)).willReturn(List.copyOf(authorities));
        given(adminAccessAuthorityService.filterCurrentAuthorities(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyCollection()
        )).willReturn(List.copyOf(authorities));
    }
}
