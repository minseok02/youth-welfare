package com.example.welfare.recommend.service;

import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.repository.ServiceTagRepository;
import com.example.welfare.policy.repository.WelfareServiceRepository;
import com.example.welfare.recommend.dto.RecommendationCandidateProjection;
import com.example.welfare.recommend.dto.RetrievedRecommendationCandidates;
import com.example.welfare.recommend.dto.RecommendationUserSnapshot;
import com.example.welfare.recommend.repository.CanonicalRecommendationReadModelRepository;
import com.example.welfare.recommend.support.RecommendationYouthRelevanceSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RetrievalServiceTest {

    @Mock
    private WelfareServiceRepository welfareServiceRepository;

    @Mock
    private ServiceTagRepository serviceTagRepository;

    @Mock
    private RecommendationYouthRelevanceSupport recommendationYouthRelevanceSupport;

    @Mock
    private CanonicalRecommendationReadModelRepository canonicalRecommendationReadModelRepository;

    @Test
    @DisplayName("regionCode가 있으면 regionCode 추천 쿼리를 사용한다")
    void retrieveUsesRegionCodeQueriesWhenRegionCodeExists() {
        RetrievalService service = new RetrievalService(
                welfareServiceRepository,
                serviceTagRepository,
                recommendationYouthRelevanceSupport,
                canonicalRecommendationReadModelRepository
        );

        RecommendationUserSnapshot user = user("서울특별시", "11680");
        WelfareService candidate = welfareService(1L, "청년 월세 지원");
        given(welfareServiceRepository.findCandidatesWithRegionCode(eq(26), eq(5), eq("11680"), any(PageRequest.class)))
                .willReturn(List.of(candidate));
        given(welfareServiceRepository.findLatestCandidatesWithRegionCode(eq(26), eq(5), eq("11680"), any(PageRequest.class)))
                .willReturn(List.of());
        given(serviceTagRepository.findByServiceIdIn(any())).willReturn(Collections.emptyList());
        given(canonicalRecommendationReadModelRepository.findByServiceIds(any()))
                .willReturn(Map.of(1L, projection(1L, true)));

        RetrievedRecommendationCandidates results = service.retrieve("youth_all", user);

        assertThat(results.candidates()).extracting(WelfareService::getId).containsExactly(1L);
        verify(welfareServiceRepository).findCandidatesWithRegionCode(eq(26), eq(5), eq("11680"), any(PageRequest.class));
        verify(welfareServiceRepository).findLatestCandidatesWithRegionCode(eq(26), eq(5), eq("11680"), any(PageRequest.class));
        verify(welfareServiceRepository, never()).findCandidatesWithSido(any(Integer.class), any(Integer.class), any(), any(PageRequest.class));
        verify(canonicalRecommendationReadModelRepository).findByServiceIds(argThat(ids -> ids.equals(List.of(1L))));
    }

    @Test
    @DisplayName("regionCode가 없고 sido만 있으면 sido 추천 쿼리를 사용한다")
    void retrieveUsesSidoQueriesWhenRegionCodeMissing() {
        RetrievalService service = new RetrievalService(
                welfareServiceRepository,
                serviceTagRepository,
                recommendationYouthRelevanceSupport,
                canonicalRecommendationReadModelRepository
        );

        RecommendationUserSnapshot user = user("서울특별시", null);
        WelfareService candidate = welfareService(2L, "청년 취업 지원");
        given(welfareServiceRepository.findCandidatesWithSido(eq(26), eq(5), eq("서울특별시"), any(PageRequest.class)))
                .willReturn(List.of(candidate));
        given(welfareServiceRepository.findLatestCandidatesWithSido(eq(26), eq(5), eq("서울특별시"), any(PageRequest.class)))
                .willReturn(List.of());
        given(serviceTagRepository.findByServiceIdIn(any())).willReturn(Collections.emptyList());
        given(canonicalRecommendationReadModelRepository.findByServiceIds(any()))
                .willReturn(Map.of(2L, projection(2L, true)));

        RetrievedRecommendationCandidates results = service.retrieve("youth_all", user);

        assertThat(results.candidates()).extracting(WelfareService::getId).containsExactly(2L);
        verify(welfareServiceRepository).findCandidatesWithSido(eq(26), eq(5), eq("서울특별시"), any(PageRequest.class));
        verify(welfareServiceRepository).findLatestCandidatesWithSido(eq(26), eq(5), eq("서울특별시"), any(PageRequest.class));
        verify(welfareServiceRepository, never()).findCandidatesWithRegionCode(any(Integer.class), any(Integer.class), any(), any(PageRequest.class));
        verify(canonicalRecommendationReadModelRepository).findByServiceIds(argThat(ids -> ids.equals(List.of(2L))));
    }

    @Test
    @DisplayName("canonical projection 이 있으면 youth heuristic 대신 projection relevance를 사용한다")
    void retrieveUsesProjectionYouthRelevanceBeforeFallbackHeuristic() {
        RetrievalService service = new RetrievalService(
                welfareServiceRepository,
                serviceTagRepository,
                recommendationYouthRelevanceSupport,
                canonicalRecommendationReadModelRepository
        );

        RecommendationUserSnapshot user = user("서울특별시", "11680");
        WelfareService candidate = welfareService(3L, "일반 복지");
        given(welfareServiceRepository.findCandidatesWithRegionCode(eq(26), eq(5), eq("11680"), any(PageRequest.class)))
                .willReturn(List.of(candidate));
        given(welfareServiceRepository.findLatestCandidatesWithRegionCode(eq(26), eq(5), eq("11680"), any(PageRequest.class)))
                .willReturn(List.of());
        given(serviceTagRepository.findByServiceIdIn(any())).willReturn(Collections.emptyList());
        given(canonicalRecommendationReadModelRepository.findByServiceIds(any()))
                .willReturn(Map.of(3L, projection(3L, false)));

        RetrievedRecommendationCandidates results = service.retrieve("youth_all", user);

        assertThat(results.candidates()).isEmpty();
        verify(recommendationYouthRelevanceSupport, never()).isYouthRelevant(eq(candidate), any());
    }

    private RecommendationUserSnapshot user(String sido, String regionCode) {
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
                List.of()
        );
    }

    private WelfareService welfareService(Long id, String title) {
        return WelfareService.builder()
                .id(id)
                .sourceType(WelfareService.SourceType.YOUTH)
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
