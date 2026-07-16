package com.example.welfare.recommend.service;

import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.recommend.dto.RecommendationCandidateProjection;
import com.example.welfare.recommend.entity.UserRecommendation;
import com.example.welfare.recommend.repository.RecommendationSummaryReadRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecommendationProjectionReadServiceTest {

    @Mock
    private RecommendationSummaryReadRepository recommendationSummaryReadRepository;

    @Test
    @DisplayName("추천 projection 조회는 recommendation 목록의 service id를 canonical read model에 위임한다")
    void findCandidateProjectionsDelegatesToRepository() {
        RecommendationProjectionReadService service =
                new RecommendationProjectionReadService(recommendationSummaryReadRepository);
        WelfareService welfareService = WelfareService.builder()
                .id(11L)
                .sourceType(WelfareService.SourceType.YOUTH)
                .sourceId("Y-11")
                .title("청년 월세 지원")
                .build();
        UserRecommendation recommendation = UserRecommendation.builder()
                .id(100L)
                .userKey("user-key-1")
                .service(welfareService)
                .build();
        RecommendationCandidateProjection projection = RecommendationCandidateProjection.builder()
                .serviceId(11L)
                .unifiedCategoryCompat("주거")
                .build();

        when(recommendationSummaryReadRepository.findCandidateProjections(List.of(11L)))
                .thenReturn(Map.of(11L, projection));

        Map<Long, RecommendationCandidateProjection> result =
                service.findCandidateProjections(List.of(recommendation));

        assertThat(result).containsEntry(11L, projection);
        verify(recommendationSummaryReadRepository).findCandidateProjections(List.of(11L));
    }

    @Test
    @DisplayName("정책 서비스 projection 조회는 정책 목록의 service id를 canonical read model에 위임한다")
    void findCandidateProjectionsByServicesDelegatesToRepository() {
        RecommendationProjectionReadService service =
                new RecommendationProjectionReadService(recommendationSummaryReadRepository);
        WelfareService welfareService = WelfareService.builder()
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
                service.findCandidateProjectionsByServices(List.of(welfareService));

        assertThat(result).containsEntry(11L, projection);
        verify(recommendationSummaryReadRepository).findCandidateProjections(List.of(11L));
    }

    @Test
    @DisplayName("정책 summary projection 조회는 정책 목록의 service id를 summary read model에 위임한다")
    void findSummaryProjectionsByServicesDelegatesToRepository() {
        RecommendationProjectionReadService service =
                new RecommendationProjectionReadService(recommendationSummaryReadRepository);
        WelfareService welfareService = WelfareService.builder()
                .id(11L)
                .sourceType(WelfareService.SourceType.YOUTH)
                .sourceId("Y-11")
                .title("청년 월세 지원")
                .build();
        RecommendationCandidateProjection projection = RecommendationCandidateProjection.builder()
                .serviceId(11L)
                .unifiedCategoryCompat("주거")
                .build();

        when(recommendationSummaryReadRepository.findSummaryProjections(List.of(11L)))
                .thenReturn(Map.of(11L, projection));

        Map<Long, RecommendationCandidateProjection> result =
                service.findSummaryProjectionsByServices(List.of(welfareService));

        assertThat(result).containsEntry(11L, projection);
        verify(recommendationSummaryReadRepository).findSummaryProjections(List.of(11L));
    }

    @Test
    @DisplayName("service id projection 조회는 canonical read model 저장소에 위임한다")
    void findCandidateProjectionsByServiceIdsDelegatesToRepository() {
        RecommendationProjectionReadService service =
                new RecommendationProjectionReadService(recommendationSummaryReadRepository);
        RecommendationCandidateProjection projection = RecommendationCandidateProjection.builder()
                .serviceId(11L)
                .unifiedCategoryCompat("주거")
                .build();

        when(recommendationSummaryReadRepository.findCandidateProjections(List.of(11L)))
                .thenReturn(Map.of(11L, projection));

        Map<Long, RecommendationCandidateProjection> result =
                service.findCandidateProjectionsByServiceIds(List.of(11L));

        assertThat(result).containsEntry(11L, projection);
        verify(recommendationSummaryReadRepository).findCandidateProjections(List.of(11L));
    }
}
