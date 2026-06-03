package com.example.welfare.api;

import com.example.welfare.global.web.ClientFingerprintService;
import com.example.welfare.policy.controller.PolicyController;
import com.example.welfare.policy.service.PolicyBookmarkCommandService;
import com.example.welfare.policy.service.PolicyDetailService;
import com.example.welfare.policy.service.PolicyListService;
import com.example.welfare.policy.service.PolicyRankingService;
import com.example.welfare.policy.service.PolicySearchKeywordReadService;
import com.example.welfare.policy.service.PolicySearchLogService;
import com.example.welfare.policy.service.PolicySearchService;
import com.example.welfare.policy.service.PolicyTrafficRateLimitService;
import com.example.welfare.policy.service.PolicyViewLogService;
import com.example.welfare.recommend.service.RecommendationLogService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PolicyController.class)
@AutoConfigureMockMvc(addFilters = false)
class PolicySearchKeywordApiWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PolicyListService policyListService;
    @MockitoBean
    private PolicyDetailService policyDetailService;
    @MockitoBean
    private PolicyBookmarkCommandService policyBookmarkCommandService;
    @MockitoBean
    private PolicyRankingService policyRankingService;
    @MockitoBean
    private PolicySearchService policySearchService;
    @MockitoBean
    private PolicySearchLogService policySearchLogService;
    @MockitoBean
    private PolicySearchKeywordReadService policySearchKeywordReadService;
    @MockitoBean
    private PolicyTrafficRateLimitService policyTrafficRateLimitService;
    @MockitoBean
    private RecommendationLogService recommendationLogService;
    @MockitoBean
    private PolicyViewLogService policyViewLogService;
    @MockitoBean
    private ClientFingerprintService clientFingerprintService;
    @MockitoBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    @DisplayName("인기 검색어 endpoint는 성공 응답을 반환한다")
    void trendingReturnsSuccessResponse() throws Exception {
        given(policySearchKeywordReadService.getTrendingKeywords(6))
                .willReturn(List.of("월세 지원", "도약계좌"));
        given(clientFingerprintService.build(any())).willReturn("fp-trending");

        mockMvc.perform(get("/api/policies/search/trending")
                        .param("limit", "6"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0]").value("월세 지원"))
                .andExpect(jsonPath("$.data[1]").value("도약계좌"));

        then(policyTrafficRateLimitService).should().checkTrendingLimit("fp:fp-trending");
        then(policySearchKeywordReadService).should().getTrendingKeywords(6);
    }

    @Test
    @DisplayName("검색 자동완성 endpoint는 성공 응답을 반환한다")
    void suggestionsReturnsSuccessResponse() throws Exception {
        given(policySearchKeywordReadService.getSuggestions(eq("월세"), eq(8)))
                .willReturn(List.of("월세 지원", "청년 월세 지원"));
        given(clientFingerprintService.build(any())).willReturn("fp-suggestions");

        mockMvc.perform(get("/api/policies/search/suggestions")
                        .param("keyword", "월세")
                        .param("limit", "8"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0]").value("월세 지원"))
                .andExpect(jsonPath("$.data[1]").value("청년 월세 지원"));

        then(policyTrafficRateLimitService).should().checkSuggestionLimit("fp:fp-suggestions");
        then(policySearchKeywordReadService).should().getSuggestions("월세", 8);
    }
}
