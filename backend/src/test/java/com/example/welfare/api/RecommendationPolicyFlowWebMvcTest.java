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
import com.example.welfare.policy.service.PolicyErrorReportCommandService;
import com.example.welfare.policy.service.PolicyListService;
import com.example.welfare.policy.service.PolicyRankingService;
import com.example.welfare.policy.service.PolicySearchLogService;
import com.example.welfare.policy.service.PolicySearchKeywordReadService;
import com.example.welfare.policy.service.PolicySearchService;
import com.example.welfare.policy.service.PolicyTrafficRateLimitService;
import com.example.welfare.policy.service.PolicyViewLogService;
import com.example.welfare.recommend.controller.RecommendationController;
import com.example.welfare.recommend.dto.RecommendationCandidateProjection;
import com.example.welfare.recommend.dto.RecommendationRefreshStatusResponse;
import com.example.welfare.recommend.entity.UserRecommendation;
import com.example.welfare.recommend.service.RecommendationAccessService;
import com.example.welfare.recommend.service.RecommendationBookmarkCommandService;
import com.example.welfare.recommend.service.RecommendationGenerationService;
import com.example.welfare.recommend.service.RecommendationLogReadService;
import com.example.welfare.recommend.service.RecommendationLogService;
import com.example.welfare.recommend.service.RecommendationProjectionReadService;
import com.example.welfare.recommend.service.RecommendationRefreshAsyncJobService;
import com.example.welfare.recommend.service.SimilarUsersViewedPolicyReadService;
import com.example.welfare.recommend.dto.SimilarUsersViewedPolicyResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
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

    @MockitoBean
    private RecommendationAccessService recommendationAccessService;
    @MockitoBean
    private RecommendationGenerationService recommendationGenerationService;
    @MockitoBean
    private RecommendationRefreshAsyncJobService recommendationRefreshAsyncJobService;
    @MockitoBean
    private RecommendationBookmarkCommandService recommendationBookmarkCommandService;
    @MockitoBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;
    @MockitoBean
    private PolicyListService policyListService;
    @MockitoBean
    private PolicyDetailService policyDetailService;
    @MockitoBean
    private PolicyErrorReportCommandService policyErrorReportCommandService;
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
    private RecommendationLogReadService recommendationLogReadService;
    @MockitoBean
    private RecommendationProjectionReadService recommendationProjectionReadService;
    @MockitoBean
    private SimilarUsersViewedPolicyReadService similarUsersViewedPolicyReadService;
    @MockitoBean
    private PolicyViewLogService policyViewLogService;
    @MockitoBean
    private ClientFingerprintService clientFingerprintService;
    @MockitoBean
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
                .gov24ServiceFieldLabel("주거·자립")
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
                .gov24ServiceFieldLabel("주거·자립")
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
                                .summary("월세 부담을 낮추는 상세 지원 안내")
                                .youthMajorLabel("주거")
                                .youthMidLabel("전월세 및 주거급여 지원")
                                .provisionMethodLabel("온라인")
                                .gov24ServiceFieldLabel("주거·자립")
                                .gov24UserTypeLabel("청년")
                                .gov24BenefitTypeLabel("서비스")
                                .build()
                ));
        given(serviceRegionRepository.findRegionLabelsByServiceIds(List.of(11L)))
                .willReturn(List.<Object[]>of(new Object[]{11L, "서울특별시"}));
        given(policyRankingService.getRanking(5)).willReturn(List.of(ranking));
        given(policySearchService.search(isNull(), eq("월세"), eq("ACTIVE"), isNull(), eq("HOUSING"), eq("YOUTH"), eq(true), isNull(), isNull(), eq("RELEVANCE"), isNull(), isNull(), isNull(), isNull(), isNull(), eq(0), eq(10)))
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
                .andExpect(jsonPath("$.data[0].description").value("월세 부담을 낮추는 상세 지원 안내"))
                .andExpect(jsonPath("$.data[0].unifiedCategory").value("주거"))
                .andExpect(jsonPath("$.data[0].youthMajorLabel").value("주거"))
                .andExpect(jsonPath("$.data[0].youthMidLabel").value("전월세 및 주거급여 지원"))
                .andExpect(jsonPath("$.data[0].provisionMethodLabel").value("온라인"))
                .andExpect(jsonPath("$.data[0].gov24ServiceFieldLabel").value("주거·자립"))
                .andExpect(jsonPath("$.data[0].gov24UserTypeLabel").value("청년"))
                .andExpect(jsonPath("$.data[0].gov24BenefitTypeLabel").value("서비스"))
                .andExpect(jsonPath("$.data[0].hostOrg").value("서울시"))
                .andExpect(jsonPath("$.data[0].operatingOrg").value("서울주거재단"))
                .andExpect(jsonPath("$.data[0].sido").value("서울특별시"))
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
                .andExpect(jsonPath("$.data[0].gov24ServiceFieldLabel").value("주거·자립"))
                .andExpect(jsonPath("$.data[0].gov24UserTypeLabel").value("청년"))
                .andExpect(jsonPath("$.data[0].gov24BenefitTypeLabel").value("서비스"));

        mockMvc.perform(post("/api/policies/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                  {
                                    "keyword": "월세",
                                    "status": "ACTIVE",
                                    "category": "HOUSING",
                                    "sourceType": "YOUTH",
                                    "onlineApply": true,
                                    "sort": "RELEVANCE",
                                    "page": 0,
                                    "size": 10
                                  }
                                  """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].id").value(11))
                .andExpect(jsonPath("$.data.content[0].title").value("청년 월세 지원"))
                .andExpect(jsonPath("$.data.content[0].bookmarked").value(true))
                .andExpect(jsonPath("$.data.content[0].youthMajorLabel").value("주거"))
                .andExpect(jsonPath("$.data.content[0].youthMidLabel").value("전월세 및 주거급여 지원"))
                .andExpect(jsonPath("$.data.content[0].provisionMethodLabel").value("온라인"))
                .andExpect(jsonPath("$.data.content[0].gov24ServiceFieldLabel").value("주거·자립"))
                .andExpect(jsonPath("$.data.content[0].gov24UserTypeLabel").value("청년"))
                .andExpect(jsonPath("$.data.content[0].gov24BenefitTypeLabel").value("서비스"))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.totalPages").value(1))
                .andExpect(jsonPath("$.data.hasNext").value(false));

        verify(recommendationGenerationService).recommend(isNull(), eq(false));
        verify(policyRankingService).getRanking(5);
        verify(policySearchService).search(isNull(), eq("월세"), eq("ACTIVE"), isNull(), eq("HOUSING"), eq("YOUTH"), eq(true), isNull(), isNull(), eq("RELEVANCE"), isNull(), isNull(), isNull(), isNull(), isNull(), eq(0), eq(10));
        verify(policySearchLogService).record(any());
    }

    @Test
    @DisplayName("personal 추천 갱신 rate limit 초과 시 429를 반환한다")
    void refreshReturnsTooManyRequestsWhenRecommendationRefreshRateLimited() throws Exception {
        given(recommendationGenerationService.recommend(org.mockito.ArgumentMatchers.any(), eq(true)))
                .willThrow(new CustomException(ErrorCode.RECOMMENDATION_REFRESH_RATE_LIMIT_EXCEEDED));

        mockMvc.perform(post("/api/recommendations/refresh")
                        .param("personal", "true")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                new AuthenticatedUser(1L, "user-key-1"),
                                null,
                                Collections.emptyList()
                        )))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("R004"));
    }

    @Test
    @DisplayName("비동기 추천 갱신은 저장 추천을 즉시 반환하고 refresh 상태를 함께 반환한다")
    void refreshAsyncReturnsStoredRecommendationsAndQueuedStatus() throws Exception {
        WelfareService service = sampleService(11L, "청년 월세 지원");
        UserRecommendation recommendation = UserRecommendation.builder()
                .id(1001L)
                .service(service)
                .finalScore(new BigDecimal("0.9100"))
                .aiScore(new BigDecimal("0.7600"))
                .aiReason("주거 부담 완화에 유리")
                .recommendedAt(LocalDateTime.of(2026, 4, 16, 8, 0))
                .build();

        given(recommendationAccessService.getRecommendations(isNull(), eq(6))).willReturn(List.of(recommendation));
        given(recommendationLogReadService.findLatestLogIdMap(isNull(), org.mockito.ArgumentMatchers.anyList()))
                .willReturn(java.util.Map.of(11L, 9001L));
        given(recommendationProjectionReadService.findCandidateProjections(org.mockito.ArgumentMatchers.anyList()))
                .willReturn(java.util.Map.of());
        given(serviceRegionRepository.findRegionLabelsByServiceIds(List.of(11L))).willReturn(List.of());
        given(recommendationRefreshAsyncJobService.trigger(isNull(), eq(false)))
                .willReturn(new RecommendationRefreshStatusResponse(
                        RecommendationRefreshStatusResponse.RecommendationRefreshState.QUEUED,
                        true,
                        false,
                        "추천 갱신이 대기열에 등록되었습니다.",
                        LocalDateTime.of(2026, 7, 17, 9, 0),
                        null,
                        null,
                        LocalDateTime.of(2026, 4, 16, 8, 0),
                        1,
                        null,
                        null,
                        1000L
                ));

        mockMvc.perform(post("/api/recommendations/refresh-async")
                        .param("size", "6")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                new AuthenticatedUser(1L, "user-key-1"),
                                null,
                                Collections.emptyList()
                        )))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.recommendations[0].serviceId").value(11))
                .andExpect(jsonPath("$.data.recommendations[0].title").value("청년 월세 지원"))
                .andExpect(jsonPath("$.data.status.state").value("QUEUED"))
                .andExpect(jsonPath("$.data.status.active").value(true))
                .andExpect(jsonPath("$.data.status.personal").value(false))
                .andExpect(jsonPath("$.data.status.pollAfterMs").value(1000));

        verify(recommendationAccessService).getRecommendations(null, 6);
        verify(recommendationRefreshAsyncJobService).trigger(null, false);
        verify(recommendationGenerationService, never()).recommend(any(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    @DisplayName("비동기 추천 갱신은 personal=true를 거부한다")
    void refreshAsyncRejectsPersonalMode() throws Exception {
        mockMvc.perform(post("/api/recommendations/refresh-async")
                        .param("personal", "true")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                new AuthenticatedUser(1L, "user-key-1"),
                                null,
                                Collections.emptyList()
                        )))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("C001"));

        verify(recommendationRefreshAsyncJobService, never()).trigger(any(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    @DisplayName("추천 갱신 상태 조회는 현재 refresh 상태를 반환한다")
    void refreshStatusReturnsCurrentStatus() throws Exception {
        given(recommendationRefreshAsyncJobService.getStatus(isNull(), eq(false)))
                .willReturn(new RecommendationRefreshStatusResponse(
                        RecommendationRefreshStatusResponse.RecommendationRefreshState.RUNNING,
                        true,
                        false,
                        "추천 갱신이 진행 중입니다.",
                        LocalDateTime.of(2026, 7, 17, 9, 0),
                        LocalDateTime.of(2026, 7, 17, 9, 0, 1),
                        null,
                        null,
                        0,
                        null,
                        null,
                        1000L
                ));

        mockMvc.perform(get("/api/recommendations/refresh-status")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                new AuthenticatedUser(1L, "user-key-1"),
                                null,
                                Collections.emptyList()
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.state").value("RUNNING"))
                .andExpect(jsonPath("$.data.active").value(true));

        verify(recommendationRefreshAsyncJobService).getStatus(null, false);
    }

    @Test
    @DisplayName("추천 목록 조회는 100을 초과하는 size를 400 invalid input으로 거부한다")
    void getRecommendationsRejectsOversizedRequest() throws Exception {
        mockMvc.perform(get("/api/recommendations")
                        .param("size", "100000")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                new AuthenticatedUser(1L, "user-key-1"),
                                null,
                                Collections.emptyList()
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("C001"));
    }

    @Test
    @DisplayName("비슷한 사용자들이 본 정책 API는 집계형 추천 응답을 반환한다")
    void similarUsersViewedRecommendationsReturnsPolicySummaries() throws Exception {
        PolicySummaryResponse policy = PolicySummaryResponse.builder()
                .id(11L)
                .title("청년 월세 지원")
                .description("월세 부담 경감")
                .unifiedCategory("주거")
                .status("ACTIVE")
                .sourceType("YOUTH")
                .hostOrg("서울시")
                .bookmarked(false)
                .build();
        given(similarUsersViewedPolicyReadService.getSimilarUsersViewedPolicies(isNull(), eq(4)))
                .willReturn(List.of(new SimilarUsersViewedPolicyResponse(
                        policy,
                        "비슷한 프로필의 사용자가 최근 확인"
                )));

        mockMvc.perform(get("/api/recommendations/similar-users-viewed")
                        .param("size", "4")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                new AuthenticatedUser(1L, "user-key-1"),
                                null,
                                Collections.emptyList()
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].policy.id").value(11))
                .andExpect(jsonPath("$.data[0].policy.title").value("청년 월세 지원"))
                .andExpect(jsonPath("$.data[0].reasonLabel").value("비슷한 프로필의 사용자가 최근 확인"));

        verify(similarUsersViewedPolicyReadService).getSimilarUsersViewedPolicies(null, 4);
    }

    @Test
    @DisplayName("추천 북마크 API는 1 미만 추천 id를 400 invalid input으로 거부한다")
    void recommendationBookmarkRejectsInvalidRecommendationId() throws Exception {
        mockMvc.perform(post("/api/recommendations/{id}/bookmark", 0L)
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                new AuthenticatedUser(1L, "user-key-1"),
                                null,
                                Collections.emptyList()
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("C001"));

        verify(recommendationBookmarkCommandService, never()).toggleRecommendationBookmark(any(), any());
    }

    @Test
    @DisplayName("정책 검색 rate limit 초과 시 429를 반환한다")
    void searchReturnsTooManyRequestsWhenRateLimitExceeded() throws Exception {
        given(clientFingerprintService.build(any())).willReturn("fp-search");
        org.mockito.BDDMockito.willThrow(new CustomException(ErrorCode.POLICY_RATE_LIMIT_EXCEEDED))
                .given(policyTrafficRateLimitService)
                .checkSearchLimit("fp:fp-search");

        mockMvc.perform(post("/api/policies/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                  {
                                    "keyword": "월세"
                                  }
                                  """))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("P003"));
    }

    @Test
    @DisplayName("정책 목록 API는 범위를 벗어난 incomeLevel을 400으로 거부한다")
    void listRejectsInvalidIncomeLevel() throws Exception {
        mockMvc.perform(get("/api/policies")
                        .param("incomeLevel", "12"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("C001"));
    }

    @Test
    @DisplayName("정책 검색 API는 과도한 page를 400으로 거부한다")
    void searchRejectsOversizedPage() throws Exception {
        mockMvc.perform(post("/api/policies/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                  {
                                    "keyword": "월세",
                                    "page": 1001
                                  }
                                  """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("C001"));
    }

    @Test
    @DisplayName("정책 랭킹 rate limit 초과 시 429를 반환한다")
    void rankingReturnsTooManyRequestsWhenRateLimitExceeded() throws Exception {
        given(clientFingerprintService.build(any())).willReturn("fp-ranking");
        org.mockito.BDDMockito.willThrow(new CustomException(ErrorCode.POLICY_RATE_LIMIT_EXCEEDED))
                .given(policyTrafficRateLimitService)
                .checkRankingLimit("fp:fp-ranking");

        mockMvc.perform(get("/api/policies/ranking"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("P003"));
    }

    @Test
    @DisplayName("정책 오류제보 API는 긴 note를 400으로 거부한다")
    void policyErrorReportRejectsTooLongNote() throws Exception {
        mockMvc.perform(post("/api/policies/{id}/error-reports", 11L)
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                new AuthenticatedUser(1L, "user-key-1"),
                                null,
                                Collections.emptyList()
                        )))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reasonCode": "REGION_MISMATCH",
                                  "note": "%s"
                                }
                                """.formatted("x".repeat(1001))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("C001"));
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
                                .summary("월세 부담을 낮추는 상세 지원 안내")
                                .youthMajorLabel("주거")
                                .youthMidLabel("전월세 및 주거급여 지원")
                                .provisionMethodLabel("온라인")
                                .gov24ServiceFieldLabel("주거·자립")
                                .gov24UserTypeLabel("청년")
                                .gov24BenefitTypeLabel("서비스")
                                .build()
                ));
        given(serviceRegionRepository.findRegionLabelsByServiceIds(List.of(11L)))
                .willReturn(List.<Object[]>of(new Object[]{11L, "서울특별시"}));

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
                .andExpect(jsonPath("$.data[0].description").value("월세 부담을 낮추는 상세 지원 안내"))
                .andExpect(jsonPath("$.data[0].unifiedCategory").value("주거"))
                .andExpect(jsonPath("$.data[0].youthMajorLabel").value("주거"))
                .andExpect(jsonPath("$.data[0].youthMidLabel").value("전월세 및 주거급여 지원"))
                .andExpect(jsonPath("$.data[0].provisionMethodLabel").value("온라인"))
                .andExpect(jsonPath("$.data[0].gov24ServiceFieldLabel").value("주거·자립"))
                .andExpect(jsonPath("$.data[0].gov24UserTypeLabel").value("청년"))
                .andExpect(jsonPath("$.data[0].gov24BenefitTypeLabel").value("서비스"))
                .andExpect(jsonPath("$.data[0].hostOrg").value("서울시"))
                .andExpect(jsonPath("$.data[0].operatingOrg").value("서울주거재단"))
                .andExpect(jsonPath("$.data[0].sido").value("서울특별시"))
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
    @DisplayName("정책 상세 조회는 URL log_id로 클릭 로그를 기록하지 않는다")
    void policyDetailDoesNotMarkClickFromUrlLogId() throws Exception {
        PolicyDetailResponse detail = PolicyDetailResponse.builder()
                .id(11L)
                .title("청년 월세 지원")
                .status("ACTIVE")
                .sourceType("YOUTH")
                .youthMajorLabel("주거")
                .youthMidLabel("전월세 및 주거급여 지원")
                .provisionMethodLabel("온라인")
                .youthIncomeConditionTypeLabel("무관")
                .youthEmploymentRequirementLabels(List.of("미취업자"))
                .youthEducationRequirementLabels(List.of("대학 재학"))
                .youthSpecialRequirementLabels(List.of("지역인재"))
                .youthMaritalStatusLabel("제한없음")
                .gov24ServiceFieldLabel("주거·자립")
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
                .andExpect(jsonPath("$.data.youthIncomeConditionTypeLabel").value("무관"))
                .andExpect(jsonPath("$.data.youthEmploymentRequirementLabels[0]").value("미취업자"))
                .andExpect(jsonPath("$.data.youthEducationRequirementLabels[0]").value("대학 재학"))
                .andExpect(jsonPath("$.data.youthSpecialRequirementLabels[0]").value("지역인재"))
                .andExpect(jsonPath("$.data.youthMaritalStatusLabel").value("제한없음"))
                .andExpect(jsonPath("$.data.gov24ServiceFieldLabel").value("주거·자립"))
                .andExpect(jsonPath("$.data.gov24UserTypeLabel").value("청년"))
                .andExpect(jsonPath("$.data.gov24BenefitTypeLabel").value("서비스"));

        verify(policyDetailService).getDetail(isNull(), eq(11L), eq(true));
        verify(recommendationLogService, never()).markClicked(anyLong(), any());
    }

    @Test
    @DisplayName("추천 클릭 추적은 URL이 아닌 POST body로 기록한다")
    void recommendationClickMarksClickFromPostBody() throws Exception {
        mockMvc.perform(post("/api/policies/{id}/recommendation-click", 11L)
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                new AuthenticatedUser(1L, "user-key-1"),
                                null,
                                Collections.emptyList()
                        )))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "logId": 9001
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(recommendationLogService).markClicked(9001L, null, 11L);
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
