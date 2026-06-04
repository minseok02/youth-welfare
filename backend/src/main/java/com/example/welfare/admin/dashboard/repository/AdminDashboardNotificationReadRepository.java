package com.example.welfare.admin.dashboard.repository;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Repository
@Transactional(readOnly = true)
public class AdminDashboardNotificationReadRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public AdminDashboardNotificationReadRepository(
            @Qualifier("adminDashboardReadNamedParameterJdbcTemplate") NamedParameterJdbcTemplate jdbcTemplate
    ) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public AdminDashboardReadRows.NotificationSummaryRow fetchNotificationSummary(LocalDateTime dayAgo, LocalDateTime weekAgo) {
        return jdbcTemplate.queryForObject("""
                select notification_summary.sent_last_24h,
                       notification_summary.failed_last_24h,
                       notification_summary.sent_in_window,
                       notification_summary.failed_in_window,
                       user_alert_summary.unread_alerts,
                       user_alert_summary.stale_unread_7d,
                       user_alert_summary.stale_unread_14d,
                       notification_summary.retryable_failed_notifications,
                       notification_summary.terminal_failed_notifications
                  from (
                        select coalesce(sum(case when status = 'SENT' and sent_at >= :dayAgo then 1 else 0 end), 0) as sent_last_24h,
                               coalesce(sum(case when status = 'FAILED' and created_at >= :dayAgo then 1 else 0 end), 0) as failed_last_24h,
                               coalesce(sum(case when status = 'SENT' and sent_at >= :weekAgo then 1 else 0 end), 0) as sent_in_window,
                               coalesce(sum(case when status = 'FAILED' and created_at >= :weekAgo then 1 else 0 end), 0) as failed_in_window,
                               coalesce(sum(case when status = 'FAILED' and next_retry_at is not null then 1 else 0 end), 0) as retryable_failed_notifications,
                               coalesce(sum(case when status = 'FAILED' and next_retry_at is null then 1 else 0 end), 0) as terminal_failed_notifications
                          from notifications
                  ) notification_summary
                  cross join (
                        select coalesce(sum(case when status = 'UNREAD' then 1 else 0 end), 0) as unread_alerts
                             , coalesce(sum(case when status = 'UNREAD' and created_at < :stale7dCutoff then 1 else 0 end), 0) as stale_unread_7d
                             , coalesce(sum(case when status = 'UNREAD' and created_at < :stale14dCutoff then 1 else 0 end), 0) as stale_unread_14d
                          from user_alerts
                  ) user_alert_summary
                """,
                new MapSqlParameterSource()
                        .addValue("dayAgo", dayAgo)
                        .addValue("weekAgo", weekAgo)
                        .addValue("stale7dCutoff", LocalDateTime.now().minusDays(7))
                        .addValue("stale14dCutoff", LocalDateTime.now().minusDays(14)),
                (rs, rowNum) -> new AdminDashboardReadRows.NotificationSummaryRow(
                        rs.getLong("sent_last_24h"),
                        rs.getLong("failed_last_24h"),
                        rs.getLong("sent_in_window"),
                        rs.getLong("failed_in_window"),
                        rs.getLong("unread_alerts"),
                        rs.getLong("stale_unread_7d"),
                        rs.getLong("stale_unread_14d"),
                        rs.getLong("retryable_failed_notifications"),
                        rs.getLong("terminal_failed_notifications")
                )
        );
    }
}
