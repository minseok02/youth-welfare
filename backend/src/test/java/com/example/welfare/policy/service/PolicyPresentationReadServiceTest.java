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

import java.time.LocalDate;
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
        LocalDate applyStartDate = LocalDate.now().minusDays(10);
        LocalDate applyEndDate = LocalDate.now().plusDays(30);
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
                .unifiedCategory("HOUSING")
                .hostOrg("서울시")
                .operatingOrg("서울청년센터")
                .status(WelfareService.ServiceStatus.ACTIVE)
                .applyStartDate(applyStartDate)
                .applyEndDate(applyEndDate)
                .apiViewCount(120L)
                .viewCount(3)
                .registeredAt(LocalDateTime.of(2026, 5, 1, 10, 0))
                .lastModifiedAt(LocalDateTime.of(2026, 5, 5, 9, 30))
                .build();
        ReflectionTestUtils.setField(policy, "createdAt", LocalDateTime.of(2026, 4, 30, 8, 0));
        Page<WelfareService> page = new PageImpl<>(List.of(policy));

        when(recommendationBookmarkReadService.findBookmarkedServiceIds(7L, List.of(policy)))
                .thenReturn(Set.of(11L));
        when(recommendationProjectionReadService.findSummaryProjectionsByServices(List.of(policy)))
                .thenReturn(Map.of(
                        11L,
                        RecommendationCandidateProjection.builder()
                                .serviceId(11L)
                                .unifiedCategoryCompat("주거")
                                .summary("월세 부담을 낮추는 상세 지원 안내")
                                .youthMajorLabel("주거")
                                .build()
                ));
        when(serviceRegionRepository.findRegionLabelCandidatesByServiceIds(List.of(11L)))
                .thenReturn(List.<Object[]>of(new Object[]{11L, "서울특별시 강남구", null}));

        Page<PolicySummaryResponse> result = service.buildSummaryPage(7L, page);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).isBookmarked()).isTrue();
        assertThat(result.getContent().get(0).getDescription()).isEqualTo("월세 부담을 낮추는 상세 지원 안내");
        assertThat(result.getContent().get(0).getUnifiedCategory()).isEqualTo("주거");
        assertThat(result.getContent().get(0).getOperatingOrg()).isEqualTo("서울청년센터");
        assertThat(result.getContent().get(0).getProviderName()).isEqualTo("서울시");
        assertThat(result.getContent().get(0).getRegionLabel()).isEqualTo("서울특별시 강남구");
        assertThat(result.getContent().get(0).getSido()).isEqualTo("서울특별시");
        assertThat(result.getContent().get(0).getApplicationPeriod()).isEqualTo(applyStartDate + " ~ " + applyEndDate);
        assertThat(result.getContent().get(0).getStatusLabel()).isEqualTo("진행중");
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

    @Test
    @DisplayName("summary region label은 기관 힌트와 맞는 지역을 우선하고 Gov24 stray row는 억제한다")
    void buildSummaryPagePrefersOrganizationRegionHint() {
        PolicyPresentationReadService service = new PolicyPresentationReadService(
                recommendationBookmarkReadService,
                recommendationProjectionReadService,
                serviceRegionRepository
        );
        WelfareService localGov24 = WelfareService.builder()
                .id(9344L)
                .sourceType(WelfareService.SourceType.GOV24)
                .title("청년 월세 지원")
                .hostOrg("충청북도 옥천군")
                .operatingOrg("성장정책과")
                .status(WelfareService.ServiceStatus.ACTIVE)
                .build();
        WelfareService centralGov24 = WelfareService.builder()
                .id(6355L)
                .sourceType(WelfareService.SourceType.GOV24)
                .title("청년주택드림 청약통장")
                .hostOrg("국토교통부")
                .operatingOrg("주택기금과")
                .status(WelfareService.ServiceStatus.ACTIVE)
                .build();

        when(recommendationBookmarkReadService.findBookmarkedServiceIds(null, List.of(localGov24, centralGov24)))
                .thenReturn(Set.of());
        when(recommendationProjectionReadService.findSummaryProjectionsByServices(List.of(localGov24, centralGov24)))
                .thenReturn(Map.of());
        when(serviceRegionRepository.findRegionLabelCandidatesByServiceIds(List.of(9344L, 6355L)))
                .thenReturn(List.of(
                        new Object[]{9344L, "전북특별자치도 무주군", null},
                        new Object[]{9344L, "충청북도 옥천군", null},
                        new Object[]{6355L, "전북특별자치도 무주군", null}
                ));

        List<PolicySummaryResponse> result = service.buildSummaryResponses(null, List.of(localGov24, centralGov24));

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getRegionLabel()).isEqualTo("충청북도 옥천군");
        assertThat(result.get(0).getSido()).isEqualTo("충청북도");
        assertThat(result.get(1).getRegionLabel()).isNull();
        assertThat(result.get(1).getSido()).isNull();
    }

    @Test
    @DisplayName("summary region label은 code-only local row를 행정구역 코드에서 복구한다")
    void buildSummaryPageResolvesCodeOnlyLocalRegion() {
        PolicyPresentationReadService service = new PolicyPresentationReadService(
                recommendationBookmarkReadService,
                recommendationProjectionReadService,
                serviceRegionRepository
        );
        WelfareService localGov24 = WelfareService.builder()
                .id(196L)
                .sourceType(WelfareService.SourceType.GOV24)
                .title("청년구직자 면접비 지원사업")
                .hostOrg("경제과")
                .operatingOrg(null)
                .status(WelfareService.ServiceStatus.ACTIVE)
                .build();

        when(recommendationBookmarkReadService.findBookmarkedServiceIds(null, List.of(localGov24)))
                .thenReturn(Set.of());
        when(recommendationProjectionReadService.findSummaryProjectionsByServices(List.of(localGov24)))
                .thenReturn(Map.of());
        when(serviceRegionRepository.findRegionLabelCandidatesByServiceIds(List.of(196L)))
                .thenReturn(List.<Object[]>of(new Object[]{196L, null, "44150"}));

        List<PolicySummaryResponse> result = service.buildSummaryResponses(null, List.of(localGov24));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getRegionLabel()).isEqualTo("충청남도 공주시");
        assertThat(result.get(0).getSido()).isEqualTo("충청남도");
    }
}
