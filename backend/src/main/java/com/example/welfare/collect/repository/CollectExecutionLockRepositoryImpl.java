package com.example.welfare.collect.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;

@Repository
@RequiredArgsConstructor
public class CollectExecutionLockRepositoryImpl implements CollectExecutionLockRepository {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public boolean tryAcquire(String lockName, String ownerToken, LocalDateTime now, LocalDateTime lockedUntil) {
        int inserted = jdbcTemplate.update("""
                insert ignore into collect_execution_locks
                    (lock_name, owner_token, locked_until, acquired_at, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?)
                """,
                lockName,
                ownerToken,
                Timestamp.valueOf(lockedUntil),
                Timestamp.valueOf(now),
                Timestamp.valueOf(now),
                Timestamp.valueOf(now)
        );
        if (inserted == 1) {
            return true;
        }

        int updated = jdbcTemplate.update("""
                update collect_execution_locks
                   set owner_token = ?,
                       locked_until = ?,
                       acquired_at = ?,
                       updated_at = ?
                 where lock_name = ?
                   and locked_until < ?
                """,
                ownerToken,
                Timestamp.valueOf(lockedUntil),
                Timestamp.valueOf(now),
                Timestamp.valueOf(now),
                lockName,
                Timestamp.valueOf(now)
        );
        return updated == 1;
    }

    @Override
    public boolean release(String lockName, String ownerToken) {
        int deleted = jdbcTemplate.update("""
                delete from collect_execution_locks
                 where lock_name = ?
                   and owner_token = ?
                """,
                lockName,
                ownerToken
        );
        return deleted == 1;
    }
}
