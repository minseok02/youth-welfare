package com.example.welfare.recommend.repository;

import com.example.welfare.recommend.entity.ClusterAiResult;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public class ClusterAiResultCommandRepositoryImpl implements ClusterAiResultCommandRepository {

    private static final String DELETE_EXPIRED_SQL = """
            DELETE FROM cluster_ai_results
            WHERE created_at < :before
            """;

    private final ClusterAiResultRepository clusterAiResultRepository;
    private final NamedParameterJdbcTemplate clusterAiCleanupNamedParameterJdbcTemplate;

    public ClusterAiResultCommandRepositoryImpl(
            ClusterAiResultRepository clusterAiResultRepository,
            @Qualifier("clusterAiCleanupNamedParameterJdbcTemplate")
            NamedParameterJdbcTemplate clusterAiCleanupNamedParameterJdbcTemplate
    ) {
        this.clusterAiResultRepository = clusterAiResultRepository;
        this.clusterAiCleanupNamedParameterJdbcTemplate = clusterAiCleanupNamedParameterJdbcTemplate;
    }

    @Override
    public ClusterAiResult save(ClusterAiResult clusterAiResult) {
        return clusterAiResultRepository.save(clusterAiResult);
    }

    @Override
    public void deleteExpiredBefore(LocalDateTime before) {
        clusterAiCleanupNamedParameterJdbcTemplate.update(
                DELETE_EXPIRED_SQL,
                new MapSqlParameterSource("before", before)
        );
    }
}
