package com.example.welfare.recommend.repository;

import com.example.welfare.recommend.entity.UserRecommendation;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public class RecommendationPersistenceCommandRepositoryImpl implements RecommendationPersistenceCommandRepository {

    private static final String DELETE_ALL_FOR_USER_SQL = """
            DELETE FROM user_recommendations
            WHERE user_key = :userKey
            """;

    private static final String INSERT_RECOMMENDATION_SQL = """
            INSERT INTO user_recommendations (
                user_key,
                service_id,
                recommended_at,
                rule_base_score,
                rule_weighted_score,
                ai_score,
                ai_reason,
                ai_status,
                rule_weight_used,
                ai_weight_used,
                final_score,
                is_bookmarked
            ) VALUES (
                :userKey,
                :serviceId,
                :recommendedAt,
                :ruleBaseScore,
                :ruleWeightedScore,
                :aiScore,
                :aiReason,
                :aiStatus,
                :ruleWeightUsed,
                :aiWeightUsed,
                :finalScore,
                :isBookmarked
            )
            """;

    private static final String DELETE_OLD_UNBOOKMARKED_SQL = """
            DELETE FROM user_recommendations
            WHERE recommended_at < :before
              AND is_bookmarked = false
            """;

    private final NamedParameterJdbcTemplate recommendationPersistenceCommandNamedParameterJdbcTemplate;
    private final NamedParameterJdbcTemplate recommendationRetentionCleanupNamedParameterJdbcTemplate;

    public RecommendationPersistenceCommandRepositoryImpl(
            @Qualifier("recommendationPersistenceCommandNamedParameterJdbcTemplate")
            NamedParameterJdbcTemplate recommendationPersistenceCommandNamedParameterJdbcTemplate,
            @Qualifier("recommendationRetentionCleanupNamedParameterJdbcTemplate")
            NamedParameterJdbcTemplate recommendationRetentionCleanupNamedParameterJdbcTemplate
    ) {
        this.recommendationPersistenceCommandNamedParameterJdbcTemplate = recommendationPersistenceCommandNamedParameterJdbcTemplate;
        this.recommendationRetentionCleanupNamedParameterJdbcTemplate = recommendationRetentionCleanupNamedParameterJdbcTemplate;
    }

    @Override
    @Transactional(transactionManager = "recommendationPersistenceCommandTransactionManager")
    public List<UserRecommendation> replaceAllForUser(String userKey, List<UserRecommendation> recommendations) {
        recommendationPersistenceCommandNamedParameterJdbcTemplate.update(
                DELETE_ALL_FOR_USER_SQL,
                new MapSqlParameterSource("userKey", userKey)
        );
        if (recommendations == null || recommendations.isEmpty()) {
            return List.of();
        }
        recommendationPersistenceCommandNamedParameterJdbcTemplate.batchUpdate(
                INSERT_RECOMMENDATION_SQL,
                recommendations.stream()
                        .map(this::toInsertParameterSource)
                        .toArray(SqlParameterSource[]::new)
        );
        return recommendations;
    }

    @Override
    public void deleteOldUnbookmarked(LocalDateTime before) {
        recommendationRetentionCleanupNamedParameterJdbcTemplate.update(
                DELETE_OLD_UNBOOKMARKED_SQL,
                new MapSqlParameterSource("before", before)
        );
    }

    private SqlParameterSource toInsertParameterSource(UserRecommendation recommendation) {
        return new MapSqlParameterSource()
                .addValue("userKey", recommendation.getUserKey())
                .addValue("serviceId", recommendation.getService().getId())
                .addValue("recommendedAt", recommendation.getRecommendedAt())
                .addValue("ruleBaseScore", recommendation.getRuleBaseScore())
                .addValue("ruleWeightedScore", recommendation.getRuleWeightedScore())
                .addValue("aiScore", recommendation.getAiScore())
                .addValue("aiReason", recommendation.getAiReason())
                .addValue("aiStatus", recommendation.getAiStatus().name())
                .addValue("ruleWeightUsed", recommendation.getRuleWeightUsed())
                .addValue("aiWeightUsed", recommendation.getAiWeightUsed())
                .addValue("finalScore", recommendation.getFinalScore())
                .addValue("isBookmarked", recommendation.isBookmarked());
    }
}
