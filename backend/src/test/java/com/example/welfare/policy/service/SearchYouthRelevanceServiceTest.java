package com.example.welfare.policy.service;

import com.example.welfare.policy.dto.SearchYouthRelevanceBackfillResponse;
import com.example.welfare.policy.entity.ServiceTag;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.repository.SearchYouthRelevanceReadRepository;
import com.example.welfare.recommend.support.RecommendationYouthRelevanceSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class SearchYouthRelevanceServiceTest {

    @Mock
    private SearchYouthRelevanceReadRepository searchYouthRelevanceReadRepository;

    @Mock
    private RecommendationYouthRelevanceSupport recommendationYouthRelevanceSupport;

    @Test
    @DisplayName("백필은 현재 규칙으로 검색용 청년 플래그를 다시 계산한다")
    void backfillAllRefreshesFlags() {
        SearchYouthRelevanceService service = new SearchYouthRelevanceService(
                searchYouthRelevanceReadRepository,
                recommendationYouthRelevanceSupport
        );

        WelfareService youth = WelfareService.builder()
                .id(1L)
                .sourceType(WelfareService.SourceType.YOUTH)
                .sourceId("Y-1")
                .title("청년 월세 지원")
                .searchYouthRelevant(false)
                .build();
        WelfareService excluded = WelfareService.builder()
                .id(2L)
                .sourceType(WelfareService.SourceType.BOKJIRO_CENTRAL)
                .sourceId("B-2")
                .title("일반 복지")
                .searchYouthRelevant(true)
                .build();

        given(searchYouthRelevanceReadRepository.findBackfillTargetServices()).willReturn(List.of(youth, excluded));
        given(searchYouthRelevanceReadRepository.findTagsByServiceIds(List.of(1L, 2L))).willReturn(List.of());
        given(recommendationYouthRelevanceSupport.isYouthRelevant(youth, List.of())).willReturn(true);
        given(recommendationYouthRelevanceSupport.isYouthRelevant(excluded, List.of())).willReturn(false);

        SearchYouthRelevanceBackfillResponse result = service.backfillAll();

        assertThat(result.processedCount()).isEqualTo(2);
        assertThat(result.updatedCount()).isEqualTo(2);
        assertThat(result.relevantCount()).isEqualTo(1);
        assertThat(result.excludedCount()).isEqualTo(1);
        assertThat(youth.isSearchYouthRelevant()).isTrue();
        assertThat(excluded.isSearchYouthRelevant()).isFalse();
    }

    @Test
    @DisplayName("단건 재계산은 전달된 태그 기준으로 검색용 청년 플래그를 갱신한다")
    void refreshForServiceUpdatesFlag() {
        SearchYouthRelevanceService service = new SearchYouthRelevanceService(
                searchYouthRelevanceReadRepository,
                recommendationYouthRelevanceSupport
        );

        WelfareService policy = WelfareService.builder()
                .id(1L)
                .sourceType(WelfareService.SourceType.YOUTH)
                .sourceId("Y-1")
                .title("청년 정책")
                .searchYouthRelevant(true)
                .build();
        List<ServiceTag> tags = List.of(ServiceTag.builder()
                .id(10L)
                .service(policy)
                .tagType(ServiceTag.TagType.KEYWORD)
                .tagValue("일반")
                .build());

        given(recommendationYouthRelevanceSupport.isYouthRelevant(policy, tags)).willReturn(false);

        service.refreshForService(policy, tags);

        assertThat(policy.isSearchYouthRelevant()).isFalse();
    }

    @Test
    @DisplayName("단건 재계산은 read 경계에서 태그를 읽어 검색용 청년 플래그를 갱신할 수 있다")
    void refreshForServiceLoadsTagsThroughReadRepository() {
        SearchYouthRelevanceService service = new SearchYouthRelevanceService(
                searchYouthRelevanceReadRepository,
                recommendationYouthRelevanceSupport
        );

        WelfareService policy = WelfareService.builder()
                .id(2L)
                .sourceType(WelfareService.SourceType.YOUTH)
                .sourceId("Y-2")
                .title("청년 정책")
                .searchYouthRelevant(false)
                .build();
        List<ServiceTag> tags = List.of(ServiceTag.builder()
                .id(11L)
                .service(policy)
                .tagType(ServiceTag.TagType.KEYWORD)
                .tagValue("청년")
                .build());

        given(searchYouthRelevanceReadRepository.findTagsByServiceId(2L)).willReturn(tags);
        given(recommendationYouthRelevanceSupport.isYouthRelevant(policy, tags)).willReturn(true);

        service.refreshForService(policy);

        assertThat(policy.isSearchYouthRelevant()).isTrue();
    }
}
