package com.example.welfare.admin.dashboard.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class AdminNotificationStaleTargetReadRepository {

    private final JdbcTemplate jdbcTemplate;

    private static final RowMapper<NotificationStaleTargetRow> ROW_MAPPER = (rs, rowNum) -> new NotificationStaleTargetRow(
            rs.getString("kind"),
            rs.getString("title"),
            rs.getString("deeplink_url"),
            rs.getLong("row_count"),
            rs.getLong("user_count"),
            toLocalDateTime(rs.getTimestamp("oldest_created_at")),
            toLocalDateTime(rs.getTimestamp("newest_created_at"))
    );

    public long countStaleRows(LocalDateTime cutoff) {
        return jdbcTemplate.queryForObject("""
                select count(*)
                from user_alerts ua
                where ua.status = 'UNREAD'
                  and ua.created_at < ?
                """, Long.class, Timestamp.valueOf(cutoff));
    }

    public long countStaleGroups(LocalDateTime cutoff) {
        return jdbcTemplate.queryForObject("""
                select count(*)
                from (
                  select ua.kind, ua.title, coalesce(ua.deeplink_url, '')
                  from user_alerts ua
                  where ua.status = 'UNREAD'
                    and ua.created_at < ?
                  group by ua.kind, ua.title, coalesce(ua.deeplink_url, '')
                ) groups
                """, Long.class, Timestamp.valueOf(cutoff));
    }

    public List<NotificationStaleTargetRow> findTargets(LocalDateTime cutoff, int limit) {
        return jdbcTemplate.query("""
                select ua.kind,
                       ua.title,
                       coalesce(ua.deeplink_url, '') as deeplink_url,
                       count(*) as row_count,
                       count(distinct ua.user_key) as user_count,
                       min(ua.created_at) as oldest_created_at,
                       max(ua.created_at) as newest_created_at
                from user_alerts ua
                where ua.status = 'UNREAD'
                  and ua.created_at < ?
                group by ua.kind, ua.title, coalesce(ua.deeplink_url, '')
                order by row_count desc, oldest_created_at asc, title asc
                limit ?
                """, ROW_MAPPER, Timestamp.valueOf(cutoff), limit);
    }

    private static LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    public record NotificationStaleTargetRow(
            String kind,
            String title,
            String deeplinkUrl,
            long rowCount,
            long userCount,
            LocalDateTime oldestCreatedAt,
            LocalDateTime newestCreatedAt
    ) {
    }
}
