package com.example.welfare.recommend.repository;

import com.example.welfare.recommend.entity.UserRecommendation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class RecommendationPersistenceCommandRepositoryImplTest {

    @Mock
    private UserRecommendationRepository userRecommendationRepository;

    @InjectMocks
    private RecommendationPersistenceCommandRepositoryImpl recommendationPersistenceCommandRepository;

    @Test
    @DisplayName("recommendation persistence command repository는 추천 전체 교체 저장을 수행한다")
    void replaceAllForUserDelegates() {
        UserRecommendation recommendation = UserRecommendation.builder().userKey("user-key-1").build();

        recommendationPersistenceCommandRepository.replaceAllForUser("user-key-1", List.of(recommendation));

        then(userRecommendationRepository).should().deleteAllByUserKey("user-key-1");
        then(userRecommendationRepository).should().saveAll(List.of(recommendation));
    }

    @Test
    @DisplayName("recommendation persistence command repository는 retention 삭제를 위임한다")
    void deleteOldUnbookmarkedDelegates() {
        LocalDateTime before = LocalDateTime.now().minusDays(30);

        recommendationPersistenceCommandRepository.deleteOldUnbookmarked(before);

        then(userRecommendationRepository).should().deleteOldUnbookmarked(before);
    }
}
