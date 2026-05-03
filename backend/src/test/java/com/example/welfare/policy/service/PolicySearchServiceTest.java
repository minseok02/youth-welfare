package com.example.welfare.policy.service;

import com.example.welfare.policy.dto.PolicySearchResponse;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.repository.WelfareServiceRepository;
import com.example.welfare.recommend.dto.RecommendationCandidateProjection;
import com.example.welfare.recommend.facade.RecommendationReadFacade;
import com.example.welfare.recommend.repository.CanonicalRecommendationReadModelRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PolicySearchServiceTest {

    @Mock
    private WelfareServiceRepository welfareServiceRepository;

    @Mock
    private RecommendationReadFacade recommendationReadFacade;
    @Mock
    private CanonicalRecommendationReadModelRepository canonicalRecommendationReadModelRepository;

    @Test
    @DisplayName("검색은 SQL 레벨 청년 플래그 필터 결과를 페이지 메타데이터와 함께 반환한다")
    void searchReturnsPagedResponse() {
        PolicySearchService service = new PolicySearchService(
                welfareServiceRepository,
                recommendationReadFacade,
                canonicalRecommendationReadModelRepository
        );

        WelfareService youthService = welfareService(1L, "청년 정책");
        given(welfareServiceRepository.searchByKeywordWithFiltersNoRegion(
                eq("+청년"),
                isNull(),
                eq(0),
                isNull(),
                isNull(),
                isNull(),
                eq("RELEVANCE"),
                any(PageRequest.class)
        )).willReturn(new PageImpl<>(List.of(youthService), PageRequest.of(0, 10), 21));
        given(canonicalRecommendationReadModelRepository.findByServiceIds(List.of(1L)))
                .willReturn(java.util.Map.of(
                        1L,
                        RecommendationCandidateProjection.builder()
                                .serviceId(1L)
                                .unifiedCategoryCompat("주거")
                                .build()
                ));

        PolicySearchResponse results = service.search(null, "청년", null, null, null, null, null, null, null, null, 0, 10);

        assertThat(results.getContent()).hasSize(1);
        assertThat(results.getContent().get(0).getUnifiedCategory()).isEqualTo("주거");
        assertThat(results.getTotalElements()).isEqualTo(21);
        assertThat(results.getTotalPages()).isEqualTo(3);
        assertThat(results.isHasNext()).isTrue();

        ArgumentCaptor<PageRequest> captor = ArgumentCaptor.forClass(PageRequest.class);
        verify(welfareServiceRepository).searchByKeywordWithFiltersNoRegion(
                eq("+청년"),
                isNull(),
                eq(0),
                isNull(),
                isNull(),
                isNull(),
                eq("RELEVANCE"),
                captor.capture()
        );
        assertThat(captor.getValue().getPageNumber()).isEqualTo(0);
        assertThat(captor.getValue().getPageSize()).isEqualTo(10);
    }

    @Test
    @DisplayName("시도와 시군구가 있으면 지역 검색 쿼리를 사용한다")
    void searchWithSidoAndSggUsesRegionQuery() {
        PolicySearchService service = new PolicySearchService(
                welfareServiceRepository,
                recommendationReadFacade,
                canonicalRecommendationReadModelRepository
        );

        WelfareService youthService = welfareService(2L, "서울 청년 정책");
        given(welfareServiceRepository.searchByKeywordWithFiltersWithSidoSgg(
                eq("+청년"),
                isNull(),
                eq(0),
                isNull(),
                isNull(),
                isNull(),
                eq("서울특별시"),
                eq("관악구"),
                eq("RELEVANCE"),
                any(PageRequest.class)
        )).willReturn(new PageImpl<>(List.of(youthService), PageRequest.of(0, 10), 1));
        given(canonicalRecommendationReadModelRepository.findByServiceIds(List.of(2L)))
                .willReturn(java.util.Map.of());

        PolicySearchResponse results = service.search(
                null,
                "청년",
                null,
                null,
                null,
                null,
                null,
                "서울특별시",
                "관악구",
                null,
                0,
                10
        );

        assertThat(results.getContent()).hasSize(1);
        verify(welfareServiceRepository).searchByKeywordWithFiltersWithSidoSgg(
                eq("+청년"),
                isNull(),
                eq(0),
                isNull(),
                isNull(),
                isNull(),
                eq("서울특별시"),
                eq("관악구"),
                eq("RELEVANCE"),
                any(PageRequest.class)
        );
    }

    @Test
    @DisplayName("시도만 있으면 시도 전용 지역 검색 쿼리를 사용한다")
    void searchWithSidoOnlyUsesSidoQuery() {
        PolicySearchService service = new PolicySearchService(
                welfareServiceRepository,
                recommendationReadFacade,
                canonicalRecommendationReadModelRepository
        );

        WelfareService youthService = welfareService(3L, "서울 전체 청년 정책");
        given(welfareServiceRepository.searchByKeywordWithFiltersWithSido(
                eq("+청년"),
                isNull(),
                eq(0),
                isNull(),
                isNull(),
                isNull(),
                eq("서울특별시"),
                eq("RELEVANCE"),
                any(PageRequest.class)
        )).willReturn(new PageImpl<>(List.of(youthService), PageRequest.of(0, 10), 1));
        given(canonicalRecommendationReadModelRepository.findByServiceIds(List.of(3L)))
                .willReturn(java.util.Map.of());

        PolicySearchResponse results = service.search(
                null,
                "청년",
                null,
                null,
                null,
                null,
                null,
                "서울특별시",
                null,
                null,
                0,
                10
        );

        assertThat(results.getContent()).hasSize(1);
        verify(welfareServiceRepository).searchByKeywordWithFiltersWithSido(
                eq("+청년"),
                isNull(),
                eq(0),
                isNull(),
                isNull(),
                isNull(),
                eq("서울특별시"),
                eq("RELEVANCE"),
                any(PageRequest.class)
        );
    }

    private WelfareService welfareService(Long id, String title) {
        return WelfareService.builder()
                .id(id)
                .sourceType(WelfareService.SourceType.BOKJIRO_CENTRAL)
                .sourceId("S" + id)
                .title(title)
                .status(WelfareService.ServiceStatus.ACTIVE)
                .searchYouthRelevant(true)
                .build();
    }
}
