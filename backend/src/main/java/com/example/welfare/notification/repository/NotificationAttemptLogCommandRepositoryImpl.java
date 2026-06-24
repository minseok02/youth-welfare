package com.example.welfare.notification.repository;

import com.example.welfare.notification.service.NotificationAttemptLogCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
@RequiredArgsConstructor
public class NotificationAttemptLogCommandRepositoryImpl implements NotificationAttemptLogCommandRepository {

    private static final String INSERT_SQL = """
            INSERT INTO notification_attempt_logs (
                user_key_hash,
                channel,
                kind,
                outcome,
                item_count,
                endpoint_host,
                error_type,
                duration_ms
            ) VALUES (
                :userKeyHash,
                :channel,
                :kind,
                :outcome,
                :itemCount,
                :endpointHost,
                :errorType,
                :durationMs
            )
            """;

    private static final String DELETE_OLDER_THAN_SQL = """
            DELETE FROM notification_attempt_logs
            WHERE created_at < :before
            """;

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Override
    public void save(NotificationAttemptLogCommand command) {
        namedParameterJdbcTemplate.update(INSERT_SQL, new MapSqlParameterSource()
                .addValue("userKeyHash", command.userKeyHash())
                .addValue("channel", command.channel())
                .addValue("kind", command.kind())
                .addValue("outcome", command.outcome())
                .addValue("itemCount", command.itemCount())
                .addValue("endpointHost", command.endpointHost())
                .addValue("errorType", command.errorType())
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
