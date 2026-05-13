package com.example.welfare.api;

import com.example.welfare.global.auth.AuthenticatedUser;
import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.example.welfare.global.web.ClientFingerprintService;
import com.example.welfare.policy.controller.PolicyController;
import com.example.welfare.policy.dto.PolicyDetailResponse;
import com.example.welfare.policy.dto.PolicyRankingResponse;
import com.example.welfare.policy.dto.PolicySearchResponse;
import com.example.welfare.policy.dto.PolicySummaryResponse;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.repository.ServiceRegionRepository;
import com.example.welfare.policy.service.PolicyBookmarkCommandService;
import com.example.welfare.policy.service.PolicyDetailService;
import com.example.welfare.policy.service.PolicyListService;
import com.example.welfare.policy.service.PolicyRankingService;
import com.example.welfare.policy.service.PolicySearchLogService;
import com.example.welfare.policy.service.PolicySearchService;
import com.example.welfare.policy.service.PolicyTrafficRateLimitService;
import com.example.welfare.policy.service.PolicyViewLogService;
import com.example.welfare.recommend.controller.RecommendationController;
import com.example.welfare.recommend.dto.RecommendationCandidateProjection;
import com.example.welfare.recommend.entity.UserRecommendation;
import com.example.welfare.recommend.service.RecommendationAccessService;
import com.example.welfare.recommend.service.RecommendationBookmarkCommandService;
import com.example.welfare.recommend.service.RecommendationGenerationService;
import com.example.welfare.recommend.service.RecommendationLogReadService;
import com.example.welfare.recommend.service.RecommendationLogService;
import com.example.welfare.recommend.service.RecommendationProjectionReadService;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
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
    private RecommendationAccessService recommendationAccessService;
    @MockBean
    private RecommendationGenerationService recommendationGenerationService;
    @MockBean
    private RecommendationBookmarkCommandService recommendationBookmarkCommandService;
    @MockBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;
    @MockBean
    private PolicyListService policyListService;
    @MockBean
    private PolicyDetailService policyDetailService;
    @MockBean
    private PolicyBookmarkCommandService policyBookmarkCommandService;
    @MockBean
    private PolicyRankingService policyRankingService;
    @MockBean
    private PolicySearchService policySearchService;
    @MockBean
    private PolicySearchLogService policySearchLogService;
    @MockBean
    private PolicyTrafficRateLimitService policyTrafficRateLimitService;
    @MockBean
    private RecommendationLogService recommendationLogService;
    @MockBean
    private RecommendationLogReadService recommendationLogReadService;
    @MockBean
    private RecommendationProjectionReadService recommendationProjectionReadService;
    @MockBean
    private PolicyViewLogService policyViewLogService;
    @MockBean
    private ClientFingerprintService clientFingerprintService;
    @MockBean
    private ServiceRegionRepository serviceRegionRepository;

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
                .youthMajorLabel("주거")
                .youthMidLabel("전월세 및 주거급여 지원")
                .provisionMethodLabel("온라인")
                .gov24ServiceFieldLabel("보육")
                .gov24UserTypeLabel("청년")
                .gov24BenefitTypeLabel("서비스")
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
                .youthMajorLabel("주거")
                .youthMidLabel("전월세 및 주거급여 지원")
                .provisionMethodLabel("온라인")
                .gov24ServiceFieldLabel("보육")
                .gov24UserTypeLabel("청년")
                .gov24BenefitTypeLabel("서비스")
                .isOnlineApply(true)
                .bookmarked(true)
                .build();

        given(recommendationGenerationService.recommend(isNull(), eq(false))).willReturn(List.of(recommendation));
        given(recommendationLogReadService.findLatestLogIdMap(isNull(), org.mockito.ArgumentMatchers.anyList()))
                .willReturn(java.util.Map.of(11L, 9001L));
        given(recommendationProjectionReadService.findCandidateProjections(org.mockito.ArgumentMatchers.anyList()))
                .willReturn(java.util.Map.of(
                        11L,
                        RecommendationCandidateProjection.builder()
                                .serviceId(11L)
                                .unifiedCategoryCompat("주거")
                                .youthMajorLabel("주거")
                                .youthMidLabel("전월세 및 주거급여 지원")
                                .provisionMethodLabel("온라인")
                                .gov24ServiceFieldLabel("보육")
                                .gov24UserTypeLabel("청년")
                                .gov24BenefitTypeLabel("서비스")
                                .build()
                ));
        given(serviceRegionRepository.findFirstSidoByServiceIds(List.of(11L)))
                .willReturn(List.<Object[]>of(new Object[]{11L, "서울"}));
        given(policyRankingService.getRanking(5)).willReturn(List.of(ranking));
        given(policySearchService.search(isNull(), eq("월세"), eq("ACTIVE"), isNull(), eq("HOUSING"), eq("YOUTH"), eq(true), isNull(), isNull(), eq("RELEVANCE"), isNull(), isNull(), eq(0), eq(10)))
                .willReturn(PolicySearchResponse.builder()
                        .content(List.of(searchHit))
                        .totalElements(1)
                        .totalPages(1)
                        .pageNumber(0)
                        .pageSize(10)
                        .hasNext(false)
                        .build());
        given(clientFingerprintService.build(any())).willReturn("fp-search");

        mockMvc.perform(post("/api/recommendations/refresh")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                new AuthenticatedUser(1L, "user-key-1"),
                                null,
                                Collections.emptyList()
                        )))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].serviceId").value(11))
                .andExpect(jsonPath("$.data[0].title").value("청년 월세 지원"))
                .andExpect(jsonPath("$.data[0].unifiedCategory").value("주거"))
                .andExpect(jsonPath("$.data[0].youthMajorLabel").value("주거"))
                .andExpect(jsonPath("$.data[0].youthMidLabel").value("전월세 및 주거급여 지원"))
                .andExpect(jsonPath("$.data[0].provisionMethodLabel").value("온라인"))
                .andExpect(jsonPath("$.data[0].gov24ServiceFieldLabel").value("보육"))
                .andExpect(jsonPath("$.data[0].gov24UserTypeLabel").value("청년"))
                .andExpect(jsonPath("$.data[0].gov24BenefitTypeLabel").value("서비스"))
                .andExpect(jsonPath("$.data[0].hostOrg").value("서울시"))
                .andExpect(jsonPath("$.data[0].operatingOrg").value("서울주거재단"))
                .andExpect(jsonPath("$.data[0].sido").value("서울"))
                .andExpect(jsonPath("$.data[0].applyEndDate").value("2026-12-31"));

        mockMvc.perform(get("/api/policies/ranking")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].serviceId").value(11))
                .andExpect(jsonPath("$.data[0].rankingScore").value(0.9812))
                .andExpect(jsonPath("$.data[0].youthMajorLabel").value("주거"))
                .andExpect(jsonPath("$.data[0].youthMidLabel").value("전월세 및 주거급여 지원"))
                .andExpect(jsonPath("$.data[0].provisionMethodLabel").value("온라인"))
                .andExpect(jsonPath("$.data[0].gov24ServiceFieldLabel").value("보육"))
                .andExpect(jsonPath("$.data[0].gov24UserTypeLabel").value("청년"))
                .andExpect(jsonPath("$.data[0].gov24BenefitTypeLabel").value("서비스"));

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
                .andExpect(jsonPath("$.data.content[0].id").value(11))
                .andExpect(jsonPath("$.data.content[0].title").value("청년 월세 지원"))
                .andExpect(jsonPath("$.data.content[0].bookmarked").value(true))
                .andExpect(jsonPath("$.data.content[0].youthMajorLabel").value("주거"))
                .andExpect(jsonPath("$.data.content[0].youthMidLabel").value("전월세 및 주거급여 지원"))
                .andExpect(jsonPath("$.data.content[0].provisionMethodLabel").value("온라인"))
                .andExpect(jsonPath("$.data.content[0].gov24ServiceFieldLabel").value("보육"))
                .andExpect(jsonPath("$.data.content[0].gov24UserTypeLabel").value("청년"))
                .andExpect(jsonPath("$.data.content[0].gov24BenefitTypeLabel").value("서비스"))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.totalPages").value(1))
                .andExpect(jsonPath("$.data.hasNext").value(false));

        verify(recommendationGenerationService).recommend(isNull(), eq(false));
        verify(policyRankingService).getRanking(5);
        verify(policySearchService).search(isNull(), eq("월세"), eq("ACTIVE"), isNull(), eq("HOUSING"), eq("YOUTH"), eq(true), isNull(), isNull(), eq("RELEVANCE"), isNull(), isNull(), eq(0), eq(10));
        verify(policySearchLogService).record(any());
    }

    @Test
    @DisplayName("정책 검색 rate limit 초과 시 429를 반환한다")
    void searchReturnsTooManyRequestsWhenRateLimitExceeded() throws Exception {
        given(clientFingerprintService.build(any())).willReturn("fp-search");
        org.mockito.BDDMockito.willThrow(new CustomException(ErrorCode.POLICY_RATE_LIMIT_EXCEEDED))
                .given(policyTrafficRateLimitService)
                .checkSearchLimit("fp:fp-search");

        mockMvc.perform(get("/api/policies/search")
                        .param("keyword", "월세"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("P003"));
    }

    @Test
    @DisplayName("정책 상세 rate limit 초과 시 429를 반환한다")
    void detailReturnsTooManyRequestsWhenRateLimitExceeded() throws Exception {
        given(clientFingerprintService.build(any())).willReturn("fp-detail");
        org.mockito.BDDMockito.willThrow(new CustomException(ErrorCode.POLICY_RATE_LIMIT_EXCEEDED))
                .given(policyTrafficRateLimitService)
                .checkDetailLimit("fp:fp-detail", 11L);

        mockMvc.perform(get("/api/policies/{id}", 11L))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("P003"));
    }

    @Test
    @DisplayName("저장된 추천 목록 조회는 canonical summary additive field를 함께 반환한다")
    void recommendationListIncludesCanonicalSummaryFields() throws Exception {
        WelfareService service = sampleService(11L, "청년 월세 지원");
        UserRecommendation recommendation = UserRecommendation.builder()
                .id(1001L)
                .service(service)
                .finalScore(new BigDecimal("0.9100"))
                .aiScore(new BigDecimal("0.7600"))
                .aiReason("주거 부담 완화에 유리")
                .recommendedAt(LocalDateTime.of(2026, 4, 16, 8, 0))
                .build();

        given(recommendationAccessService.getRecommendations(isNull(), eq(10))).willReturn(List.of(recommendation));
        given(recommendationLogReadService.findLatestLogIdMap(isNull(), org.mockito.ArgumentMatchers.anyList()))
                .willReturn(java.util.Map.of(11L, 9001L));
        given(recommendationProjectionReadService.findCandidateProjections(org.mockito.ArgumentMatchers.anyList()))
                .willReturn(java.util.Map.of(
                        11L,
                        RecommendationCandidateProjection.builder()
                                .serviceId(11L)
                                .unifiedCategoryCompat("주거")
                                .youthMajorLabel("주거")
                                .youthMidLabel("전월세 및 주거급여 지원")
                                .provisionMethodLabel("온라인")
                                .gov24ServiceFieldLabel("보육")
                                .gov24UserTypeLabel("청년")
                                .gov24BenefitTypeLabel("서비스")
                                .build()
                ));
        given(serviceRegionRepository.findFirstSidoByServiceIds(List.of(11L)))
                .willReturn(List.<Object[]>of(new Object[]{11L, "서울"}));

        mockMvc.perform(get("/api/recommendations")
                        .param("size", "10")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                new AuthenticatedUser(1L, "user-key-1"),
                                null,
                                Collections.emptyList()
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(1001))
                .andExpect(jsonPath("$.data[0].serviceId").value(11))
                .andExpect(jsonPath("$.data[0].logId").value(9001))
                .andExpect(jsonPath("$.data[0].unifiedCategory").value("주거"))
                .andExpect(jsonPath("$.data[0].youthMajorLabel").value("주거"))
                .andExpect(jsonPath("$.data[0].youthMidLabel").value("전월세 및 주거급여 지원"))
                .andExpect(jsonPath("$.data[0].provisionMethodLabel").value("온라인"))
                .andExpect(jsonPath("$.data[0].gov24ServiceFieldLabel").value("보육"))
                .andExpect(jsonPath("$.data[0].gov24UserTypeLabel").value("청년"))
                .andExpect(jsonPath("$.data[0].gov24BenefitTypeLabel").value("서비스"))
                .andExpect(jsonPath("$.data[0].hostOrg").value("서울시"))
                .andExpect(jsonPath("$.data[0].operatingOrg").value("서울주거재단"))
                .andExpect(jsonPath("$.data[0].sido").value("서울"))
                .andExpect(jsonPath("$.data[0].applyEndDate").value("2026-12-31"));

        verify(recommendationAccessService).getRecommendations(isNull(), eq(10));
    }

    @Test
    @DisplayName("추천 갱신이 이미 진행 중이면 409 R003 을 반환한다")
    void refreshReturnsConflictWhenRecommendationAlreadyRunning() throws Exception {
        given(recommendationGenerationService.recommend(isNull(), eq(false)))
                .willThrow(new CustomException(ErrorCode.RECOMMENDATION_ALREADY_RUNNING));

        mockMvc.perform(post("/api/recommendations/refresh")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                new AuthenticatedUser(1L, "user-key-1"),
                                null,
                                Collections.emptyList()
                        )))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("R003"));
    }

    @Test
    @DisplayName("정책 상세 조회 시 log_id가 있으면 클릭 로그를 기록한다")
    void policyDetailMarksClickWhenLogIdExists() throws Exception {
        PolicyDetailResponse detail = PolicyDetailResponse.builder()
                .id(11L)
                .title("청년 월세 지원")
                .status("ACTIVE")
                .sourceType("YOUTH")
                .youthMajorLabel("주거")
                .youthMidLabel("전월세 및 주거급여 지원")
                .provisionMethodLabel("온라인")
                .gov24ServiceFieldLabel("보육")
                .gov24UserTypeLabel("청년")
                .gov24BenefitTypeLabel("서비스")
                .build();
        given(clientFingerprintService.build(org.mockito.ArgumentMatchers.any())).willReturn("fp");
        given(policyViewLogService.registerViewIfFirstInWindow(eq(11L), isNull(), eq("fp"))).willReturn(true);
        given(policyDetailService.getDetail(isNull(), eq(11L), eq(true))).willReturn(detail);

        mockMvc.perform(get("/api/policies/{id}", 11L)
                        .param("logId", "9001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(11))
                .andExpect(jsonPath("$.data.title").value("청년 월세 지원"))
                .andExpect(jsonPath("$.data.youthMajorLabel").value("주거"))
                .andExpect(jsonPath("$.data.youthMidLabel").value("전월세 및 주거급여 지원"))
                .andExpect(jsonPath("$.data.provisionMethodLabel").value("온라인"))
                .andExpect(jsonPath("$.data.gov24ServiceFieldLabel").value("보육"))
                .andExpect(jsonPath("$.data.gov24UserTypeLabel").value("청년"))
                .andExpect(jsonPath("$.data.gov24BenefitTypeLabel").value("서비스"));

        verify(policyDetailService).getDetail(isNull(), eq(11L), eq(true));
        verify(recommendationLogService).markClicked(9001L, null);
    }

    @Test
    @DisplayName("정책 북마크 API는 정책 북마크 command service에 토글을 위임한다")
    void policyBookmarkDelegatesToPolicyBookmarkCommandService() throws Exception {
        mockMvc.perform(post("/api/policies/{id}/bookmark", 11L)
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                new AuthenticatedUser(1L, "user-key-1"),
                                null,
                                Collections.emptyList()
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(policyBookmarkCommandService).toggleBookmark(isNull(), eq(11L));
    }

    private WelfareService sampleService(Long id, String title) {
        return WelfareService.builder()
                .id(id)
                .sourceType(WelfareService.SourceType.YOUTH)
                .sourceId("SRC-" + id)
                .title(title)
                .description("설명")
                .unifiedCategory("HOUSING")
                .hostOrg("서울시")
                .operatingOrg("서울주거재단")
                .status(WelfareService.ServiceStatus.ACTIVE)
                .applyEndDate(LocalDate.of(2026, 12, 31))
                .isOnlineApply(true)
                .build();
    }
}
