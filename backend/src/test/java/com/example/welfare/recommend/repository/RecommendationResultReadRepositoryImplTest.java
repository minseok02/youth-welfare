package com.example.welfare.recommend.repository;

import com.example.welfare.recommend.entity.UserRecommendation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class RecommendationResultReadRepositoryImplTest {

    @Mock
    private UserRecommendationRepository userRecommendationRepository;

    @InjectMocks
    private RecommendationResultReadRepositoryImpl recommendationResultReadRepository;

    @Test
    @DisplayName("recommendation result read repository는 최신 저장 추천 조회를 위임한다")
    void findLatestSavedRecommendationsDelegates() {
        UserRecommendation recommendation = UserRecommendation.builder().id(1L).userKey("user-key-1").build();
        given(userRecommendationRepository.findLatestByUserKeyOrderByFinalScoreDesc("user-key-1"))
                .willReturn(List.of(recommendation));

        assertThat(recommendationResultReadRepository.findLatestSavedRecommendations("user-key-1"))
                .containsExactly(recommendation);
    }

    @Test
    @DisplayName("recommendation result read repository는 상위 추천 목록 조회를 위임한다")
    void findTopRecommendationsDelegates() {
        UserRecommendation recommendation = UserRecommendation.builder().id(1L).userKey("user-key-1").build();
        given(userRecommendationRepository.findTopByUserKey(any(), any(Pageable.class)))
                .willReturn(List.of(recommendation));

        assertThat(recommendationResultReadRepository.findTopRecommendations("user-key-1", 5))
                .containsExactly(recommendation);
        then(userRecommendationRepository).should().findTopByUserKey(any(), any(Pageable.class));
    }
}
