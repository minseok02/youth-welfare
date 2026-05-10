package com.example.welfare.policy.service;

import com.example.welfare.policy.dto.PolicySummaryResponse;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.repository.ServiceRegionRepository;
import com.example.welfare.recommend.dto.RecommendationCandidateProjection;
import com.example.welfare.recommend.service.RecommendationBookmarkReadService;
import com.example.welfare.recommend.service.RecommendationProjectionReadService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PolicyPresentationReadServiceTest {

    @Mock
    private RecommendationBookmarkReadService recommendationBookmarkReadService;

    @Mock
    private RecommendationProjectionReadService recommendationProjectionReadService;
    @Mock
    private ServiceRegionRepository serviceRegionRepository;

    @Test
    @DisplayName("정책 목록 페이지는 북마크와 projection additive field를 합쳐 summary 응답으로 조립한다")
    void buildSummaryPageBuildsSummaries() {
        PolicyPresentationReadService service = new PolicyPresentationReadService(
                recommendationBookmarkReadService,
                recommendationProjectionReadService,
                serviceRegionRepository
        );
        WelfareService policy = WelfareService.builder()
                .id(11L)
                .sourceType(WelfareService.SourceType.YOUTH)
                .sourceId("Y-11")
                .title("청년 월세 지원")
                .description("월세 부담 완화")
                .unifiedCategory("HOUSING")
                .status(WelfareService.ServiceStatus.ACTIVE)
                .apiViewCount(120L)
                .viewCount(3)
                .registeredAt(LocalDateTime.of(2026, 5, 1, 10, 0))
                .lastModifiedAt(LocalDateTime.of(2026, 5, 5, 9, 30))
                .build();
        ReflectionTestUtils.setField(policy, "createdAt", LocalDateTime.of(2026, 4, 30, 8, 0));
        Page<WelfareService> page = new PageImpl<>(List.of(policy));

        when(recommendationBookmarkReadService.findBookmarkedServiceIds(7L, List.of(policy)))
                .thenReturn(Set.of(11L));
        when(recommendationProjectionReadService.findCandidateProjectionsByServices(List.of(policy)))
                .thenReturn(Map.of(
                        11L,
                        RecommendationCandidateProjection.builder()
                                .serviceId(11L)
                                .unifiedCategoryCompat("주거")
                                .youthMajorLabel("주거")
                                .build()
                ));
        when(serviceRegionRepository.findFirstSidoByServiceIds(List.of(11L))).thenReturn(List.of());

        Page<PolicySummaryResponse> result = service.buildSummaryPage(7L, page);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).isBookmarked()).isTrue();
        assertThat(result.getContent().get(0).getUnifiedCategory()).isEqualTo("주거");
        assertThat(result.getContent().get(0).getYouthMajorLabel()).isEqualTo("주거");
        assertThat(result.getContent().get(0).getApiViewCount()).isEqualTo(120L);
        assertThat(result.getContent().get(0).getViewCount()).isEqualTo(3);
        assertThat(result.getContent().get(0).getCreatedAt()).isEqualTo(LocalDateTime.of(2026, 4, 30, 8, 0));
        assertThat(result.getContent().get(0).getRegisteredAt()).isEqualTo(LocalDateTime.of(2026, 5, 1, 10, 0));
        assertThat(result.getContent().get(0).getLastModifiedAt()).isEqualTo(LocalDateTime.of(2026, 5, 5, 9, 30));
    }

    @Test
    @DisplayName("정책 상세 presentation은 북마크 상태와 projection을 함께 반환한다")
    void buildDetailPresentationBuildsDetailPresentation() {
        PolicyPresentationReadService service = new PolicyPresentationReadService(
                recommendationBookmarkReadService,
                recommendationProjectionReadService,
                serviceRegionRepository
        );
        WelfareService policy = WelfareService.builder()
                .id(11L)
                .sourceType(WelfareService.SourceType.YOUTH)
                .sourceId("Y-11")
                .title("청년 월세 지원")
                .status(WelfareService.ServiceStatus.ACTIVE)
                .build();
        RecommendationCandidateProjection projection = RecommendationCandidateProjection.builder()
                .serviceId(11L)
                .unifiedCategoryCompat("주거")
                .build();

        when(recommendationBookmarkReadService.findBookmarkedServiceIds(7L, List.of(policy)))
                .thenReturn(Set.of(11L));
        when(recommendationProjectionReadService.findCandidateProjectionsByServices(List.of(policy)))
                .thenReturn(Map.of(11L, projection));

        PolicyPresentationReadService.PolicyDetailPresentation result =
                service.buildDetailPresentation(7L, policy);

        assertThat(result.bookmarked()).isTrue();
        assertThat(result.projection()).isEqualTo(projection);
    }
}
