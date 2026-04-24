package com.example.welfare.policy.service;

import com.example.welfare.policy.dto.PolicySearchResponse;
import com.example.welfare.policy.entity.ServiceTag;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.repository.ServiceTagRepository;
import com.example.welfare.policy.repository.WelfareServiceRepository;
import com.example.welfare.recommend.repository.UserRecommendationRepository;
import com.example.welfare.recommend.service.YouthPolicyFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PolicySearchServiceTest {

    @Mock
    private WelfareServiceRepository welfareServiceRepository;

    @Mock
    private ServiceTagRepository serviceTagRepository;

    @Mock
    private UserRecommendationRepository userRecommendationRepository;

    @Mock
    private YouthPolicyFilter youthPolicyFilter;

    @Test
    @DisplayName("검색 결과에서 청년 관련 정책만 남긴다")
    void searchFiltersNonYouthPolicies() {
        PolicySearchService service = new PolicySearchService(
                welfareServiceRepository,
                serviceTagRepository,
                userRecommendationRepository,
                youthPolicyFilter
        );

        WelfareService youthService = welfareService(1L, "청년 정책");
        WelfareService genericService = welfareService(2L, "일반 복지");

        when(welfareServiceRepository.searchByKeywordWithFilters(
                anyString(),
                isNull(),
                eq(0),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                anyString(),
                anyInt(),
                anyInt()
        )).thenReturn(List.of(youthService, genericService), List.of());
        when(serviceTagRepository.findByServiceIdIn(List.of(1L, 2L))).thenReturn(List.of());
        when(youthPolicyFilter.isYouthRelevant(youthService, List.of())).thenReturn(true);
        when(youthPolicyFilter.isYouthRelevant(genericService, List.of())).thenReturn(false);

        PolicySearchResponse results = service.search(null, "청년", null, null, null, null, null, null, null, null, 0, 10);

        assertThat(results.getContent()).hasSize(1);
        assertThat(results.getTotalElements()).isEqualTo(1);
        assertThat(results.getTotalPages()).isEqualTo(1);
        assertThat(results.isHasNext()).isFalse();
    }

    private WelfareService welfareService(Long id, String title) {
        return WelfareService.builder()
                .id(id)
                .sourceType(WelfareService.SourceType.BOKJIRO_CENTRAL)
                .sourceId("S" + id)
                .title(title)
                .status(WelfareService.ServiceStatus.ACTIVE)
                .build();
    }
}
