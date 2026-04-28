package com.example.welfare.admin;

import com.example.welfare.collect.controller.CollectAdminController;
import com.example.welfare.collect.service.CollectService;
import com.example.welfare.global.auth.AuthenticatedUser;
import com.example.welfare.global.config.JacksonConfig;
import com.example.welfare.global.config.SecurityConfig;
import com.example.welfare.global.util.JwtUtil;
import com.example.welfare.policy.controller.PolicyAdminController;
import com.example.welfare.policy.service.SearchYouthRelevanceService;
import com.example.welfare.user.controller.UserAdminController;
import com.example.welfare.user.dto.response.UserMetadataUserKeyBackfillResponse;
import com.example.welfare.user.dto.response.UserPiiBackfillResponse;
import com.example.welfare.user.dto.response.UserPiiSyncReplayResponse;
import com.example.welfare.user.dto.response.UserPiiSyncStatusResponse;
import com.example.welfare.user.service.UserMetadataUserKeyBackfillService;
import com.example.welfare.user.service.UserPiiBackfillService;
import com.example.welfare.user.service.UserPiiSyncReplayService;
import com.example.welfare.user.service.UserPiiSyncStatusService;
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

@WebMvcTest(controllers = {CollectAdminController.class, PolicyAdminController.class, UserAdminController.class})
@Import({SecurityConfig.class, JacksonConfig.class})
class AdminSecurityWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CollectService collectService;
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
        doNothing().when(collectService).collectYouth();

        mockMvc.perform(post("/api/admin/collect/youth")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value("온통청년 수집 완료"));

        then(collectService).should().collectYouth();
    }

    @Test
    @DisplayName("관리자 토큰으로 detail refresh 관리자 API를 호출하면 refresh 수집 서비스를 실행한다")
    void adminEndpointAllowsDetailRefresh() throws Exception {
        mockAuthenticatedToken("admin-token", List.of(
                new SimpleGrantedAuthority("ROLE_USER"),
                new SimpleGrantedAuthority("ROLE_ADMIN")
        ));
        doNothing().when(collectService).collectBokjiroDetailsRefresh();

        mockMvc.perform(post("/api/admin/collect/bokjiro-details-refresh")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value("복지로 상세 refresh 완료"));

        then(collectService).should().collectBokjiroDetailsRefresh();
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
        given(jwtUtil.getAuthenticatedUser(token)).willReturn(new AuthenticatedUser(1L, "user-key-1"));
        given(jwtUtil.getAuthorities(token)).willReturn(List.copyOf(authorities));
    }
}
