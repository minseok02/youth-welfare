package com.example.welfare.recommend.repository;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class SimilarUsersViewedPolicyReadRepositoryImpl implements SimilarUsersViewedPolicyReadRepository {

    private static final String NONE_SENTINEL = "__none__";

    private final NamedParameterJdbcTemplate primaryNamedParameterJdbcTemplate;

    public SimilarUsersViewedPolicyReadRepositoryImpl(
            @Qualifier("primaryNamedParameterJdbcTemplate")
            NamedParameterJdbcTemplate primaryNamedParameterJdbcTemplate
    ) {
        this.primaryNamedParameterJdbcTemplate = primaryNamedParameterJdbcTemplate;
    }

    @Override
    public List<SimilarUsersViewedPolicyCandidate> findCandidates(SimilarUsersViewedPolicyQuery query) {
        return primaryNamedParameterJdbcTemplate.query(sql(), params(query), (rs, rowNum) ->
                new SimilarUsersViewedPolicyCandidate(
                        rs.getLong("service_id"),
                        rs.getInt("similar_user_count"),
                        rs.getInt("recent_view_count")
                )
        );
    }

    private String sql() {
        return """
                WITH similar_users AS (
                    SELECT up.user_key,
                           (
                               CASE WHEN :regionCodePresent AND up.region_code = :regionCode THEN 4.0 ELSE 0.0 END
                             + CASE WHEN :sidoPresent AND up.sido = :sido THEN 2.0 ELSE 0.0 END
                             + CASE WHEN :ageBandPresent AND up.age_band = :ageBand THEN 2.0 ELSE 0.0 END
                             + CASE WHEN :incomeLevelPresent AND up.income_level BETWEEN :incomeMin AND :incomeMax THEN 1.0 ELSE 0.0 END
                             + CASE WHEN :interestFieldsPresent AND EXISTS (
                                   SELECT 1
                                   FROM user_attributes ua
                                   WHERE ua.user_key = up.user_key
                                     AND ua.attr_type = 'INTEREST_FIELD'
                                     AND ua.attr_value IN (:interestFields)
                               ) THEN 1.5 ELSE 0.0 END
                             + CASE WHEN :targetTypesPresent AND EXISTS (
                                   SELECT 1
                                   FROM user_attributes ua
                                   WHERE ua.user_key = up.user_key
                                     AND ua.attr_type = 'TARGET_TYPE'
                                     AND ua.attr_value IN (:targetTypes)
                               ) THEN 1.0 ELSE 0.0 END
                             + CASE WHEN :priorityCodesPresent AND EXISTS (
                                   SELECT 1
                                   FROM user_priorities upr
                                   JOIN priority_options po ON po.id = upr.priority_option_id
                                   WHERE upr.user_key = up.user_key
                                     AND po.code IN (:priorityCodes)
                               ) THEN 1.5 ELSE 0.0 END
                           ) AS similarity_score
                    FROM user_profiles up
                    JOIN users u ON u.user_key = up.user_key
                    WHERE up.user_key <> :userKey
                      AND u.is_active = true
                      AND u.account_origin = 'REAL_USER'
                ),
                eligible_similar_users AS (
                    SELECT user_key, similarity_score
                    FROM similar_users
                    WHERE similarity_score >= :minSimilarityScore
                )
                SELECT rpv.service_id,
                       COUNT(DISTINCT esu.user_key) AS similar_user_count,
                       COUNT(*) AS recent_view_count
                FROM recent_policy_views rpv
                JOIN eligible_similar_users esu ON esu.user_key = rpv.user_key
                JOIN welfare_services ws ON ws.id = rpv.service_id
                WHERE rpv.last_viewed_at >= :viewedSince
                  AND ws.status IN ('ACTIVE', 'UPCOMING')
                  AND ws.search_youth_relevant = true
                  AND (ws.min_age IS NULL OR ws.min_age <= :age)
                  AND (ws.max_age IS NULL OR ws.max_age >= :age)
                  AND (
                        (ws.min_income IS NULL AND ws.max_income IS NULL)
                        OR (ws.min_income = 0 AND ws.max_income = 0)
                        OR (
                            (ws.min_income IS NULL OR ws.min_income <= :incomeLevel)
                            AND (ws.max_income IS NULL OR ws.max_income >= :incomeLevel)
                        )
                      )
                  AND NOT EXISTS (
                      SELECT 1
                      FROM recent_policy_views own_rpv
                      WHERE own_rpv.user_key = :userKey
                        AND own_rpv.service_id = rpv.service_id
                        AND own_rpv.last_viewed_at >= :viewedSince
                  )
                  AND NOT EXISTS (
                      SELECT 1
                      FROM user_recommendations own_ur
                      WHERE own_ur.user_key = :userKey
                        AND own_ur.service_id = rpv.service_id
                        AND own_ur.recommended_at = (
                            SELECT MAX(latest_ur.recommended_at)
                            FROM user_recommendations latest_ur
                            WHERE latest_ur.user_key = :userKey
                        )
                  )
                GROUP BY rpv.service_id
                HAVING COUNT(DISTINCT esu.user_key) >= :minSimilarUsers
                ORDER BY SUM(esu.similarity_score) DESC,
                         COUNT(*) DESC,
                         MAX(rpv.last_viewed_at) DESC,
                         rpv.service_id DESC
                LIMIT :limit
                """;
    }

    private MapSqlParameterSource params(SimilarUsersViewedPolicyQuery query) {
        int incomeLevel = query.incomeLevel() != null ? query.incomeLevel() : 5;
        return new MapSqlParameterSource()
                .addValue("userKey", query.userKey())
                .addValue("age", query.age() != null ? query.age() : 25)
                .addValue("ageBand", query.ageBand())
                .addValue("ageBandPresent", hasText(query.ageBand()))
                .addValue("sido", query.sido())
                .addValue("sidoPresent", hasText(query.sido()))
                .addValue("regionCode", query.regionCode())
                .addValue("regionCodePresent", hasText(query.regionCode()))
                .addValue("incomeLevel", incomeLevel)
                .addValue("incomeMin", Math.max(1, incomeLevel - 1))
                .addValue("incomeMax", Math.min(10, incomeLevel + 1))
                .addValue("incomeLevelPresent", query.incomeLevel() != null)
                .addValue("interestFields", nonEmpty(query.interestFields()))
                .addValue("interestFieldsPresent", query.interestFields() != null && !query.interestFields().isEmpty())
                .addValue("targetTypes", nonEmpty(query.targetTypes()))
                .addValue("targetTypesPresent", query.targetTypes() != null && !query.targetTypes().isEmpty())
                .addValue("priorityCodes", nonEmpty(query.priorityCodes()))
                .addValue("priorityCodesPresent", query.priorityCodes() != null && !query.priorityCodes().isEmpty())
                .addValue("viewedSince", query.viewedSince())
                .addValue("minSimilarUsers", query.minSimilarUsers())
                .addValue("minSimilarityScore", query.minSimilarityScore())
                .addValue("limit", query.limit());
    }

    private List<String> nonEmpty(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of(NONE_SENTINEL);
        }
        return values;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
