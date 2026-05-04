package com.example.welfare.collect.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;

@Repository
@RequiredArgsConstructor
public class CollectRuntimeStatusCommandRepositoryImpl implements CollectRuntimeStatusCommandRepository {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void upsertOpenUntil(String circuitKey, LocalDateTime openUntil, LocalDateTime updatedAt) {
        jdbcTemplate.update("""
                insert into collect_runtime_statuses
                    (circuit_key, open_until, created_at, updated_at)
                values (?, ?, ?, ?)
                on duplicate key update
                    open_until = values(open_until),
                    updated_at = values(updated_at)
                """,
                circuitKey,
                Timestamp.valueOf(openUntil),
                Timestamp.valueOf(updatedAt),
                Timestamp.valueOf(updatedAt)
        );
    }
}
