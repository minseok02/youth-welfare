package com.example.welfare.admin;

import com.example.welfare.collect.controller.CollectAdminController;
import com.example.welfare.collect.service.CollectService;
import com.example.welfare.global.config.JacksonConfig;
import com.example.welfare.global.config.SecurityConfig;
import com.example.welfare.global.util.JwtUtil;
import com.example.welfare.policy.controller.PolicyAdminController;
import com.example.welfare.policy.service.SearchYouthRelevanceService;
import com.example.welfare.user.controller.UserAdminController;
import com.example.welfare.user.dto.response.UserMetadataUserKeyBackfillResponse;
import com.example.welfare.user.dto.response.UserPiiBackfillResponse;
import com.example.welfare.user.service.UserMetadataUserKeyBackfillService;
import com.example.welfare.user.service.UserPiiBackfillService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.doNothing;
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

    private void mockAuthenticatedToken(String token,
                                        List<SimpleGrantedAuthority> authorities) {
        doNothing().when(jwtUtil).validate(token);
        given(jwtUtil.getUserId(token)).willReturn(1L);
        given(jwtUtil.getAuthorities(token)).willReturn(List.copyOf(authorities));
    }
}
