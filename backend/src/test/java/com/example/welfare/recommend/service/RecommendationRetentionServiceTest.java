package com.example.welfare.recommend.service;

import com.example.welfare.recommend.repository.RecommendationPersistenceCommandRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RecommendationRetentionServiceTest {

    @Mock
    private RecommendationPersistenceCommandRepository recommendationPersistenceCommandRepository;

    @InjectMocks
    private RecommendationRetentionService recommendationRetentionService;

    @Test
    @DisplayName("30일 지난 미북마크 추천을 정리한다")
    void cleanupOldUnbookmarkedRecommendations() {
        recommendationRetentionService.cleanupOldUnbookmarkedRecommendations();

        verify(recommendationPersistenceCommandRepository).deleteOldUnbookmarked(any());
    }
}
