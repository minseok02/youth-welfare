package com.example.welfare.recommend.repository;

import com.example.welfare.recommend.entity.UserRecommendation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class RecommendationPersistenceCommandRepositoryImplTest {

    @Mock
    private UserRecommendationRepository userRecommendationRepository;

    @Mock
    private NamedParameterJdbcTemplate recommendationRetentionCleanupNamedParameterJdbcTemplate;

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
    @DisplayName("recommendation persistence command repository는 retention 삭제를 cleanup jdbc로 위임한다")
    void deleteOldUnbookmarkedDelegates() {
        LocalDateTime before = LocalDateTime.now().minusDays(30);

        recommendationPersistenceCommandRepository.deleteOldUnbookmarked(before);

        ArgumentCaptor<SqlParameterSource> parameterCaptor = ArgumentCaptor.forClass(SqlParameterSource.class);

        then(recommendationRetentionCleanupNamedParameterJdbcTemplate).should().update(
                eq("""
                        DELETE FROM user_recommendations
                        WHERE recommended_at < :before
                          AND is_bookmarked = false
                        """),
                parameterCaptor.capture()
        );
        assertThat(parameterCaptor.getValue().getValue("before")).isEqualTo(before);
        then(userRecommendationRepository).shouldHaveNoMoreInteractions();
    }
}
