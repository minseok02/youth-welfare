package com.example.welfare.policy.service;

import com.example.welfare.chat.config.ChatRetrievalProperties;
import com.example.welfare.chat.repository.ChatPolicyReadCondition;
import com.example.welfare.chat.service.ChatSemanticSearchService;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.repository.WelfareServiceRepository;
import com.example.welfare.policy.repository.WelfareServiceSearchRepository;
import com.example.welfare.recommend.repository.RecommendationCandidateReadCondition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PolicyExplorationServiceTest {

    @Mock
    private WelfareServiceRepository welfareServiceRepository;
    @Mock
    private WelfareServiceSearchRepository welfareServiceSearchRepository;
    @Mock
    private ChatSemanticSearchService chatSemanticSearchService;

    private PolicyExplorationService policyExplorationService;

    @BeforeEach
    void setUp() {
        policyExplorationService = new PolicyExplorationService(
                welfareServiceRepository,
                welfareServiceSearchRepository,
                chatSemanticSearchService,
                new ChatRetrievalProperties(3, 2, 3, 3)
        );
    }

    @Test
    @DisplayName("chat candidate 탐색은 FTS와 semantic 결과를 중복 없이 병합한다")
    void findChatCandidatesMergesKeywordAndSemanticResults() {
        WelfareService keywordMatch = service(1L, "청년 월세 지원", "주거");
        WelfareService semanticOnly = service(2L, "청년 주거비 완화", "주거");

        when(welfareServiceSearchRepository.searchChatCandidates("주거 월세", 3))
                .thenReturn(List.of(keywordMatch));
        when(chatSemanticSearchService.findCandidates("주거", "주거", List.of("월세"), 3))
                .thenReturn(List.of(keywordMatch, semanticOnly));

        List<WelfareService> candidates = policyExplorationService.findChatCandidates(
                new ChatPolicyReadCondition("주거", 3, "housing-cash", "주거", List.of("월세"))
        );

        assertThat(candidates).extracting(WelfareService::getId).containsExactly(1L, 2L);
    }

    @Test
    @DisplayName("chat candidate 탐색은 semantic blend 수를 상한으로 제한한다")
    void findChatCandidatesCapsSemanticBlendCount() {
        WelfareService keywordMatch = service(1L, "청년 월세 지원", "주거");
        WelfareService semanticOne = service(2L, "청년 주거비 완화", "주거");
        WelfareService semanticTwo = service(3L, "청년 임대 지원", "주거");
        WelfareService semanticThree = service(4L, "청년 전세 지원", "주거");

        when(welfareServiceSearchRepository.searchChatCandidates("주거 월세 주거비 지원금", 5))
                .thenReturn(List.of(keywordMatch));
        when(chatSemanticSearchService.findCandidates("주거", "주거", List.of("월세", "주거비", "지원금"), 5))
                .thenReturn(List.of(keywordMatch, semanticOne, semanticTwo, semanticThree));

        List<WelfareService> candidates = policyExplorationService.findChatCandidates(
                new ChatPolicyReadCondition("주거", 5, "housing-cash", "주거", List.of("월세", "주거비", "지원금"))
        );

        assertThat(candidates).extracting(WelfareService::getId).containsExactly(1L, 2L, 3L);
    }

    @Test
    @DisplayName("chat candidate 탐색은 FTS가 요청 limit을 이미 채우면 semantic 검색을 건너뛴다")
    void traceChatCandidatesSkipsSemanticSearchWhenFtsAlreadyFillsLimit() {
        WelfareService keywordOne = service(1L, "청년 월세 지원", "주거");
        WelfareService keywordTwo = service(2L, "청년 주거비 지원", "주거");
        WelfareService keywordThree = service(3L, "청년 전세 지원", "주거");

        when(welfareServiceSearchRepository.searchChatCandidates("주거 월세", 3))
                .thenReturn(List.of(keywordOne, keywordTwo, keywordThree));

        PolicyExplorationService.ChatExplorationTrace trace = policyExplorationService.traceChatCandidates(
                new ChatPolicyReadCondition("주거", 3, "housing-cash", "주거", List.of("월세"))
        );

        verify(chatSemanticSearchService, never()).findCandidates(any(), any(), any(), anyInt());
        assertThat(trace.fallbackStrategy()).isEqualTo("MERGED_RESULTS");
        assertThat(trace.semanticCandidates()).isEmpty();
        assertThat(trace.finalCandidates()).extracting(WelfareService::getId).containsExactly(1L, 2L, 3L);
    }

    @Test
    @DisplayName("chat candidate 탐색은 결과가 부족하면 category fallback 으로 채운다")
    void findChatCandidatesFillsFromCategoryFallbackWhenMergedTooSmall() {
        WelfareService keywordMatch = service(1L, "청년 월세 지원", "주거");
        WelfareService fallbackOne = service(8L, "인기 주거 정책", "주거");
        WelfareService fallbackTwo = service(9L, "최신 주거 정책", "주거");

        when(welfareServiceSearchRepository.searchChatCandidates("주거 월세", 5))
                .thenReturn(List.of(keywordMatch));
        when(chatSemanticSearchService.findCandidates("주거", "주거", List.of("월세"), 5))
                .thenReturn(List.of());
        when(welfareServiceRepository
                .findBySearchYouthRelevantTrueAndStatusInAndUnifiedCategoryOrderByApiViewCountDescViewCountDescCreatedAtDesc(
                        eq(List.of(WelfareService.ServiceStatus.ACTIVE, WelfareService.ServiceStatus.UPCOMING)),
                        eq("주거"),
                        any(Pageable.class)
                ))
                .thenReturn(List.of(keywordMatch, fallbackOne, fallbackTwo));

        PolicyExplorationService.ChatExplorationTrace trace = policyExplorationService.traceChatCandidates(
                new ChatPolicyReadCondition("주거", 5, "housing-cash", "주거", List.of("월세"))
        );

        assertThat(trace.fallbackStrategy()).isEqualTo("MERGED_WITH_CATEGORY_FILL");
        assertThat(trace.finalCandidates()).extracting(WelfareService::getId).containsExactly(1L, 8L, 9L);
    }

    @Test
    @DisplayName("search keyword 는 preferred term 개수를 상한으로 제한한다")
    void traceChatCandidatesCapsPreferredTermsInKeyword() {
        when(welfareServiceSearchRepository.searchChatCandidates("취업 창업 금융 사업", 5))
                .thenReturn(List.of());
        when(chatSemanticSearchService.findCandidates("취업", "일자리", List.of("창업", "금융", "사업", "자금"), 5))
                .thenReturn(List.of());
        when(welfareServiceRepository.findBySearchYouthRelevantTrueAndStatusInAndUnifiedCategoryOrderByApiViewCountDescViewCountDescCreatedAtDesc(
                eq(List.of(WelfareService.ServiceStatus.ACTIVE, WelfareService.ServiceStatus.UPCOMING)),
                eq("일자리"),
                any(Pageable.class)
        )).thenReturn(List.of());

        PolicyExplorationService.ChatExplorationTrace trace = policyExplorationService.traceChatCandidates(
                new ChatPolicyReadCondition("취업", 5, "job-startup", "일자리", List.of("창업", "금융", "사업", "자금"))
        );

        assertThat(trace.searchKeyword()).isEqualTo("취업 창업 금융 사업");
    }

    @Test
    @DisplayName("chat candidate 탐색은 사용자 지역 매칭 후보를 타지역 로컬 후보보다 앞에 둔다")
    void traceChatCandidatesPrefersUserRegionMatches() {
        WelfareService iksanLocal = service(858L, "익산형 청년월세 지원사업", "주거");
        WelfareService national = service(2666L, "청년월세 지원사업", "주거");
        WelfareService seoul = service(11160L, "서울시 청년 월세 지원", "주거");

        when(welfareServiceSearchRepository.searchChatCandidates("서울 월세 주거비 지원금", 12))
                .thenReturn(List.of(seoul));
        when(welfareServiceSearchRepository.searchChatCandidates("월세 쪽으로 보여줘 주거비 지원금", 12))
                .thenReturn(List.of(iksanLocal, national, seoul));
        when(welfareServiceRepository.findServiceIdsWithRegions(List.of(11160L, 858L, 2666L)))
                .thenReturn(List.of(858L, 11160L));
        when(welfareServiceRepository.findRegionMatchedServiceIds(
                List.of(11160L, 858L, 2666L),
                "11440",
                "서울특별시",
                "마포구"
        )).thenReturn(List.of(11160L));

        PolicyExplorationService.ChatExplorationTrace trace = policyExplorationService.traceChatCandidates(
                new ChatPolicyReadCondition(
                        "월세 쪽으로 보여줘",
                        3,
                        "housing-cash",
                        "주거",
                        List.of("월세", "주거비", "지원금"),
                        "11440",
                        "서울특별시",
                        "마포구"
                )
        );

        assertThat(trace.finalCandidates()).extracting(WelfareService::getId)
                .containsExactly(11160L, 2666L, 858L);
    }

    @Test
    @DisplayName("chat candidate 탐색은 branch term 미일치 지역 후보보다 term 일치 후보를 우선한다")
    void traceChatCandidatesPrefersBranchTermBeforeRegionOnlyMatch() {
        WelfareService seoulJeonse = service(14912L, "전세보증금반환보증 보증료 지원", "주거");
        WelfareService nationalMonthly = service(2666L, "청년월세 지원사업", "주거");
        WelfareService iksanMonthly = service(858L, "익산형 청년월세 지원사업", "주거");

        when(welfareServiceSearchRepository.searchChatCandidates("서울 월세 주거비 지원금", 12))
                .thenReturn(List.of(seoulJeonse, nationalMonthly, iksanMonthly));
        when(welfareServiceSearchRepository.searchChatCandidates("월세 쪽으로 보여줘 주거비 지원금", 12))
                .thenReturn(List.of(seoulJeonse, nationalMonthly, iksanMonthly));
        when(welfareServiceRepository.findServiceIdsWithRegions(List.of(14912L, 2666L, 858L)))
                .thenReturn(List.of(14912L, 858L));
        when(welfareServiceRepository.findRegionMatchedServiceIds(
                List.of(14912L, 2666L, 858L),
                "11440",
                "서울특별시",
                "마포구"
        )).thenReturn(List.of(14912L));

        PolicyExplorationService.ChatExplorationTrace trace = policyExplorationService.traceChatCandidates(
                new ChatPolicyReadCondition(
                        "월세 쪽으로 보여줘",
                        3,
                        "housing-cash",
                        "주거",
                        List.of("월세", "주거비", "지원금"),
                        "11440",
                        "서울특별시",
                        "마포구"
                )
        );

        assertThat(trace.finalCandidates()).extracting(WelfareService::getId)
                .containsExactly(2666L, 858L, 14912L);
    }

    @Test
    @DisplayName("recommendation base 탐색은 regionCode 경로를 유지한다")
    void findRecommendationBaseCandidatesUsesRegionCodeQuery() {
        RecommendationCandidateReadCondition condition =
                new RecommendationCandidateReadCondition(26, 5, "서울특별시", null, "11680", 150, 20);
        WelfareService candidate = service(3L, "서울 청년 지원", null);
        when(welfareServiceRepository.findCandidatesWithRegionCode(eq(26), eq(5), eq("11680"), eq("서울특별시"), eq(null), any(Pageable.class)))
                .thenReturn(List.of(candidate));

        List<WelfareService> candidates = policyExplorationService.findRecommendationBaseCandidates(condition);

        assertThat(candidates).containsExactly(candidate);
        verify(welfareServiceRepository).findCandidatesWithRegionCode(eq(26), eq(5), eq("11680"), eq("서울특별시"), eq(null), any(Pageable.class));
    }

    @Test
    @DisplayName("recommendation latest 탐색은 regionCode 경로를 유지한다")
    void findRecommendationLatestCandidatesUsesRegionCodeQuery() {
        RecommendationCandidateReadCondition condition =
                new RecommendationCandidateReadCondition(26, 5, "서울특별시", null, "11680", 150, 20);
        WelfareService candidate = service(4L, "최신 서울 청년 지원", null);
        when(welfareServiceRepository.findLatestCandidatesWithRegionCode(eq(26), eq(5), eq("11680"), eq("서울특별시"), eq(null), any(Pageable.class)))
                .thenReturn(List.of(candidate));

        List<WelfareService> candidates = policyExplorationService.findRecommendationLatestCandidates(condition);

        assertThat(candidates).containsExactly(candidate);
        verify(welfareServiceRepository).findLatestCandidatesWithRegionCode(eq(26), eq(5), eq("11680"), eq("서울특별시"), eq(null), any(Pageable.class));
    }

    private WelfareService service(Long id, String title, String category) {
        return WelfareService.builder()
                .id(id)
                .sourceType(WelfareService.SourceType.YOUTH)
                .sourceId("test-" + id)
                .title(title)
                .unifiedCategory(category)
                .status(WelfareService.ServiceStatus.ACTIVE)
                .searchYouthRelevant(true)
                .build();
    }
}
