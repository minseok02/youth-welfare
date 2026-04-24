package com.example.welfare.policy.service;

import com.example.welfare.policy.dto.PolicySearchResponse;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.repository.WelfareServiceRepository;
import com.example.welfare.recommend.repository.UserRecommendationRepository;
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
    private UserRecommendationRepository userRecommendationRepository;

    @Test
    @DisplayName("검색은 SQL 레벨 청년 플래그 필터 결과를 페이지 메타데이터와 함께 반환한다")
    void searchReturnsPagedResponse() {
        PolicySearchService service = new PolicySearchService(
                welfareServiceRepository,
                userRecommendationRepository
        );

        WelfareService youthService = welfareService(1L, "청년 정책");
        given(welfareServiceRepository.searchByKeywordWithFilters(
                eq("+청년"),
                isNull(),
                eq(0),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                eq("RELEVANCE"),
                any(PageRequest.class)
        )).willReturn(new PageImpl<>(List.of(youthService), PageRequest.of(0, 10), 21));

        PolicySearchResponse results = service.search(null, "청년", null, null, null, null, null, null, null, null, 0, 10);

        assertThat(results.getContent()).hasSize(1);
        assertThat(results.getTotalElements()).isEqualTo(21);
        assertThat(results.getTotalPages()).isEqualTo(3);
        assertThat(results.isHasNext()).isTrue();

        ArgumentCaptor<PageRequest> captor = ArgumentCaptor.forClass(PageRequest.class);
        verify(welfareServiceRepository).searchByKeywordWithFilters(
                eq("+청년"),
                isNull(),
                eq(0),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                eq("RELEVANCE"),
                captor.capture()
        );
        assertThat(captor.getValue().getPageNumber()).isEqualTo(0);
        assertThat(captor.getValue().getPageSize()).isEqualTo(10);
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
