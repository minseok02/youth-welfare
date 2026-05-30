package com.example.welfare.recommend.repository;

import com.example.welfare.recommend.entity.UserRecommendation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class RecommendationPersistenceCommandRepositoryImplTest {

    @Mock
    private NamedParameterJdbcTemplate recommendationPersistenceCommandNamedParameterJdbcTemplate;

    @Mock
    private NamedParameterJdbcTemplate recommendationRetentionCleanupNamedParameterJdbcTemplate;

    private RecommendationPersistenceCommandRepositoryImpl recommendationPersistenceCommandRepository;

    @BeforeEach
    void setUp() {
        recommendationPersistenceCommandRepository = new RecommendationPersistenceCommandRepositoryImpl(
                recommendationPersistenceCommandNamedParameterJdbcTemplate,
                recommendationRetentionCleanupNamedParameterJdbcTemplate
        );
    }

    @Test
    @DisplayName("recommendation persistence command repository는 추천 전체 교체 저장을 수행한다")
    void replaceAllForUserDelegates() {
        com.example.welfare.policy.entity.WelfareService service = com.example.welfare.policy.entity.WelfareService.builder()
                .id(11L)
                .build();
        UserRecommendation recommendation = UserRecommendation.builder()
                .userKey("user-key-1")
                .service(service)
                .recommendedAt(LocalDateTime.of(2026, 5, 30, 12, 0))
                .ruleBaseScore(BigDecimal.valueOf(10.0))
                .ruleWeightedScore(BigDecimal.valueOf(12.0))
                .aiScore(BigDecimal.valueOf(88.0))
                .aiReason("reason")
                .aiStatus(com.example.welfare.recommend.entity.AiScoreStatus.SCORED)
                .ruleWeightUsed(BigDecimal.valueOf(0.7))
                .aiWeightUsed(BigDecimal.valueOf(0.3))
                .finalScore(BigDecimal.valueOf(0.7321))
                .isBookmarked(true)
                .build();

        List<UserRecommendation> saved = recommendationPersistenceCommandRepository.replaceAllForUser("user-key-1", List.of(recommendation));

        ArgumentCaptor<String> deleteSqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<SqlParameterSource> deleteCaptor = ArgumentCaptor.forClass(SqlParameterSource.class);
        then(recommendationPersistenceCommandNamedParameterJdbcTemplate).should().update(
                deleteSqlCaptor.capture(),
                deleteCaptor.capture()
        );
        assertThat(deleteSqlCaptor.getValue()).contains("DELETE FROM user_recommendations");
        assertThat(deleteSqlCaptor.getValue()).contains("WHERE user_key = :userKey");
        assertThat(deleteCaptor.getValue().getValue("userKey")).isEqualTo("user-key-1");
        ArgumentCaptor<String> insertSqlCaptor = ArgumentCaptor.forClass(String.class);
        then(recommendationPersistenceCommandNamedParameterJdbcTemplate).should().batchUpdate(
                insertSqlCaptor.capture(),
                org.mockito.ArgumentMatchers.any(SqlParameterSource[].class)
        );
        assertThat(insertSqlCaptor.getValue()).contains("INSERT INTO user_recommendations");
        assertThat(insertSqlCaptor.getValue()).contains(":serviceId");
        assertThat(saved).containsExactly(recommendation);
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
        then(recommendationPersistenceCommandNamedParameterJdbcTemplate).shouldHaveNoMoreInteractions();
    }
}
