package com.example.welfare.recommend.facade;

import com.example.welfare.policy.dto.PolicySummaryResponse;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.recommend.dto.RecommendationCandidateProjection;
import com.example.welfare.recommend.entity.UserRecommendation;
import com.example.welfare.recommend.repository.RecommendationSummaryReadRepository;
import com.example.welfare.user.service.UserKeyLookupService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecommendationReadFacadeTest {

    @Mock private RecommendationSummaryReadRepository recommendationSummaryReadRepository;
    @Mock private UserKeyLookupService userKeyLookupService;

    @Test
    @DisplayName("북마크 서비스 id 조회는 nullable userKey와 최신 북마크 기준으로 반환한다")
    void findBookmarkedServiceIdsReturnsLatestBookmarks() {
        RecommendationReadFacade facade = new RecommendationReadFacade(
                recommendationSummaryReadRepository,
                userKeyLookupService
        );
        WelfareService service = WelfareService.builder()
                .id(11L)
                .sourceType(WelfareService.SourceType.YOUTH)
                .sourceId("Y-11")
                .title("청년 월세 지원")
                .build();

        when(userKeyLookupService.findNullable(1L)).thenReturn("user-key-1");
        when(recommendationSummaryReadRepository.findLatestBookmarkedServiceIds("user-key-1", List.of(11L)))
                .thenReturn(List.of(11L));

        Set<Long> bookmarkedIds = facade.findBookmarkedServiceIds(1L, List.of(service));

        assertThat(bookmarkedIds).containsExactly(11L);
    }

    @Test
    @DisplayName("추천 projection 조회는 recommendation 목록의 service id를 canonical read model에 위임한다")
    void findCandidateProjectionsDelegatesToCanonicalReadModelRepository() {
        RecommendationReadFacade facade = new RecommendationReadFacade(
                recommendationSummaryReadRepository,
                userKeyLookupService
        );
        WelfareService service = WelfareService.builder()
                .id(11L)
                .sourceType(WelfareService.SourceType.YOUTH)
                .sourceId("Y-11")
                .title("청년 월세 지원")
                .build();
        UserRecommendation recommendation = UserRecommendation.builder()
                .id(100L)
                .userKey("user-key-1")
                .service(service)
                .build();
        RecommendationCandidateProjection projection = RecommendationCandidateProjection.builder()
                .serviceId(11L)
                .unifiedCategoryCompat("주거")
                .build();

        when(recommendationSummaryReadRepository.findCandidateProjections(List.of(11L)))
                .thenReturn(Map.of(11L, projection));

        Map<Long, RecommendationCandidateProjection> result =
                facade.findCandidateProjections(List.of(recommendation));

        assertThat(result).containsEntry(11L, projection);
        verify(recommendationSummaryReadRepository).findCandidateProjections(List.of(11L));
    }

    @Test
    @DisplayName("정책 서비스 projection 조회는 정책 목록의 service id를 canonical read model에 위임한다")
    void findCandidateProjectionsByServicesDelegatesToCanonicalReadModelRepository() {
        RecommendationReadFacade facade = new RecommendationReadFacade(
                recommendationSummaryReadRepository,
                userKeyLookupService
        );
        WelfareService service = WelfareService.builder()
                .id(11L)
                .sourceType(WelfareService.SourceType.YOUTH)
                .sourceId("Y-11")
                .title("청년 월세 지원")
                .build();
        RecommendationCandidateProjection projection = RecommendationCandidateProjection.builder()
                .serviceId(11L)
                .unifiedCategoryCompat("주거")
                .build();

        when(recommendationSummaryReadRepository.findCandidateProjections(List.of(11L)))
                .thenReturn(Map.of(11L, projection));

        Map<Long, RecommendationCandidateProjection> result =
                facade.findCandidateProjectionsByServices(List.of(service));

        assertThat(result).containsEntry(11L, projection);
        verify(recommendationSummaryReadRepository).findCandidateProjections(List.of(11L));
    }

    @Test
    @DisplayName("service id projection 조회는 canonical read model 저장소에 위임한다")
    void findCandidateProjectionsByServiceIdsDelegatesToCanonicalReadModelRepository() {
        RecommendationReadFacade facade = new RecommendationReadFacade(
                recommendationSummaryReadRepository,
                userKeyLookupService
        );
        RecommendationCandidateProjection projection = RecommendationCandidateProjection.builder()
                .serviceId(11L)
                .unifiedCategoryCompat("주거")
                .build();

        when(recommendationSummaryReadRepository.findCandidateProjections(List.of(11L)))
                .thenReturn(Map.of(11L, projection));

        Map<Long, RecommendationCandidateProjection> result =
                facade.findCandidateProjectionsByServiceIds(List.of(11L));

        assertThat(result).containsEntry(11L, projection);
        verify(recommendationSummaryReadRepository).findCandidateProjections(List.of(11L));
    }

    @Test
    @DisplayName("북마크 정책 요약 조회는 recommendation read 경계 안에서 summary 응답으로 조립한다")
    void findBookmarkedPolicySummariesReturnsPolicySummaries() {
        RecommendationReadFacade facade = new RecommendationReadFacade(
                recommendationSummaryReadRepository,
                userKeyLookupService
        );
        WelfareService policy = WelfareService.builder()
                .id(11L)
                .sourceType(WelfareService.SourceType.YOUTH)
                .sourceId("Y-11")
                .title("청년 월세 지원")
                .description("월세 부담 완화")
                .unifiedCategory("HOUSING")
                .status(WelfareService.ServiceStatus.ACTIVE)
                .build();

        when(recommendationSummaryReadRepository.findLatestBookmarkedRecommendations("user-key-1"))
                .thenReturn(List.of(UserRecommendation.builder()
                        .id(100L)
                        .userKey("user-key-1")
                        .service(policy)
                        .isBookmarked(true)
                        .build()));
        when(recommendationSummaryReadRepository.findCandidateProjections(List.of(11L)))
                .thenReturn(Map.of(
                        11L,
                        RecommendationCandidateProjection.builder()
                                .serviceId(11L)
                                .unifiedCategoryCompat("주거")
                                .youthMajorLabel("주거")
                                .youthMidLabel("전월세 및 주거급여 지원")
                                .provisionMethodLabel("온라인")
                                .gov24ServiceFieldLabel("보육")
                                .gov24UserTypeLabel("청년")
                                .gov24BenefitTypeLabel("서비스")
                                .build()
                ));

        List<PolicySummaryResponse> response = facade.findBookmarkedPolicySummaries("user-key-1");

        assertThat(response).hasSize(1);
        assertThat(response.get(0).getId()).isEqualTo(11L);
        assertThat(response.get(0).getTitle()).isEqualTo("청년 월세 지원");
        assertThat(response.get(0).getUnifiedCategory()).isEqualTo("주거");
        assertThat(response.get(0).getYouthMajorLabel()).isEqualTo("주거");
        assertThat(response.get(0).getYouthMidLabel()).isEqualTo("전월세 및 주거급여 지원");
        assertThat(response.get(0).getProvisionMethodLabel()).isEqualTo("온라인");
        assertThat(response.get(0).getGov24ServiceFieldLabel()).isEqualTo("보육");
        assertThat(response.get(0).getGov24UserTypeLabel()).isEqualTo("청년");
        assertThat(response.get(0).getGov24BenefitTypeLabel()).isEqualTo("서비스");
        assertThat(response.get(0).isBookmarked()).isTrue();
    }
}
