package com.example.welfare.policy.service;

import com.example.welfare.policy.dto.SearchYouthRelevanceBackfillResponse;
import com.example.welfare.policy.entity.ServiceTag;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.repository.ServiceTagRepository;
import com.example.welfare.policy.repository.WelfareServiceRepository;
import com.example.welfare.recommend.service.YouthPolicyFilter;
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
    private WelfareServiceRepository welfareServiceRepository;

    @Mock
    private ServiceTagRepository serviceTagRepository;

    @Mock
    private YouthPolicyFilter youthPolicyFilter;

    @Test
    @DisplayName("백필은 현재 규칙으로 검색용 청년 플래그를 다시 계산한다")
    void backfillAllRefreshesFlags() {
        SearchYouthRelevanceService service = new SearchYouthRelevanceService(
                welfareServiceRepository,
                serviceTagRepository,
                youthPolicyFilter
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

        given(welfareServiceRepository.findAll()).willReturn(List.of(youth, excluded));
        given(serviceTagRepository.findByServiceIdIn(List.of(1L, 2L))).willReturn(List.of());
        given(youthPolicyFilter.isYouthRelevant(youth, List.of())).willReturn(true);
        given(youthPolicyFilter.isYouthRelevant(excluded, List.of())).willReturn(false);

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
                welfareServiceRepository,
                serviceTagRepository,
                youthPolicyFilter
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

        given(youthPolicyFilter.isYouthRelevant(policy, tags)).willReturn(false);

        service.refreshForService(policy, tags);

        assertThat(policy.isSearchYouthRelevant()).isFalse();
    }
}
