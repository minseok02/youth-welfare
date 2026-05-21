package com.example.welfare.recommend.repository;

import com.example.welfare.recommend.entity.UserRecommendation;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public class RecommendationPersistenceCommandRepositoryImpl implements RecommendationPersistenceCommandRepository {

    private static final String DELETE_OLD_UNBOOKMARKED_SQL = """
            DELETE FROM user_recommendations
            WHERE recommended_at < :before
              AND is_bookmarked = false
            """;

    private final UserRecommendationRepository userRecommendationRepository;
    private final NamedParameterJdbcTemplate recommendationRetentionCleanupNamedParameterJdbcTemplate;

    public RecommendationPersistenceCommandRepositoryImpl(
            UserRecommendationRepository userRecommendationRepository,
            @Qualifier("recommendationRetentionCleanupNamedParameterJdbcTemplate")
            NamedParameterJdbcTemplate recommendationRetentionCleanupNamedParameterJdbcTemplate
    ) {
        this.userRecommendationRepository = userRecommendationRepository;
        this.recommendationRetentionCleanupNamedParameterJdbcTemplate = recommendationRetentionCleanupNamedParameterJdbcTemplate;
    }

    @Override
    public List<UserRecommendation> replaceAllForUser(String userKey, List<UserRecommendation> recommendations) {
        userRecommendationRepository.deleteAllByUserKey(userKey);
        return userRecommendationRepository.saveAll(recommendations);
    }

    @Override
    public void deleteOldUnbookmarked(LocalDateTime before) {
        recommendationRetentionCleanupNamedParameterJdbcTemplate.update(
                DELETE_OLD_UNBOOKMARKED_SQL,
                new MapSqlParameterSource("before", before)
        );
    }
}
