package com.example.welfare.admin.dashboard.repository;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

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

    public AdminDashboardReadRows.NotificationAttemptSummaryRow fetchNotificationAttemptSummary(LocalDateTime windowAgo) {
        return jdbcTemplate.queryForObject("""
                select count(*) as total_attempts,
                       coalesce(sum(case when outcome in ('sent', 'fanout_completed', 'skipped_disabled') then 1 else 0 end), 0) as success_attempts,
                       coalesce(sum(case when outcome in ('gateway_false', 'failed', 'exception', 'fanout_failed') then 1 else 0 end), 0) as failed_attempts,
                       coalesce(sum(case when outcome = 'disabled' then 1 else 0 end), 0) as disabled_attempts,
                       coalesce(avg(duration_ms), 0) as average_duration_ms,
                       max(created_at) as latest_attempt_at
                  from notification_attempt_logs
                 where created_at >= :windowAgo
                """,
                new MapSqlParameterSource("windowAgo", windowAgo),
                (rs, rowNum) -> new AdminDashboardReadRows.NotificationAttemptSummaryRow(
                        rs.getLong("total_attempts"),
                        rs.getLong("success_attempts"),
                        rs.getLong("failed_attempts"),
                        rs.getLong("disabled_attempts"),
                        rs.getBigDecimal("average_duration_ms"),
                        AdminDashboardJdbcSupport.getLocalDateTime(rs, "latest_attempt_at")
                )
        );
    }

    public List<AdminDashboardReadRows.NotificationAttemptBreakdownRow> fetchNotificationAttemptBreakdowns(
            LocalDateTime windowAgo,
            int limit
    ) {
        return jdbcTemplate.query("""
                select channel,
                       kind,
                       outcome,
                       count(*) as attempt_count,
                       coalesce(avg(duration_ms), 0) as average_duration_ms,
                       max(created_at) as latest_attempt_at
                  from notification_attempt_logs
                 where created_at >= :windowAgo
              group by channel, kind, outcome
              order by attempt_count desc, channel asc, kind asc, outcome asc
                 limit %d
                """.formatted(limit),
                new MapSqlParameterSource("windowAgo", windowAgo),
                (rs, rowNum) -> new AdminDashboardReadRows.NotificationAttemptBreakdownRow(
                        rs.getString("channel"),
                        rs.getString("kind"),
                        rs.getString("outcome"),
                        rs.getLong("attempt_count"),
                        rs.getBigDecimal("average_duration_ms"),
                        AdminDashboardJdbcSupport.getLocalDateTime(rs, "latest_attempt_at")
                )
        );
    }

    public List<AdminDashboardReadRows.NotificationAttemptSampleRow> fetchRecentNotificationAttemptFailures(
            LocalDateTime windowAgo,
            int limit
    ) {
        return jdbcTemplate.query("""
                select id,
                       channel,
                       kind,
                       outcome,
                       item_count,
                       endpoint_host,
                       error_type,
                       duration_ms,
                       created_at
                  from notification_attempt_logs
                 where created_at >= :windowAgo
                   and outcome in ('gateway_false', 'failed', 'exception', 'fanout_failed', 'disabled')
              order by created_at desc, id desc
                 limit %d
                """.formatted(limit),
                new MapSqlParameterSource("windowAgo", windowAgo),
                (rs, rowNum) -> new AdminDashboardReadRows.NotificationAttemptSampleRow(
                        rs.getLong("id"),
                        rs.getString("channel"),
                        rs.getString("kind"),
                        rs.getString("outcome"),
                        rs.getInt("item_count"),
                        rs.getString("endpoint_host"),
                        rs.getString("error_type"),
                        rs.getLong("duration_ms"),
                        AdminDashboardJdbcSupport.getLocalDateTime(rs, "created_at")
                )
        );
    }
}
