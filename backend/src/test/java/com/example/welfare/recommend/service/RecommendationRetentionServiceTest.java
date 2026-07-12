package com.example.welfare.recommend.service;

import com.example.welfare.global.service.AppSchedulerGate;
import org.junit.jupiter.api.BeforeEach;
import com.example.welfare.recommend.repository.RecommendationPersistenceCommandRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecommendationRetentionServiceTest {

    @Mock
    private RecommendationPersistenceCommandRepository recommendationPersistenceCommandRepository;

    @Mock
    private AppSchedulerGate appSchedulerGate;

    @InjectMocks
    private RecommendationRetentionService recommendationRetentionService;

    @BeforeEach
    void setUpSchedulerGate() {
        lenient().when(appSchedulerGate.shouldRun("RecommendationRetentionService.cleanupOldUnbookmarkedRecommendations"))
                .thenReturn(true);
    }

    @Test
    @DisplayName("30일 지난 미북마크 추천을 정리한다")
    void cleanupOldUnbookmarkedRecommendations() {
        recommendationRetentionService.cleanupOldUnbookmarkedRecommendations();

        verify(recommendationPersistenceCommandRepository).deleteOldUnbookmarked(any());
    }

    @Test
    @DisplayName("scheduler가 비활성화된 노드에서는 추천 retention 정리를 실행하지 않는다")
    void cleanupOldUnbookmarkedRecommendationsSkipsWhenSchedulerDisabled() {
        when(appSchedulerGate.shouldRun("RecommendationRetentionService.cleanupOldUnbookmarkedRecommendations"))
                .thenReturn(false);

        recommendationRetentionService.cleanupOldUnbookmarkedRecommendations();

        verify(recommendationPersistenceCommandRepository, never()).deleteOldUnbookmarked(any());
    }
}
