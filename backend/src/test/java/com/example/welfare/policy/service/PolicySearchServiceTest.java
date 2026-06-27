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

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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

    private PolicySearchService fixedClockService() {
        return new PolicySearchService(
                welfareServiceReadRepository,
                policyPresentationReadService,
                Clock.fixed(Instant.parse("2026-05-30T00:00:00Z"), ZoneOffset.UTC)
        );
    }

    @Test
    @DisplayName("검색은 SQL 레벨 청년 플래그 필터 결과를 페이지 메타데이터와 함께 반환한다")
    void searchReturnsPagedResponse() {
        PolicySearchService service = fixedClockService();

        WelfareService youthService = welfareService(1L, "청년 정책");
        given(welfareServiceReadRepository.search(
                eq(new PolicySearchReadCondition(
                        "청년", null, "ACTIVE_ONLY", null, null, null, null, null, "RELEVANCE", null, null, "주거·자립", "개인", "현금"
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

        PolicySearchResponse results = service.search(null, "청년", null, null, null, null, null, null, null, null, null, null, "주거·자립", "개인", "현금", 0, 10);

        assertThat(results.getContent()).hasSize(1);
        assertThat(results.getContent().get(0).getUnifiedCategory()).isEqualTo("주거");
        assertThat(results.getTotalElements()).isEqualTo(21);
        assertThat(results.getTotalPages()).isEqualTo(3);
        assertThat(results.isHasNext()).isTrue();

        ArgumentCaptor<PageRequest> captor = ArgumentCaptor.forClass(PageRequest.class);
        verify(welfareServiceReadRepository).search(
                eq(new PolicySearchReadCondition(
                        "청년", null, "ACTIVE_ONLY", null, null, null, null, null, "RELEVANCE", null, null, "주거·자립", "개인", "현금"
                )),
                captor.capture()
        );
        assertThat(captor.getValue().getPageNumber()).isEqualTo(0);
        assertThat(captor.getValue().getPageSize()).isEqualTo(10);
    }

    @Test
    @DisplayName("시도와 시군구가 있으면 지역 검색 쿼리를 사용한다")
    void searchWithSidoAndSggUsesRegionQuery() {
        PolicySearchService service = fixedClockService();

        WelfareService youthService = welfareService(2L, "서울 청년 정책");
        given(welfareServiceReadRepository.search(
                eq(new PolicySearchReadCondition(
                        "청년", null, "ACTIVE_ONLY", null, null, null, "서울특별시", "관악구", "RELEVANCE", null, null, null, null, null
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
                null,
                null,
                null,
                0,
                10
        );

        assertThat(results.getContent()).hasSize(1);
        verify(welfareServiceReadRepository).search(
                eq(new PolicySearchReadCondition(
                        "청년", null, "ACTIVE_ONLY", null, null, null, "서울특별시", "관악구", "RELEVANCE", null, null, null, null, null
                )),
                any(PageRequest.class)
        );
    }

    @Test
    @DisplayName("시도만 있으면 시도 전용 지역 검색 쿼리를 사용한다")
    void searchWithSidoOnlyUsesSidoQuery() {
        PolicySearchService service = fixedClockService();

        WelfareService youthService = welfareService(3L, "서울 전체 청년 정책");
        given(welfareServiceReadRepository.search(
                eq(new PolicySearchReadCondition(
                        "청년", null, "ACTIVE_ONLY", null, null, null, "서울특별시", null, "RELEVANCE", null, null, null, null, null
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
                null,
                null,
                null,
                0,
                10
        );

        assertThat(results.getContent()).hasSize(1);
        verify(welfareServiceReadRepository).search(
                eq(new PolicySearchReadCondition(
                        "청년", null, "ACTIVE_ONLY", null, null, null, "서울특별시", null, "RELEVANCE", null, null, null, null, null
                )),
                any(PageRequest.class)
        );
    }

    @Test
    @DisplayName("지역 필터 검색은 응답 summary regionLabel 기준으로 mismatch row를 후처리 제거한다")
    void searchWithRegionFilterDropsMismatchedRows() {
        PolicySearchService service = fixedClockService();

        WelfareService wrongRegion = welfareService(13999L, "경상남도 청년 월세 지원");
        WelfareService correctRegion = welfareService(6966L, "서울 청년 월세 지원");
        WelfareService national = welfareService(2591L, "주거안정 월세대출");
        PageRequest pageRequest = PageRequest.of(0, 9);

        given(welfareServiceReadRepository.search(
                eq(new PolicySearchReadCondition(
                        "월세", null, "ACTIVE_ONLY", null, null, null, "서울특별시", null, "RELEVANCE", null, null, null, null, null
                )),
                any(PageRequest.class)
        )).willReturn(new PageImpl<>(List.of(wrongRegion, correctRegion, national), pageRequest, 3));
        given(policyPresentationReadService.buildSummaryPage(eq(null), any(org.springframework.data.domain.Page.class)))
                .willReturn(new PageImpl<>(List.of(
                        PolicySummaryResponse.builder().id(13999L).title("경상남도 청년 월세 지원").regionLabel("경상남도").statusLabel("진행중").build(),
                        PolicySummaryResponse.builder().id(6966L).title("서울 청년 월세 지원").regionLabel("서울특별시 구로구").statusLabel("진행중").build(),
                        PolicySummaryResponse.builder().id(2591L).title("주거안정 월세대출").regionLabel(null).statusLabel("진행중").build()
                ), pageRequest, 3));

        PolicySearchResponse results = service.search(
                null,
                "월세",
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
                null,
                null,
                null,
                0,
                3
        );

        assertThat(results.getContent()).extracting(PolicySummaryResponse::getId)
                .containsExactly(6966L, 2591L);

        ArgumentCaptor<PageRequest> captor = ArgumentCaptor.forClass(PageRequest.class);
        verify(welfareServiceReadRepository).search(
                eq(new PolicySearchReadCondition(
                        "월세", null, "ACTIVE_ONLY", null, null, null, "서울특별시", null, "RELEVANCE", null, null, null, null, null
                )),
                captor.capture()
        );
        assertThat(captor.getValue().getPageSize()).isEqualTo(9);
    }

    @Test
    @DisplayName("검색은 과도하게 긴 키워드를 거부한다")
    void searchRejectsTooLongKeyword() {
        PolicySearchService service = fixedClockService();

        String longKeyword = "a".repeat(101);

        assertThatThrownBy(() -> service.search(null, longKeyword, null, null, null, null, null, null, null, null, null, null, null, null, null, 0, 10))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("검색은 과도하게 많은 토큰을 거부한다")
    void searchRejectsTooManyTokens() {
        PolicySearchService service = fixedClockService();

        String manyTokens = "a b c d e f g h i j k";

        assertThatThrownBy(() -> service.search(null, manyTokens, null, null, null, null, null, null, null, null, null, null, null, null, null, 0, 10))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("비로그인 동일 검색 반복 호출은 TTL 안에서 공개 검색 캐시를 재사용한다")
    void publicSearchReusesCacheWithinTtl() {
        PolicySearchService service = fixedClockService();

        WelfareService youthService = welfareService(11L, "청년 정책");
        PageRequest pageRequest = PageRequest.of(0, 10);
        PageImpl<WelfareService> page = new PageImpl<>(List.of(youthService), pageRequest, 21);
        PageImpl<PolicySummaryResponse> summaryPage = new PageImpl<>(
                List.of(PolicySummaryResponse.from(youthService, false)),
                pageRequest,
                21
        );

        given(welfareServiceReadRepository.search(
                eq(new PolicySearchReadCondition(
                        "청년", null, "ACTIVE_ONLY", null, null, null, null, null, "RELEVANCE", null, null, null, null, null
                )),
                any(PageRequest.class)
        )).willReturn(page);
        given(policyPresentationReadService.buildSummaryPage(eq(null), any(org.springframework.data.domain.Page.class)))
                .willReturn(summaryPage);

        service.search(null, "청년", null, null, null, null, null, null, null, null, null, null, null, null, null, 0, 10);
        service.search(null, "청년", null, null, null, null, null, null, null, null, null, null, null, null, null, 0, 10);

        verify(welfareServiceReadRepository).search(
                eq(new PolicySearchReadCondition(
                        "청년", null, "ACTIVE_ONLY", null, null, null, null, null, "RELEVANCE", null, null, null, null, null
                )),
                any(PageRequest.class)
        );
        verify(policyPresentationReadService).buildSummaryPage(eq(null), any(org.springframework.data.domain.Page.class));
    }

    @Test
    @DisplayName("로그인 검색은 북마크 차이 때문에 공개 검색 캐시를 재사용하지 않는다")
    void userSearchDoesNotReusePublicCache() {
        PolicySearchService service = fixedClockService();

        WelfareService youthService = welfareService(12L, "청년 정책");
        PageRequest pageRequest = PageRequest.of(0, 10);
        PageImpl<WelfareService> page = new PageImpl<>(List.of(youthService), pageRequest, 1);
        PageImpl<PolicySummaryResponse> summaryPage = new PageImpl<>(
                List.of(PolicySummaryResponse.from(youthService, true)),
                pageRequest,
                1
        );

        given(welfareServiceReadRepository.search(
                eq(new PolicySearchReadCondition(
                        "청년", null, "ACTIVE_ONLY", null, null, null, null, null, "RELEVANCE", null, null, null, null, null
                )),
                any(PageRequest.class)
        )).willReturn(page);
        given(policyPresentationReadService.buildSummaryPage(eq(7L), any(org.springframework.data.domain.Page.class)))
                .willReturn(summaryPage);

        service.search(7L, "청년", null, null, null, null, null, null, null, null, null, null, null, null, null, 0, 10);
        service.search(7L, "청년", null, null, null, null, null, null, null, null, null, null, null, null, null, 0, 10);

        verify(welfareServiceReadRepository, org.mockito.Mockito.times(2)).search(
                eq(new PolicySearchReadCondition(
                        "청년", null, "ACTIVE_ONLY", null, null, null, null, null, "RELEVANCE", null, null, null, null, null
                )),
                any(PageRequest.class)
        );
        verify(policyPresentationReadService, org.mockito.Mockito.times(2))
                .buildSummaryPage(eq(7L), any(org.springframework.data.domain.Page.class));
    }

    @Test
    @DisplayName("Gov24 서비스분야 filter는 managed exact label만 허용한다")
    void searchRejectsUnknownGov24ServiceField() {
        PolicySearchService service = fixedClockService();

        assertThatThrownBy(() -> service.search(
                null, "청년", null, null, null, null, null, null, null, null, null, null, "알수없음", null, null, 0, 10
        )).isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("Gov24 사용자구분 filter는 managed token만 허용한다")
    void searchRejectsUnknownGov24UserType() {
        PolicySearchService service = fixedClockService();

        assertThatThrownBy(() -> service.search(
                null, "청년", null, null, null, null, null, null, null, null, null, null, null, "청년", null, 0, 10
        )).isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("Gov24 지원유형 filter는 managed token만 허용한다")
    void searchRejectsUnknownGov24BenefitType() {
        PolicySearchService service = fixedClockService();

        assertThatThrownBy(() -> service.search(
                null, "청년", null, null, null, null, null, null, null, null, null, null, null, null, "생소한유형", 0, 10
        )).isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("정책 검색은 범위를 벗어난 incomeLevel을 invalid input으로 거부한다")
    void searchRejectsOutOfRangeIncomeLevel() {
        PolicySearchService service = fixedClockService();

        assertThatThrownBy(() -> service.search(
                null, "청년", null, null, null, null, null, null, null, null, 12, null, null, null, null, 0, 10
        )).isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("정책 검색은 알 수 없는 statusFilter를 invalid input으로 거부한다")
    void searchRejectsUnknownStatusFilter() {
        PolicySearchService service = fixedClockService();

        assertThatThrownBy(() -> service.search(
                null, "청년", null, "NOT_A_FILTER", null, null, null, null, null, null, null, null, null, null, null, 0, 10
        )).isInstanceOf(CustomException.class)
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
