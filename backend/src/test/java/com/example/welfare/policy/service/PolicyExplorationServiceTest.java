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
import static org.mockito.ArgumentMatchers.eq;
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
    @DisplayName("recommendation base 탐색은 regionCode 경로를 유지한다")
    void findRecommendationBaseCandidatesUsesRegionCodeQuery() {
        RecommendationCandidateReadCondition condition =
                new RecommendationCandidateReadCondition(26, 5, "서울특별시", "11680", 150, 20);
        WelfareService candidate = service(3L, "서울 청년 지원", null);
        when(welfareServiceRepository.findCandidatesWithRegionCode(eq(26), eq(5), eq("11680"), eq("서울특별시"), any(Pageable.class)))
                .thenReturn(List.of(candidate));

        List<WelfareService> candidates = policyExplorationService.findRecommendationBaseCandidates(condition);

        assertThat(candidates).containsExactly(candidate);
        verify(welfareServiceRepository).findCandidatesWithRegionCode(eq(26), eq(5), eq("11680"), eq("서울특별시"), any(Pageable.class));
    }

    @Test
    @DisplayName("recommendation latest 탐색은 regionCode 경로를 유지한다")
    void findRecommendationLatestCandidatesUsesRegionCodeQuery() {
        RecommendationCandidateReadCondition condition =
                new RecommendationCandidateReadCondition(26, 5, "서울특별시", "11680", 150, 20);
        WelfareService candidate = service(4L, "최신 서울 청년 지원", null);
        when(welfareServiceRepository.findLatestCandidatesWithRegionCode(eq(26), eq(5), eq("11680"), eq("서울특별시"), any(Pageable.class)))
                .thenReturn(List.of(candidate));

        List<WelfareService> candidates = policyExplorationService.findRecommendationLatestCandidates(condition);

        assertThat(candidates).containsExactly(candidate);
        verify(welfareServiceRepository).findLatestCandidatesWithRegionCode(eq(26), eq(5), eq("11680"), eq("서울특별시"), any(Pageable.class));
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
