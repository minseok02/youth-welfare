package com.example.welfare.api;

import com.example.welfare.policy.controller.PolicyController;
import com.example.welfare.policy.dto.PolicyDetailResponse;
import com.example.welfare.policy.dto.PolicyRankingResponse;
import com.example.welfare.policy.dto.PolicySummaryResponse;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.service.PolicyRankingService;
import com.example.welfare.policy.service.PolicySearchService;
import com.example.welfare.policy.service.PolicyService;
import com.example.welfare.policy.service.PolicyViewLogService;
import com.example.welfare.recommend.controller.RecommendationController;
import com.example.welfare.recommend.entity.UserRecommendation;
import com.example.welfare.recommend.facade.RecommendationFacade;
import com.example.welfare.recommend.service.RecommendationLogService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {RecommendationController.class, PolicyController.class})
@AutoConfigureMockMvc(addFilters = false)
class RecommendationPolicyFlowWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RecommendationFacade recommendationFacade;
    @MockBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;
    @MockBean
    private PolicyService policyService;
    @MockBean
    private PolicyRankingService policyRankingService;
    @MockBean
    private PolicySearchService policySearchService;
    @MockBean
    private RecommendationLogService recommendationLogService;
    @MockBean
    private PolicyViewLogService policyViewLogService;

    @Test
    @DisplayName("추천 갱신 -> 랭킹 조회 -> 검색 조회 핵심 흐름이 성공 응답을 반환한다")
    void recommendationRankingSearchFlow() throws Exception {
        WelfareService service = sampleService(11L, "청년 월세 지원");
        UserRecommendation recommendation = UserRecommendation.builder()
                .id(1001L)
                .service(service)
                .finalScore(new BigDecimal("0.9100"))
                .aiScore(new BigDecimal("0.7600"))
                .aiReason("주거 부담 완화에 유리")
                .recommendedAt(LocalDateTime.of(2026, 4, 16, 8, 0))
                .build();
        PolicyRankingResponse ranking = PolicyRankingResponse.builder()
                .serviceId(11L)
                .title("청년 월세 지원")
                .sourceType("YOUTH")
                .unifiedCategory("HOUSING")
                .viewCount(8L)
                .apiViewCount(123L)
                .rankingScore(0.9812)
                .build();
        PolicySummaryResponse searchHit = PolicySummaryResponse.builder()
                .id(11L)
                .title("청년 월세 지원")
                .description("월세 부담 경감")
                .unifiedCategory("HOUSING")
                .status("ACTIVE")
                .hostOrg("서울시")
                .minAge(19)
                .maxAge(34)
                .isOnlineApply(true)
                .build();

        given(recommendationFacade.recommend(isNull())).willReturn(List.of(recommendation));
        given(policyRankingService.getRanking(5)).willReturn(List.of(ranking));
        given(policySearchService.search("월세", "ACTIVE", "HOUSING", "YOUTH", true, null, null, "RELEVANCE", 0, 10))
                .willReturn(List.of(searchHit));

        mockMvc.perform(post("/api/recommendations/refresh")
                        .with(authentication(new UsernamePasswordAuthenticationToken(1L, null, Collections.emptyList())))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].serviceId").value(11))
                .andExpect(jsonPath("$.data[0].title").value("청년 월세 지원"));

        mockMvc.perform(get("/api/policies/ranking")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].serviceId").value(11))
                .andExpect(jsonPath("$.data[0].rankingScore").value(0.9812));

        mockMvc.perform(get("/api/policies/search")
                        .param("keyword", "월세")
                        .param("status", "ACTIVE")
                        .param("category", "HOUSING")
                        .param("sourceType", "YOUTH")
                        .param("onlineApply", "true")
                        .param("sort", "RELEVANCE")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(11))
                .andExpect(jsonPath("$.data[0].title").value("청년 월세 지원"));

        verify(recommendationFacade).recommend(isNull());
        verify(policyRankingService).getRanking(5);
        verify(policySearchService).search("월세", "ACTIVE", "HOUSING", "YOUTH", true, null, null, "RELEVANCE", 0, 10);
    }

    @Test
    @DisplayName("정책 상세 조회 시 log_id가 있으면 클릭 로그를 기록한다")
    void policyDetailMarksClickWhenLogIdExists() throws Exception {
        PolicyDetailResponse detail = PolicyDetailResponse.builder()
                .id(11L)
                .title("청년 월세 지원")
                .status("ACTIVE")
                .sourceType("YOUTH")
                .build();
        given(policyViewLogService.buildClientFingerprint(org.mockito.ArgumentMatchers.any())).willReturn("fp");
        given(policyViewLogService.registerViewIfFirstInWindow(eq(11L), isNull(), eq("fp"))).willReturn(true);
        given(policyService.getDetail(11L, true)).willReturn(detail);

        mockMvc.perform(get("/api/policies/{id}", 11L)
                        .param("logId", "9001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(11))
                .andExpect(jsonPath("$.data.title").value("청년 월세 지원"));

        verify(policyService).getDetail(eq(11L), eq(true));
        verify(recommendationLogService).markClicked(9001L);
    }

    @Test
    @DisplayName("정책 북마크 API는 정책 서비스에 토글을 위임한다")
    void policyBookmarkDelegatesToPolicyService() throws Exception {
        mockMvc.perform(post("/api/policies/{id}/bookmark", 11L)
                        .with(authentication(new UsernamePasswordAuthenticationToken(1L, null, Collections.emptyList()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(policyService).toggleBookmark(isNull(), eq(11L));
    }

    private WelfareService sampleService(Long id, String title) {
        return WelfareService.builder()
                .id(id)
                .sourceType(WelfareService.SourceType.YOUTH)
                .sourceId("SRC-" + id)
                .title(title)
                .description("설명")
                .unifiedCategory("HOUSING")
                .status(WelfareService.ServiceStatus.ACTIVE)
                .isOnlineApply(true)
                .build();
    }
}
