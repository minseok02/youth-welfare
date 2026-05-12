package com.example.welfare.policy.service;

import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.example.welfare.policy.dto.PolicySearchResponse;
import com.example.welfare.policy.dto.PolicySummaryResponse;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.repository.PolicySearchReadCondition;
import com.example.welfare.policy.repository.WelfareServiceReadRepository;
import com.example.welfare.recommend.dto.RecommendationCandidateProjection;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PolicySearchServiceTest {

    @Mock
    private WelfareServiceReadRepository welfareServiceReadRepository;
    @Mock
    private PolicyPresentationReadService policyPresentationReadService;

    @Test
    @DisplayName("검색은 SQL 레벨 청년 플래그 필터 결과를 페이지 메타데이터와 함께 반환한다")
    void searchReturnsPagedResponse() {
        PolicySearchService service = new PolicySearchService(
                welfareServiceReadRepository,
                policyPresentationReadService
        );

        WelfareService youthService = welfareService(1L, "청년 정책");
        given(welfareServiceReadRepository.search(
                eq(new PolicySearchReadCondition(
                        "청년", null, "ACTIVE_ONLY", null, null, null, null, null, "RELEVANCE", null, null
                )),
                any(PageRequest.class)
        )).willReturn(new PageImpl<>(List.of(youthService), PageRequest.of(0, 10), 21));
        given(policyPresentationReadService.buildSummaryPage(eq(null), any(org.springframework.data.domain.Page.class)))
                .willReturn(new PageImpl<>(
                        List.of(PolicySummaryResponse.from(
                                youthService,
                                false,
                                RecommendationCandidateProjection.builder()
                                        .serviceId(1L)
                                        .unifiedCategoryCompat("주거")
                                        .build()
                        )),
                        PageRequest.of(0, 10),
                        21
                ));

        PolicySearchResponse results = service.search(null, "청년", null, null, null, null, null, null, null, null, null, null, 0, 10);

        assertThat(results.getContent()).hasSize(1);
        assertThat(results.getContent().get(0).getUnifiedCategory()).isEqualTo("주거");
        assertThat(results.getTotalElements()).isEqualTo(21);
        assertThat(results.getTotalPages()).isEqualTo(3);
        assertThat(results.isHasNext()).isTrue();

        ArgumentCaptor<PageRequest> captor = ArgumentCaptor.forClass(PageRequest.class);
        verify(welfareServiceReadRepository).search(
                eq(new PolicySearchReadCondition(
                        "청년", null, "ACTIVE_ONLY", null, null, null, null, null, "RELEVANCE", null, null
                )),
                captor.capture()
        );
        assertThat(captor.getValue().getPageNumber()).isEqualTo(0);
        assertThat(captor.getValue().getPageSize()).isEqualTo(10);
    }

    @Test
    @DisplayName("시도와 시군구가 있으면 지역 검색 쿼리를 사용한다")
    void searchWithSidoAndSggUsesRegionQuery() {
        PolicySearchService service = new PolicySearchService(
                welfareServiceReadRepository,
                policyPresentationReadService
        );

        WelfareService youthService = welfareService(2L, "서울 청년 정책");
        given(welfareServiceReadRepository.search(
                eq(new PolicySearchReadCondition(
                        "청년", null, "ACTIVE_ONLY", null, null, null, "서울특별시", "관악구", "RELEVANCE", null, null
                )),
                any(PageRequest.class)
        )).willReturn(new PageImpl<>(List.of(youthService), PageRequest.of(0, 10), 1));
        given(policyPresentationReadService.buildSummaryPage(eq(null), any(org.springframework.data.domain.Page.class)))
                .willReturn(new PageImpl<>(List.of(PolicySummaryResponse.from(youthService, false)), PageRequest.of(0, 10), 1));

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
                null,
                null,
                0,
                10
        );

        assertThat(results.getContent()).hasSize(1);
        verify(welfareServiceReadRepository).search(
                eq(new PolicySearchReadCondition(
                        "청년", null, "ACTIVE_ONLY", null, null, null, "서울특별시", "관악구", "RELEVANCE", null, null
                )),
                any(PageRequest.class)
        );
    }

    @Test
    @DisplayName("시도만 있으면 시도 전용 지역 검색 쿼리를 사용한다")
    void searchWithSidoOnlyUsesSidoQuery() {
        PolicySearchService service = new PolicySearchService(
                welfareServiceReadRepository,
                policyPresentationReadService
        );

        WelfareService youthService = welfareService(3L, "서울 전체 청년 정책");
        given(welfareServiceReadRepository.search(
                eq(new PolicySearchReadCondition(
                        "청년", null, "ACTIVE_ONLY", null, null, null, "서울특별시", null, "RELEVANCE", null, null
                )),
                any(PageRequest.class)
        )).willReturn(new PageImpl<>(List.of(youthService), PageRequest.of(0, 10), 1));
        given(policyPresentationReadService.buildSummaryPage(eq(null), any(org.springframework.data.domain.Page.class)))
                .willReturn(new PageImpl<>(List.of(PolicySummaryResponse.from(youthService, false)), PageRequest.of(0, 10), 1));

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
                null,
                null,
                0,
                10
        );

        assertThat(results.getContent()).hasSize(1);
        verify(welfareServiceReadRepository).search(
                eq(new PolicySearchReadCondition(
                        "청년", null, "ACTIVE_ONLY", null, null, null, "서울특별시", null, "RELEVANCE", null, null
                )),
                any(PageRequest.class)
        );
    }

    @Test
    @DisplayName("검색은 과도하게 긴 키워드를 거부한다")
    void searchRejectsTooLongKeyword() {
        PolicySearchService service = new PolicySearchService(
                welfareServiceReadRepository,
                policyPresentationReadService
        );

        String longKeyword = "a".repeat(101);

        assertThatThrownBy(() -> service.search(null, longKeyword, null, null, null, null, null, null, null, null, null, null, 0, 10))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("검색은 과도하게 많은 토큰을 거부한다")
    void searchRejectsTooManyTokens() {
        PolicySearchService service = new PolicySearchService(
                welfareServiceReadRepository,
                policyPresentationReadService
        );

        String manyTokens = "a b c d e f g h i j k";

        assertThatThrownBy(() -> service.search(null, manyTokens, null, null, null, null, null, null, null, null, null, null, 0, 10))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);
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
