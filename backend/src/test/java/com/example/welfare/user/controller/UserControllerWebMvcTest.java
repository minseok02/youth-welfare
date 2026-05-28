package com.example.welfare.user.controller;

import com.example.welfare.global.auth.AuthenticatedUser;
import com.example.welfare.policy.dto.PolicySummaryResponse;
import com.example.welfare.user.service.UserAccountCommandService;
import com.example.welfare.user.service.UserBookmarkReadService;
import com.example.welfare.user.service.UserProfileCommandService;
import com.example.welfare.user.service.UserProfileReadService;
import com.example.welfare.user.service.UserRecentViewedPolicyReadService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = false)
class UserControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserProfileReadService userProfileReadService;
    @MockBean
    private UserBookmarkReadService userBookmarkReadService;
    @MockBean
    private UserRecentViewedPolicyReadService userRecentViewedPolicyReadService;
    @MockBean
    private UserProfileCommandService userProfileCommandService;
    @MockBean
    private UserAccountCommandService userAccountCommandService;
    @MockBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    @DisplayName("마이페이지 북마크 목록 조회는 성공 응답을 반환한다")
    void getBookmarksReturnsSuccessResponse() throws Exception {
        given(userBookmarkReadService.getBookmarks(isNull())).willReturn(List.of(
                PolicySummaryResponse.builder()
                        .id(11L)
                        .title("청년 월세 지원")
                        .description("월세 부담 완화")
                        .unifiedCategory("HOUSING")
                        .status("ACTIVE")
                        .youthMajorLabel("주거")
                        .youthMidLabel("전월세 및 주거급여 지원")
                        .provisionMethodLabel("온라인")
                        .gov24ServiceFieldLabel("주거·자립")
                        .gov24UserTypeLabel("청년")
                        .gov24BenefitTypeLabel("서비스")
                        .build()
        ));

        mockMvc.perform(get("/api/users/me/bookmarks")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                new AuthenticatedUser(1L, "user-key-1"),
                                null,
                                Collections.emptyList()
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(11))
                .andExpect(jsonPath("$.data[0].title").value("청년 월세 지원"))
                .andExpect(jsonPath("$.data[0].youthMajorLabel").value("주거"))
                .andExpect(jsonPath("$.data[0].youthMidLabel").value("전월세 및 주거급여 지원"))
                .andExpect(jsonPath("$.data[0].provisionMethodLabel").value("온라인"))
                .andExpect(jsonPath("$.data[0].gov24ServiceFieldLabel").value("주거·자립"))
                .andExpect(jsonPath("$.data[0].gov24UserTypeLabel").value("청년"))
                .andExpect(jsonPath("$.data[0].gov24BenefitTypeLabel").value("서비스"));

        then(userBookmarkReadService).should().getBookmarks(isNull());
    }

    @Test
    @DisplayName("마이페이지 최근 본 정책 조회는 성공 응답을 반환한다")
    void getRecentViewedPoliciesReturnsSuccessResponse() throws Exception {
        given(userRecentViewedPolicyReadService.getRecentViewedPolicies(isNull(), org.mockito.ArgumentMatchers.eq(5)))
                .willReturn(List.of(
                        PolicySummaryResponse.builder()
                                .id(31L)
                                .title("최근 본 청년 정책")
                                .description("최근 조회 정책 설명")
                                .unifiedCategory("주거")
                                .status("ACTIVE")
                                .build()
                ));

        mockMvc.perform(get("/api/users/me/recent-viewed-policies")
                        .param("limit", "5")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                new AuthenticatedUser(1L, "user-key-1"),
                                null,
                                Collections.emptyList()
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(31))
                .andExpect(jsonPath("$.data[0].title").value("최근 본 청년 정책"));

        then(userRecentViewedPolicyReadService).should().getRecentViewedPolicies(isNull(), org.mockito.ArgumentMatchers.eq(5));
    }

    @Test
    @DisplayName("우선순위는 최소 1개 이상이어야 한다")
    void updatePrioritiesRejectsEmptyList() throws Exception {
        mockMvc.perform(put("/api/users/me/priorities")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                new AuthenticatedUser(1L, "user-key-1"),
                                null,
                                Collections.emptyList()
                        )))
                        .contentType("application/json")
                        .content("""
                                {
                                  "priorityCodes": []
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }
}
