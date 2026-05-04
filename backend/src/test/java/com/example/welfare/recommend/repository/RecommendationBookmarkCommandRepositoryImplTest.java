package com.example.welfare.recommend.repository;

import com.example.welfare.recommend.entity.UserRecommendation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class RecommendationBookmarkCommandRepositoryImplTest {

    @Mock
    private UserRecommendationRepository userRecommendationRepository;

    @InjectMocks
    private RecommendationBookmarkCommandRepositoryImpl recommendationBookmarkCommandRepository;

    @Test
    @DisplayName("bookmark command repository는 소유 추천 조회를 위임한다")
    void findOwnedRecommendationDelegates() {
        UserRecommendation recommendation = UserRecommendation.builder().id(1L).userKey("user-key-1").build();
        given(userRecommendationRepository.findByIdAndUserKey(1L, "user-key-1"))
                .willReturn(Optional.of(recommendation));

        Optional<UserRecommendation> actual =
                recommendationBookmarkCommandRepository.findOwnedRecommendation(1L, "user-key-1");

        assertThat(actual).contains(recommendation);
    }

    @Test
    @DisplayName("bookmark command repository는 최신 추천 조회를 위임한다")
    void findLatestRecommendationDelegates() {
        UserRecommendation recommendation = UserRecommendation.builder().id(2L).userKey("user-key-1").build();
        given(userRecommendationRepository.findTopByUserKeyAndServiceIdOrderByRecommendedAtDesc("user-key-1", 10L))
                .willReturn(Optional.of(recommendation));

        Optional<UserRecommendation> actual =
                recommendationBookmarkCommandRepository.findLatestRecommendation("user-key-1", 10L);

        assertThat(actual).contains(recommendation);
    }

    @Test
    @DisplayName("bookmark command repository는 북마크 수 조회를 위임한다")
    void countBookmarkedDelegates() {
        given(userRecommendationRepository.countByUserKeyAndIsBookmarkedTrue("user-key-1")).willReturn(3L);

        assertThat(recommendationBookmarkCommandRepository.countBookmarked("user-key-1")).isEqualTo(3L);
    }

    @Test
    @DisplayName("bookmark command repository는 placeholder 저장을 위임한다")
    void saveDelegates() {
        UserRecommendation recommendation = UserRecommendation.builder().userKey("user-key-1").build();

        recommendationBookmarkCommandRepository.save(recommendation);

        then(userRecommendationRepository).should().save(recommendation);
    }
}
