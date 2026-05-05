package com.example.welfare.collect.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class CollectRuntimeStatusReadRepositoryImpl implements CollectRuntimeStatusReadRepository {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public Optional<LocalDateTime> findOpenUntil(String circuitKey) {
        return jdbcTemplate.query("""
                        select open_until
                          from collect_runtime_statuses
                         where circuit_key = ?
                        """,
                rs -> {
                    if (!rs.next()) {
                        return Optional.<LocalDateTime>empty();
                    }
                    Timestamp timestamp = rs.getTimestamp("open_until");
                    return Optional.ofNullable(timestamp).map(Timestamp::toLocalDateTime);
                },
                circuitKey
        );
    }
}
