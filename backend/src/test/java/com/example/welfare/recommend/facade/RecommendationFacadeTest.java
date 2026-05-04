package com.example.welfare.recommend.facade;

import com.example.welfare.recommend.entity.UserRecommendation;
import com.example.welfare.recommend.service.RecommendationAccessService;
import com.example.welfare.recommend.service.RecommendationBookmarkCommandService;
import com.example.welfare.recommend.service.RecommendationGenerationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RecommendationFacadeTest {

    @Mock
    private RecommendationGenerationService recommendationGenerationService;

    @Mock
    private RecommendationAccessService recommendationAccessService;

    @Mock
    private RecommendationBookmarkCommandService recommendationBookmarkCommandService;

    @InjectMocks
    private RecommendationFacade recommendationFacade;

    @Test
    @DisplayName("facade는 추천 생성 요청을 generation service로 위임한다")
    void recommendDelegatesToGenerationService() {
        List<UserRecommendation> recommendations = List.of();
        given(recommendationGenerationService.recommend(1L, true)).willReturn(recommendations);

        List<UserRecommendation> result = recommendationFacade.recommend(1L, true);

        assertThat(result).isSameAs(recommendations);
        verify(recommendationGenerationService).recommend(1L, true);
    }

    @Test
    @DisplayName("facade는 저장된 추천 조회를 access service로 위임한다")
    void getRecommendationsDelegatesToAccessService() {
        List<UserRecommendation> recommendations = List.of();
        given(recommendationAccessService.getRecommendations(1L, 5)).willReturn(recommendations);

        List<UserRecommendation> result = recommendationFacade.getRecommendations(1L, 5);

        assertThat(result).isSameAs(recommendations);
        verify(recommendationAccessService).getRecommendations(1L, 5);
    }

    @Test
    @DisplayName("facade는 북마크 토글을 command service로 위임한다")
    void toggleBookmarkDelegatesToCommandService() {
        recommendationFacade.toggleBookmark(1L, 101L);

        verify(recommendationBookmarkCommandService).toggleRecommendationBookmark(1L, 101L);
    }
}
