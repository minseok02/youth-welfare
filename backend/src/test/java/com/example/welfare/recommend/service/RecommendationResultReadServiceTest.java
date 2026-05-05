package com.example.welfare.recommend.service;

import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.recommend.entity.UserRecommendation;
import com.example.welfare.recommend.repository.RecommendationResultReadRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecommendationResultReadServiceTest {

    @Mock
    private RecommendationResultReadRepository recommendationResultReadRepository;

    private RecommendationResultReadService recommendationResultReadService;

    @BeforeEach
    void setUp() {
        recommendationResultReadService = new RecommendationResultReadService(recommendationResultReadRepository);
    }

    @Test
    @DisplayName("latest saved recommendations 조회를 read repository에 위임한다")
    void findLatestSavedRecommendationsDelegatesToRepository() {
        UserRecommendation recommendation = sampleRecommendation(101L);
        when(recommendationResultReadRepository.findLatestSavedRecommendations("user-key-1"))
                .thenReturn(List.of(recommendation));

        List<UserRecommendation> result =
                recommendationResultReadService.findLatestSavedRecommendations("user-key-1");

        assertThat(result).containsExactly(recommendation);
        verify(recommendationResultReadRepository).findLatestSavedRecommendations("user-key-1");
    }

    @Test
    @DisplayName("특정 recommendation batch 조회를 read repository에 위임한다")
    void findSavedRecommendationsForBatchDelegatesToRepository() {
        UserRecommendation recommendation = sampleRecommendation(303L);
        LocalDateTime recommendedAt = LocalDateTime.of(2026, 5, 4, 12, 0);
        when(recommendationResultReadRepository.findSavedRecommendationsForBatch("user-key-1", recommendedAt))
                .thenReturn(List.of(recommendation));

        List<UserRecommendation> result =
                recommendationResultReadService.findSavedRecommendationsForBatch("user-key-1", recommendedAt);

        assertThat(result).containsExactly(recommendation);
        verify(recommendationResultReadRepository).findSavedRecommendationsForBatch("user-key-1", recommendedAt);
    }

    @Test
    @DisplayName("top recommendations 조회를 read repository에 위임한다")
    void findTopRecommendationsDelegatesToRepository() {
        UserRecommendation recommendation = sampleRecommendation(202L);
        when(recommendationResultReadRepository.findTopRecommendations("user-key-1", 5))
                .thenReturn(List.of(recommendation));

        List<UserRecommendation> result =
                recommendationResultReadService.findTopRecommendations("user-key-1", 5);

        assertThat(result).containsExactly(recommendation);
        verify(recommendationResultReadRepository).findTopRecommendations("user-key-1", 5);
    }

    private UserRecommendation sampleRecommendation(Long id) {
        WelfareService service = WelfareService.builder()
                .id(11L)
                .sourceType(WelfareService.SourceType.YOUTH)
                .sourceId("Y11")
                .title("청년 월세 지원")
                .status(WelfareService.ServiceStatus.ACTIVE)
                .build();
        return UserRecommendation.builder()
                .id(id)
                .userKey("user-key-1")
                .service(service)
                .recommendedAt(LocalDateTime.of(2026, 5, 4, 12, 0))
                .build();
    }
}
