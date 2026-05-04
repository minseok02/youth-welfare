package com.example.welfare.admin.dashboard.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Repository
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminDashboardNotificationReadRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public AdminDashboardReadRows.NotificationSummaryRow fetchNotificationSummary(LocalDateTime dayAgo, LocalDateTime weekAgo) {
        return jdbcTemplate.queryForObject("""
                select coalesce(sum(case when status = 'SENT' and sent_at >= :dayAgo then 1 else 0 end), 0) as sent_last_24h,
                       coalesce(sum(case when status = 'FAILED' and created_at >= :dayAgo then 1 else 0 end), 0) as failed_last_24h,
                       coalesce(sum(case when status = 'SENT' and sent_at >= :weekAgo then 1 else 0 end), 0) as sent_in_window,
                       coalesce(sum(case when status = 'FAILED' and created_at >= :weekAgo then 1 else 0 end), 0) as failed_in_window
                  from notifications
                """,
                new MapSqlParameterSource()
                        .addValue("dayAgo", dayAgo)
                        .addValue("weekAgo", weekAgo),
                (rs, rowNum) -> new AdminDashboardReadRows.NotificationSummaryRow(
                        rs.getLong("sent_last_24h"),
                        rs.getLong("failed_last_24h"),
                        rs.getLong("sent_in_window"),
                        rs.getLong("failed_in_window")
                )
        );
    }
}
