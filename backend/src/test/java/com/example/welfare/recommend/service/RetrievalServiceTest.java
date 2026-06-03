package com.example.welfare.recommend.service;

import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.recommend.dto.PriorityPreference;
import com.example.welfare.recommend.dto.RecommendationCandidateProjection;
import com.example.welfare.recommend.dto.RetrievedRecommendationCandidates;
import com.example.welfare.recommend.dto.RecommendationUserSnapshot;
import com.example.welfare.recommend.repository.RecommendationCandidateReadCondition;
import com.example.welfare.recommend.repository.RecommendationCandidateReadRepository;
import com.example.welfare.recommend.support.RecommendationYouthRelevanceSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RetrievalServiceTest {

    @Mock
    private RecommendationCandidateReadRepository recommendationCandidateReadRepository;

    @Mock
    private RecommendationYouthRelevanceSupport recommendationYouthRelevanceSupport;

    @Mock
    private RecommendationProjectionReadService recommendationProjectionReadService;

    @Test
    @DisplayName("regionCode가 있으면 regionCode 추천 쿼리를 사용한다")
    void retrieveUsesRegionCodeQueriesWhenRegionCodeExists() {
        RetrievalService service = new RetrievalService(
                recommendationCandidateReadRepository,
                recommendationYouthRelevanceSupport,
                recommendationProjectionReadService
        );

        RecommendationUserSnapshot user = user("서울특별시", "11680");
        WelfareService candidate = welfareService(1L, "청년 월세 지원");
        given(recommendationCandidateReadRepository.findBaseCandidates(
                new RecommendationCandidateReadCondition(26, 5, "서울특별시", "강남구", "11680", 300, 20)
        ))
                .willReturn(List.of(candidate));
        given(recommendationCandidateReadRepository.findLatestCandidates(
                new RecommendationCandidateReadCondition(26, 5, "서울특별시", "강남구", "11680", 300, 20)
        ))
                .willReturn(List.of());
        given(recommendationCandidateReadRepository.findTagsByServiceIds(any())).willReturn(Collections.emptyMap());
        given(recommendationProjectionReadService.findCandidateProjectionsByServiceIds(any()))
                .willReturn(Map.of(1L, projection(1L, true)));

        RetrievedRecommendationCandidates results = service.retrieve("youth_all", user);

        assertThat(results.candidates()).extracting(WelfareService::getId).containsExactly(1L);
        verify(recommendationCandidateReadRepository).findBaseCandidates(
                new RecommendationCandidateReadCondition(26, 5, "서울특별시", "강남구", "11680", 300, 20)
        );
        verify(recommendationCandidateReadRepository).findLatestCandidates(
                new RecommendationCandidateReadCondition(26, 5, "서울특별시", "강남구", "11680", 300, 20)
        );
        verify(recommendationProjectionReadService).findCandidateProjectionsByServiceIds(argThat(ids -> ids.equals(List.of(1L))));
    }

    @Test
    @DisplayName("regionCode가 없고 sido만 있으면 sido 추천 쿼리를 사용한다")
    void retrieveUsesSidoQueriesWhenRegionCodeMissing() {
        RetrievalService service = new RetrievalService(
                recommendationCandidateReadRepository,
                recommendationYouthRelevanceSupport,
                recommendationProjectionReadService
        );

        RecommendationUserSnapshot user = user("서울특별시", null);
        WelfareService candidate = welfareService(2L, "청년 취업 지원");
        given(recommendationCandidateReadRepository.findBaseCandidates(
                new RecommendationCandidateReadCondition(26, 5, "서울특별시", "강남구", null, 300, 20)
        ))
                .willReturn(List.of(candidate));
        given(recommendationCandidateReadRepository.findLatestCandidates(
                new RecommendationCandidateReadCondition(26, 5, "서울특별시", "강남구", null, 300, 20)
        ))
                .willReturn(List.of());
        given(recommendationCandidateReadRepository.findTagsByServiceIds(any())).willReturn(Collections.emptyMap());
        given(recommendationProjectionReadService.findCandidateProjectionsByServiceIds(any()))
                .willReturn(Map.of(2L, projection(2L, true)));

        RetrievedRecommendationCandidates results = service.retrieve("youth_all", user);

        assertThat(results.candidates()).extracting(WelfareService::getId).containsExactly(2L);
        verify(recommendationCandidateReadRepository).findBaseCandidates(
                new RecommendationCandidateReadCondition(26, 5, "서울특별시", "강남구", null, 300, 20)
        );
        verify(recommendationCandidateReadRepository).findLatestCandidates(
                new RecommendationCandidateReadCondition(26, 5, "서울특별시", "강남구", null, 300, 20)
        );
        verify(recommendationProjectionReadService).findCandidateProjectionsByServiceIds(argThat(ids -> ids.equals(List.of(2L))));
    }

    @Test
    @DisplayName("canonical projection 이 있으면 youth heuristic 대신 projection relevance를 사용한다")
    void retrieveUsesProjectionYouthRelevanceBeforeFallbackHeuristic() {
        RetrievalService service = new RetrievalService(
                recommendationCandidateReadRepository,
                recommendationYouthRelevanceSupport,
                recommendationProjectionReadService
        );

        RecommendationUserSnapshot user = user("서울특별시", "11680");
        WelfareService candidate = welfareService(3L, "일반 복지");
        given(recommendationCandidateReadRepository.findBaseCandidates(
                new RecommendationCandidateReadCondition(26, 5, "서울특별시", "강남구", "11680", 300, 20)
        ))
                .willReturn(List.of(candidate));
        given(recommendationCandidateReadRepository.findLatestCandidates(
                new RecommendationCandidateReadCondition(26, 5, "서울특별시", "강남구", "11680", 300, 20)
        ))
                .willReturn(List.of());
        given(recommendationCandidateReadRepository.findTagsByServiceIds(any())).willReturn(Collections.emptyMap());
        given(recommendationProjectionReadService.findCandidateProjectionsByServiceIds(any()))
                .willReturn(Map.of(3L, projection(3L, false)));

        RetrievedRecommendationCandidates results = service.retrieve("youth_all", user);

        assertThat(results.candidates()).isEmpty();
        verify(recommendationYouthRelevanceSupport, never()).isYouthRelevant(same(candidate), any());
    }

    @Test
    @DisplayName("priority가 비어 있으면 base candidates를 source round-robin으로 섞어 상위 구간 독점을 완화한다")
    void retrieveRebalancesSourceOrderForNoPriorityUsers() {
        RetrievalService service = new RetrievalService(
                recommendationCandidateReadRepository,
                recommendationYouthRelevanceSupport,
                recommendationProjectionReadService
        );

        RecommendationUserSnapshot user = user("서울특별시", "11680");
        WelfareService youth1 = welfareService(11L, WelfareService.SourceType.YOUTH, "청년 정책 1");
        WelfareService youth2 = welfareService(12L, WelfareService.SourceType.YOUTH, "청년 정책 2");
        WelfareService bokjiro1 = welfareService(13L, WelfareService.SourceType.BOKJIRO_CENTRAL, "복지로 정책 1");
        WelfareService bokjiro2 = welfareService(14L, WelfareService.SourceType.BOKJIRO_CENTRAL, "복지로 정책 2");
        WelfareService gov241 = welfareService(15L, WelfareService.SourceType.GOV24, "Gov24 정책 1");

        List<WelfareService> grouped = List.of(youth1, youth2, bokjiro1, bokjiro2, gov241);
        given(recommendationCandidateReadRepository.findBaseCandidates(
                new RecommendationCandidateReadCondition(26, 5, "서울특별시", "강남구", "11680", 300, 20)
        ))
                .willReturn(grouped);
        given(recommendationCandidateReadRepository.findLatestCandidates(
                new RecommendationCandidateReadCondition(26, 5, "서울특별시", "강남구", "11680", 300, 20)
        ))
                .willReturn(List.of());
        given(recommendationCandidateReadRepository.findTagsByServiceIds(any())).willReturn(Collections.emptyMap());
        given(recommendationProjectionReadService.findCandidateProjectionsByServiceIds(any()))
                .willReturn(Map.of(
                        11L, projection(11L, true),
                        12L, projection(12L, true),
                        13L, projection(13L, true),
                        14L, projection(14L, true),
                        15L, projection(15L, true)
                ));

        RetrievedRecommendationCandidates results = service.retrieve("youth_all", user);

        assertThat(results.candidates()).extracting(WelfareService::getId)
                .containsExactly(11L, 13L, 15L, 12L, 14L);
    }

    @Test
    @DisplayName("region 기반 후보 창이 local source로 과점되면 41~50 구간에 non-dominant source를 bounded하게 끼워 넣는다")
    void retrieveRebalancesRegionDominatedWindowTail() {
        RetrievalService service = new RetrievalService(
                recommendationCandidateReadRepository,
                recommendationYouthRelevanceSupport,
                recommendationProjectionReadService
        );

        RecommendationUserSnapshot user = user("경기도", null, List.of(new PriorityPreference(1, "EDUCATION", 1.0)));
        List<WelfareService> dominantLocals = java.util.stream.LongStream.rangeClosed(1, 50)
                .mapToObj(id -> welfareService(id, WelfareService.SourceType.BOKJIRO_LOCAL, "경기 로컬 " + id))
                .toList();
        List<WelfareService> nonDominant = List.of(
                welfareService(1001L, WelfareService.SourceType.GOV24, "Gov24 교육 1"),
                welfareService(1002L, WelfareService.SourceType.YOUTH, "Youth 교육 1"),
                welfareService(1003L, WelfareService.SourceType.GOV24, "Gov24 교육 2")
        );
        List<WelfareService> grouped = new java.util.ArrayList<>(dominantLocals);
        grouped.addAll(nonDominant);

        given(recommendationCandidateReadRepository.findBaseCandidates(
                new RecommendationCandidateReadCondition(26, 5, "경기도", "강남구", null, 300, 20)
        )).willReturn(grouped);
        given(recommendationCandidateReadRepository.findLatestCandidates(
                new RecommendationCandidateReadCondition(26, 5, "경기도", "강남구", null, 300, 20)
        )).willReturn(List.of());
        given(recommendationCandidateReadRepository.findTagsByServiceIds(any())).willReturn(Collections.emptyMap());

        Map<Long, RecommendationCandidateProjection> projections = new java.util.LinkedHashMap<>();
        grouped.forEach(candidate -> projections.put(candidate.getId(), projection(candidate.getId(), true)));
        given(recommendationProjectionReadService.findCandidateProjectionsByServiceIds(any())).willReturn(projections);

        RetrievedRecommendationCandidates results = service.retrieve("youth_all", user);

        assertThat(results.candidates()).hasSize(50);
        assertThat(results.candidates().subList(0, 40)).allMatch(candidate -> candidate.getSourceType() == WelfareService.SourceType.BOKJIRO_LOCAL);
        assertThat(results.candidates().subList(40, 50))
                .extracting(WelfareService::getSourceType)
                .contains(WelfareService.SourceType.GOV24, WelfareService.SourceType.YOUTH);
    }

    private RecommendationUserSnapshot user(String sido, String regionCode) {
        return user(sido, regionCode, List.of());
    }

    private RecommendationUserSnapshot user(String sido, String regionCode, List<PriorityPreference> priorities) {
        return new RecommendationUserSnapshot(
                1L,
                "user-key-1",
                26,
                "25_29",
                sido,
                "강남구",
                regionCode,
                (byte) 5,
                null,
                null,
                10,
                0.5,
                List.of(),
                List.of(),
                priorities
        );
    }

    private WelfareService welfareService(Long id, String title) {
        return welfareService(id, WelfareService.SourceType.YOUTH, title);
    }

    private WelfareService welfareService(Long id, WelfareService.SourceType sourceType, String title) {
        return WelfareService.builder()
                .id(id)
                .sourceType(sourceType)
                .sourceId("R" + id)
                .title(title)
                .status(WelfareService.ServiceStatus.ACTIVE)
                .minAge(19)
                .maxAge(34)
                .minIncome(1)
                .maxIncome(10)
                .apiViewCount(0L)
                .build();
    }

    private RecommendationCandidateProjection projection(Long serviceId, boolean youthRelevant) {
        return RecommendationCandidateProjection.builder()
                .serviceId(serviceId)
                .youthRelevant(youthRelevant)
                .build();
    }
}
