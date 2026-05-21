package com.example.welfare.collect.repository;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CollectExecutionLockCleanupCommandRepositoryImpl implements CollectExecutionLockCleanupCommandRepository {

    private static final String RELEASE_SQL = """
            DELETE FROM collect_execution_locks
             WHERE lock_name = :lockName
               AND owner_token = :ownerToken
            """;

    private final NamedParameterJdbcTemplate collectExecutionLockCleanupNamedParameterJdbcTemplate;

    public CollectExecutionLockCleanupCommandRepositoryImpl(
            @Qualifier("collectExecutionLockCleanupNamedParameterJdbcTemplate")
            NamedParameterJdbcTemplate collectExecutionLockCleanupNamedParameterJdbcTemplate
    ) {
        this.collectExecutionLockCleanupNamedParameterJdbcTemplate = collectExecutionLockCleanupNamedParameterJdbcTemplate;
    }

    @Override
    public boolean release(String lockName, String ownerToken) {
        int deleted = collectExecutionLockCleanupNamedParameterJdbcTemplate.update(
                RELEASE_SQL,
                new MapSqlParameterSource()
                        .addValue("lockName", lockName)
                        .addValue("ownerToken", ownerToken)
        );
        return deleted == 1;
    }
}
