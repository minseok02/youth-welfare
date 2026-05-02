package com.example.welfare.admin;

import com.example.welfare.admin.dashboard.controller.AdminDashboardController;
import com.example.welfare.admin.dashboard.dto.AdminDashboardResponse;
import com.example.welfare.admin.dashboard.service.AdminDashboardService;
import com.example.welfare.collect.controller.CollectAdminController;
import com.example.welfare.collect.normalization.NormalizedPolicySidecarBackfillService;
import com.example.welfare.collect.service.BokjiroDetailCollectService;
import com.example.welfare.collect.service.CollectSource;
import com.example.welfare.collect.service.CollectService;
import com.example.welfare.global.auth.AuthenticatedUser;
import com.example.welfare.global.config.JacksonConfig;
import com.example.welfare.global.config.SecurityConfig;
import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.example.welfare.global.util.JwtUtil;
import com.example.welfare.policy.controller.PolicyAdminController;
import com.example.welfare.policy.service.SearchYouthRelevanceService;
import com.example.welfare.user.controller.UserAdminController;
import com.example.welfare.user.dto.response.UserMetadataUserKeyBackfillResponse;
import com.example.welfare.user.dto.response.UserPiiBackfillResponse;
import com.example.welfare.user.dto.response.UserPiiSyncReplayResponse;
import com.example.welfare.user.dto.response.UserPiiSyncStatusResponse;
import com.example.welfare.user.repository.UserRepository;
import com.example.welfare.user.service.UserMetadataUserKeyBackfillService;
import com.example.welfare.user.service.UserPiiBackfillService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
    private CollectService collectService;
    @MockBean
    private BokjiroDetailCollectService bokjiroDetailCollectService;
    @MockBean
    private NormalizedPolicySidecarBackfillService normalizedPolicySidecarBackfillService;
    @MockBean
    private SearchYouthRelevanceService searchYouthRelevanceService;
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
    private UserRepository userRepository;
    @MockBean
    private AdminDashboardService adminDashboardService;
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
        doNothing().when(collectService).collect(CollectSource.YOUTH);

        mockMvc.perform(post("/api/admin/collect/youth")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value("온통청년 수집 완료"));

        then(collectService).should().collect(CollectSource.YOUTH);
    }

    @Test
    @DisplayName("관리자 토큰으로 대시보드 요약 API를 호출하면 admin dashboard service를 실행한다")
    void adminEndpointAllowsDashboardSummary() throws Exception {
        mockAuthenticatedToken("admin-token", List.of(
                new SimpleGrantedAuthority("ROLE_USER"),
                new SimpleGrantedAuthority("ROLE_ADMIN")
        ));
        given(adminDashboardService.getSummary())
                .willReturn(new AdminDashboardResponse(
                        LocalDateTime.of(2026, 5, 2, 10, 0),
                        new AdminDashboardResponse.CollectSection(0, 3, 1, 0, List.of()),
                        new AdminDashboardResponse.RecommendationSection(2, 8, 3, 1, java.math.BigDecimal.valueOf(0.3750), java.math.BigDecimal.valueOf(0.1250)),
                        new AdminDashboardResponse.NotificationSection(1, 0, 5, 1),
                        new AdminDashboardResponse.SearchSection(4, 12, 7, java.math.BigDecimal.valueOf(5.25), List.of(
                                new AdminDashboardResponse.SearchKeywordSnapshot("월세", 5)
                        )),
                        new AdminDashboardResponse.UserPiiSyncSection(0, 1, 12, LocalDateTime.of(2026, 5, 2, 9, 30))
                ));

        mockMvc.perform(get("/api/admin/dashboard/summary")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.collect.successJobsLast24h").value(3))
                .andExpect(jsonPath("$.data.recommendation.sentLast7d").value(8))
                .andExpect(jsonPath("$.data.search.topKeywordsLast7d[0].keyword").value("월세"))
                .andExpect(jsonPath("$.data.userPiiSync.failedCount").value(1));

        then(adminDashboardService).should().getSummary();
    }

    @Test
    @DisplayName("관리자 토큰으로 detail refresh 관리자 API를 호출하면 refresh 수집 서비스를 실행한다")
    void adminEndpointAllowsDetailRefresh() throws Exception {
        mockAuthenticatedToken("admin-token", List.of(
                new SimpleGrantedAuthority("ROLE_USER"),
                new SimpleGrantedAuthority("ROLE_ADMIN")
        ));
        doNothing().when(collectService).collect(CollectSource.BOKJIRO_DETAIL_REFRESH);

        mockMvc.perform(post("/api/admin/collect/bokjiro-details-refresh")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value("복지로 상세 refresh 완료"));

        then(collectService).should().collect(CollectSource.BOKJIRO_DETAIL_REFRESH);
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
        given(bokjiroDetailCollectService.collectBokjiroDetailGapFillResult(4, 190))
                .willReturn(new BokjiroDetailCollectService.GapFillResult(4, 3, 190, 150, 120, 30, 0, true));

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

        then(bokjiroDetailCollectService).should().collectBokjiroDetailGapFillResult(4, 190);
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
        given(bokjiroDetailCollectService.collectBokjiroDetailGapFillResult(1, 95))
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
        given(userRepository.findIdByUserKey("user-key-1")).willReturn(java.util.Optional.of(1L));

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
        given(userRepository.findIdByUserKey("missing-user")).willReturn(java.util.Optional.empty());

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

    private void mockAuthenticatedToken(String token,
                                        List<SimpleGrantedAuthority> authorities) {
        doNothing().when(jwtUtil).validate(token);
        given(userSessionRevocationService.isAccessAllowed(token)).willReturn(true);
        given(jwtUtil.getAuthenticatedUser(token)).willReturn(new AuthenticatedUser(1L, "user-key-1"));
        given(jwtUtil.getAuthorities(token)).willReturn(List.copyOf(authorities));
    }
}
