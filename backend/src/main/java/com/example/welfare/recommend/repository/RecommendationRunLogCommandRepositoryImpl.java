package com.example.welfare.recommend.repository;

import com.example.welfare.recommend.service.RecommendationRunLogCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
@RequiredArgsConstructor
public class RecommendationRunLogCommandRepositoryImpl implements RecommendationRunLogCommandRepository {

    private static final String INSERT_SQL = """
            INSERT INTO recommendation_run_logs (
                user_key,
                personal,
                outcome,
                cluster_id,
                retrieved_count,
                rule_scored_count,
                post_filter_count,
                reranked_count,
                saved_count,
                ai_status_counts_json,
                duration_ms
            ) VALUES (
                :userKey,
                :personal,
                :outcome,
                :clusterId,
                :retrievedCount,
                :ruleScoredCount,
                :postFilterCount,
                :rerankedCount,
                :savedCount,
                :aiStatusCountsJson,
                :durationMs
            )
            """;

    private static final String DELETE_OLDER_THAN_SQL = """
            DELETE FROM recommendation_run_logs
            WHERE created_at < :before
            """;

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Override
    public void save(RecommendationRunLogCommand command) {
        namedParameterJdbcTemplate.update(INSERT_SQL, new MapSqlParameterSource()
                .addValue("userKey", command.userKey())
                .addValue("personal", command.personal())
                .addValue("outcome", command.outcome())
                .addValue("clusterId", command.clusterId())
                .addValue("retrievedCount", command.retrievedCount())
                .addValue("ruleScoredCount", command.ruleScoredCount())
                .addValue("postFilterCount", command.postFilterCount())
                .addValue("rerankedCount", command.rerankedCount())
                .addValue("savedCount", command.savedCount())
                .addValue("aiStatusCountsJson", command.aiStatusCountsJson())
                .addValue("durationMs", command.durationMs()));
    }

    @Override
    public int deleteOlderThan(LocalDateTime before) {
        return namedParameterJdbcTemplate.update(
                DELETE_OLDER_THAN_SQL,
                new MapSqlParameterSource("before", before)
        );
    }
}
