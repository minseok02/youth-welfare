package com.example.welfare.admin;

import com.example.welfare.admin.dashboard.controller.AdminDashboardController;
import com.example.welfare.admin.dashboard.dto.AdminCollectFailureResponse;
import com.example.welfare.admin.dashboard.dto.AdminRecommendationCandidateDiagnosticResponse;
import com.example.welfare.admin.dashboard.dto.AdminRecommendationBreakdownResponse;
import com.example.welfare.admin.dashboard.dto.AdminSearchFailureResponse;
import com.example.welfare.admin.dashboard.dto.AdminDashboardResponse;
import com.example.welfare.admin.dashboard.service.AdminDashboardCollectService;
import com.example.welfare.admin.dashboard.service.AdminDashboardRecommendationDiagnosticService;
import com.example.welfare.admin.dashboard.service.AdminDashboardRecommendationService;
import com.example.welfare.admin.dashboard.service.AdminDashboardSearchService;
import com.example.welfare.admin.dashboard.service.AdminDashboardSummaryService;
import com.example.welfare.collect.controller.CollectAdminController;
import com.example.welfare.collect.normalization.NormalizedPolicySidecarBackfillService;
import com.example.welfare.collect.service.CollectAdminService;
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
import com.example.welfare.policy.controller.PolicyAdminController;
import com.example.welfare.policy.dto.PolicyCategoryAuditResponse;
import com.example.welfare.policy.dto.PolicyEmbeddingRefreshResponse;
import com.example.welfare.policy.dto.PolicyReferenceUrlBackfillResponse;
import com.example.welfare.policy.dto.PolicyRetrievalEvaluationCompareResponse;
import com.example.welfare.policy.dto.PolicyRetrievalEvaluationResponse;
import com.example.welfare.policy.dto.PolicyRetrievalQualityGateResponse;
import com.example.welfare.policy.service.PolicyCategoryAuditService;
import com.example.welfare.policy.service.PolicyEmbeddingAdminService;
import com.example.welfare.policy.service.PolicyReferenceUrlAdminService;
import com.example.welfare.policy.service.PolicyRetrievalEvaluationExportService;
import com.example.welfare.policy.service.PolicyRetrievalEvaluationService;
import com.example.welfare.policy.service.PolicyRetrievalQualityGateService;
import com.example.welfare.policy.service.SearchYouthRelevanceService;
import com.example.welfare.user.controller.UserAdminController;
import com.example.welfare.user.dto.response.UserMetadataUserKeyBackfillResponse;
import com.example.welfare.user.dto.response.UserPiiBackfillResponse;
import com.example.welfare.user.dto.response.UserPiiSyncReplayResponse;
import com.example.welfare.user.dto.response.UserPiiSyncStatusResponse;
import com.example.welfare.user.service.UserMetadataUserKeyBackfillService;
import com.example.welfare.user.service.UserPiiBackfillService;
import com.example.welfare.user.service.UserKeyLookupService;
import com.example.welfare.user.service.UserPiiSyncReplayService;
import com.example.welfare.user.service.UserPiiSyncStatusService;
import com.example.welfare.user.service.UserSessionRevocationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.context.annotation.Import;
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

    @MockBean
    private CollectBatchService collectBatchService;
    @MockBean
    private CollectAdminService collectAdminService;
    @MockBean
    private NormalizedPolicySidecarBackfillService normalizedPolicySidecarBackfillService;
    @MockBean
    private YouthDetailCollectService youthDetailCollectService;
    @MockBean
    private SearchYouthRelevanceService searchYouthRelevanceService;
    @MockBean
    private PolicyEmbeddingAdminService policyEmbeddingAdminService;
    @MockBean
    private PolicyReferenceUrlAdminService policyReferenceUrlAdminService;
    @MockBean
    private PolicyRetrievalEvaluationService policyRetrievalEvaluationService;
    @MockBean
    private PolicyRetrievalEvaluationExportService policyRetrievalEvaluationExportService;
    @MockBean
    private PolicyRetrievalQualityGateService policyRetrievalQualityGateService;
    @MockBean
    private PolicyCategoryAuditService policyCategoryAuditService;
    @MockBean
    private UserMetadataUserKeyBackfillService userMetadataUserKeyBackfillService;
    @MockBean
    private UserPiiBackfillService userPiiBackfillService;
    @MockBean
    private UserPiiSyncReplayService userPiiSyncReplayService;
    @MockBean
    private UserPiiSyncStatusService userPiiSyncStatusService;
    @MockBean
    private UserSessionRevocationService userSessionRevocationService;
    @MockBean
    private UserKeyLookupService userKeyLookupService;
    @MockBean
    private AdminDashboardSummaryService adminDashboardSummaryService;
    @MockBean
    private AdminDashboardSearchService adminDashboardSearchService;
    @MockBean
    private AdminDashboardRecommendationService adminDashboardRecommendationService;
    @MockBean
    private AdminDashboardRecommendationDiagnosticService adminDashboardRecommendationDiagnosticService;
    @MockBean
    private AdminDashboardCollectService adminDashboardCollectService;
    @MockBean
    private JwtUtil jwtUtil;
    @MockBean
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
                                List.of(
                                        new AdminDashboardResponse.RecommendationWeightSnapshot(
                                                "GROWTH",
                                                java.math.BigDecimal.valueOf(0.60),
                                                java.math.BigDecimal.valueOf(0.40),
                                                8
                                        )
                                )
                        ),
                        new AdminDashboardResponse.NotificationSection(1, 0, 7, 5, 1),
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
                .andExpect(jsonPath("$.data.notification.windowDays").value(7))
                .andExpect(jsonPath("$.data.notification.sentInWindow").value(5))
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
                                List.of()
                        ),
                        new AdminDashboardResponse.NotificationSection(0, 0, 14, 0, 0),
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
                                        "Gov24 서비스분야",
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
                                        "Gov24 사용자구분",
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

        mockMvc.perform(get("/api/admin/dashboard/recommendation-diagnostics")
                        .param("userKey", "user-key-1")
                        .param("serviceId", "3686", "2736")
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
                                        "핵심 청년 snapshot lane이다.",
                                        List.of(
                                                new AdminCollectFailureResponse.ConfigEntry("Scheduler", "0 0 2 * * * @ Asia/Seoul"),
                                                new AdminCollectFailureResponse.ConfigEntry("List pacing", "300ms")
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
                .andExpect(jsonPath("$.data.collectSourceLanes[0].configEntries[0].label").value("Scheduler"))
                .andExpect(jsonPath("$.data.collectSourceLanes[0].configEntries[1].value").value("300ms"))
                .andExpect(jsonPath("$.data.collectSourceLanes[0].latestRun.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.collectSourceLanes[0].latestRun.requestedCount").value(2500));

        then(adminDashboardCollectService).should().getCollectFailures(14, 3);
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
                .andExpect(jsonPath("$.data").value("복지로 상세 refresh 완료"));

        then(collectAdminService).should().collect(CollectSource.BOKJIRO_DETAIL_REFRESH);
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
                        .param("userKey", "user-key-1")
                        .param("limit", "25")
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
    @DisplayName("pii sync replay API는 범위를 벗어난 limit에 400을 반환한다")
    void adminEndpointRejectsInvalidPiiSyncReplayLimit() throws Exception {
        mockAuthenticatedToken("admin-token", List.of(
                new SimpleGrantedAuthority("ROLE_USER"),
                new SimpleGrantedAuthority("ROLE_ADMIN")
        ));

        mockMvc.perform(post("/api/admin/users/pii-sync-replay")
                        .param("limit", "0")
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
                .andExpect(jsonPath("$.data.oldestPendingUserKey").value("pending-user"))
                .andExpect(jsonPath("$.data.oldestFailedUserKey").value("failed-user"))
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

    private void mockAuthenticatedToken(String token,
                                        List<SimpleGrantedAuthority> authorities) {
        doNothing().when(jwtUtil).validate(token);
        given(userSessionRevocationService.isAccessAllowed(token)).willReturn(true);
        given(jwtUtil.getAuthenticatedUser(token)).willReturn(new AuthenticatedUser(1L, "user-key-1"));
        given(jwtUtil.getAuthorities(token)).willReturn(List.copyOf(authorities));
    }
}
