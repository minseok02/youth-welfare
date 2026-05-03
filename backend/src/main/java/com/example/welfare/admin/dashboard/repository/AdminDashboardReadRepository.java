package com.example.welfare.admin.dashboard.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

@Repository
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminDashboardReadRepository {

    private static final int DEFAULT_TOP_KEYWORD_LIMIT = 5;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public CollectSummaryRow fetchCollectSummary(LocalDateTime dayAgo) {
        return jdbcTemplate.queryForObject("""
                select coalesce(sum(case when status = 'RUNNING' then 1 else 0 end), 0) as running_jobs,
                       coalesce(sum(case when status = 'SUCCESS' and started_at >= :dayAgo then 1 else 0 end), 0) as success_jobs_last_24h,
                       coalesce(sum(case when status = 'PARTIAL_SUCCESS' and started_at >= :dayAgo then 1 else 0 end), 0) as partial_success_jobs_last_24h,
                       coalesce(sum(case when status = 'FAILED' and started_at >= :dayAgo then 1 else 0 end), 0) as failed_jobs_last_24h
                  from api_sync_logs
                """,
                new MapSqlParameterSource("dayAgo", dayAgo),
                (rs, rowNum) -> new CollectSummaryRow(
                        rs.getLong("running_jobs"),
                        rs.getLong("success_jobs_last_24h"),
                        rs.getLong("partial_success_jobs_last_24h"),
                        rs.getLong("failed_jobs_last_24h")
                )
        );
    }

    public List<CollectJobSnapshotRow> fetchLatestCollectJobs() {
        return jdbcTemplate.query("""
                select log.job_name,
                       log.status,
                       log.started_at,
                       log.finished_at,
                       log.requested_count,
                       log.saved_count,
                       log.failed_count
                  from api_sync_logs log
                  join (
                        select job_name, max(id) as max_id
                          from api_sync_logs
                      group by job_name
                  ) latest on latest.max_id = log.id
              order by log.job_name asc
                """,
                (rs, rowNum) -> new CollectJobSnapshotRow(
                        rs.getString("job_name"),
                        rs.getString("status"),
                        getLocalDateTime(rs, "started_at"),
                        getLocalDateTime(rs, "finished_at"),
                        rs.getInt("requested_count"),
                        rs.getInt("saved_count"),
                        rs.getInt("failed_count")
                )
        );
    }

    public List<CollectFailureSnapshotRow> fetchLatestCollectFailures(LocalDateTime weekAgo) {
        return jdbcTemplate.query("""
                select job_name,
                       status,
                       started_at,
                       finished_at,
                       error_code,
                       error_message,
                       requested_count,
                       saved_count,
                       failed_count
                  from api_sync_logs
                 where started_at >= :weekAgo
                   and status = 'FAILED'
              order by started_at desc, id desc
                 limit 5
                """,
                new MapSqlParameterSource("weekAgo", weekAgo),
                (rs, rowNum) -> new CollectFailureSnapshotRow(
                        rs.getString("job_name"),
                        rs.getString("status"),
                        getLocalDateTime(rs, "started_at"),
                        getLocalDateTime(rs, "finished_at"),
                        rs.getString("error_code"),
                        rs.getString("error_message"),
                        rs.getInt("requested_count"),
                        rs.getInt("saved_count"),
                        rs.getInt("failed_count")
                )
        );
    }

    public RecommendationSummaryRow fetchRecommendationSummary(LocalDateTime dayAgo, LocalDateTime weekAgo) {
        return jdbcTemplate.queryForObject("""
                select count(*) as total_logs,
                       coalesce(sum(case when sent_at >= :dayAgo then 1 else 0 end), 0) as sent_last_24h,
                       coalesce(sum(case when sent_at >= :weekAgo then 1 else 0 end), 0) as sent_last_7d,
                       coalesce(sum(case when is_clicked = true and clicked_at >= :weekAgo then 1 else 0 end), 0) as clicked_last_7d,
                       coalesce(sum(case when is_fallback = true and sent_at >= :weekAgo then 1 else 0 end), 0) as fallback_last_7d,
                       max(clicked_at) as latest_clicked_at
                  from recommendation_logs
                """,
                new MapSqlParameterSource()
                        .addValue("dayAgo", dayAgo)
                        .addValue("weekAgo", weekAgo),
                (rs, rowNum) -> new RecommendationSummaryRow(
                        rs.getLong("total_logs"),
                        rs.getLong("sent_last_24h"),
                        rs.getLong("sent_last_7d"),
                        rs.getLong("clicked_last_7d"),
                        rs.getLong("fallback_last_7d"),
                        getLocalDateTime(rs, "latest_clicked_at")
                )
        );
    }

    public List<RecommendationWeightSnapshotRow> fetchRecommendationWeightBuckets(LocalDateTime weekAgo) {
        return jdbcTemplate.query("""
                select case
                           when rule_weight_used = 0.80 and ai_weight_used = 0.20 then 'COLD_START'
                           when rule_weight_used = 0.60 and ai_weight_used = 0.40 then 'GROWTH'
                           when rule_weight_used = 0.40 and ai_weight_used = 0.60 then 'STABLE'
                           else 'CUSTOM'
                       end as weight_key,
                       rule_weight_used,
                       ai_weight_used,
                       count(*) as log_count
                  from recommendation_logs
                 where sent_at >= :weekAgo
              group by weight_key, rule_weight_used, ai_weight_used
              order by log_count desc, rule_weight_used desc, ai_weight_used asc
                """,
                new MapSqlParameterSource("weekAgo", weekAgo),
                (rs, rowNum) -> new RecommendationWeightSnapshotRow(
                        rs.getString("weight_key"),
                        rs.getBigDecimal("rule_weight_used"),
                        rs.getBigDecimal("ai_weight_used"),
                        rs.getLong("log_count")
                )
        );
    }

    public NotificationSummaryRow fetchNotificationSummary(LocalDateTime dayAgo, LocalDateTime weekAgo) {
        return jdbcTemplate.queryForObject("""
                select coalesce(sum(case when status = 'SENT' and sent_at >= :dayAgo then 1 else 0 end), 0) as sent_last_24h,
                       coalesce(sum(case when status = 'FAILED' and created_at >= :dayAgo then 1 else 0 end), 0) as failed_last_24h,
                       coalesce(sum(case when status = 'SENT' and sent_at >= :weekAgo then 1 else 0 end), 0) as sent_last_7d,
                       coalesce(sum(case when status = 'FAILED' and created_at >= :weekAgo then 1 else 0 end), 0) as failed_last_7d
                  from notifications
                """,
                new MapSqlParameterSource()
                        .addValue("dayAgo", dayAgo)
                        .addValue("weekAgo", weekAgo),
                (rs, rowNum) -> new NotificationSummaryRow(
                        rs.getLong("sent_last_24h"),
                        rs.getLong("failed_last_24h"),
                        rs.getLong("sent_last_7d"),
                        rs.getLong("failed_last_7d")
                )
        );
    }

    public SearchSummaryRow fetchSearchSummary(LocalDateTime dayAgo, LocalDateTime weekAgo) {
        return jdbcTemplate.queryForObject("""
                select coalesce(sum(case when searched_at >= :dayAgo then 1 else 0 end), 0) as searches_last_24h,
                       coalesce(sum(case when searched_at >= :weekAgo then 1 else 0 end), 0) as searches_last_7d,
                       coalesce(sum(case when searched_at >= :weekAgo and result_count = 0 then 1 else 0 end), 0) as zero_result_searches_last_7d,
                       coalesce(count(distinct case when searched_at >= :weekAgo then client_fingerprint end), 0) as unique_fingerprints_last_7d,
                       coalesce(avg(case when searched_at >= :weekAgo then result_count end), 0) as avg_result_count_last_7d
                  from search_logs
                """,
                new MapSqlParameterSource()
                        .addValue("dayAgo", dayAgo)
                        .addValue("weekAgo", weekAgo),
                (rs, rowNum) -> new SearchSummaryRow(
                        rs.getLong("searches_last_24h"),
                        rs.getLong("searches_last_7d"),
                        rs.getLong("zero_result_searches_last_7d"),
                        rs.getLong("unique_fingerprints_last_7d"),
                        rs.getBigDecimal("avg_result_count_last_7d")
                )
        );
    }

    public List<SearchKeywordSnapshotRow> fetchTopSearchKeywords(LocalDateTime weekAgo) {
        return jdbcTemplate.query("""
                select keyword,
                       count(*) as search_count
                  from search_logs
                 where searched_at >= :weekAgo
                   and keyword is not null
                   and trim(keyword) <> ''
              group by keyword
              order by search_count desc, keyword asc
                 limit %d
                """.formatted(DEFAULT_TOP_KEYWORD_LIMIT),
                new MapSqlParameterSource("weekAgo", weekAgo),
                (rs, rowNum) -> new SearchKeywordSnapshotRow(
                        rs.getString("keyword"),
                        rs.getLong("search_count")
                )
        );
    }

    private LocalDateTime getLocalDateTime(ResultSet rs, String columnName) throws SQLException {
        java.sql.Timestamp timestamp = rs.getTimestamp(columnName);
        return timestamp != null ? timestamp.toLocalDateTime() : null;
    }

    public record CollectSummaryRow(
            long runningJobs,
            long successJobsLast24h,
            long partialSuccessJobsLast24h,
            long failedJobsLast24h
    ) {
    }

    public record CollectJobSnapshotRow(
            String jobName,
            String status,
            LocalDateTime startedAt,
            LocalDateTime finishedAt,
            int requestedCount,
            int savedCount,
            int failedCount
    ) {
    }

    public record CollectFailureSnapshotRow(
            String jobName,
            String status,
            LocalDateTime startedAt,
            LocalDateTime finishedAt,
            String errorCode,
            String errorMessage,
            int requestedCount,
            int savedCount,
            int failedCount
    ) {
    }

    public record RecommendationSummaryRow(
            long totalLogs,
            long sentLast24h,
            long sentLast7d,
            long clickedLast7d,
            long fallbackLast7d,
            LocalDateTime latestClickedAt
    ) {
    }

    public record RecommendationWeightSnapshotRow(
            String weightKey,
            BigDecimal ruleWeight,
            BigDecimal aiWeight,
            long logCount
    ) {
    }

    public record NotificationSummaryRow(
            long sentLast24h,
            long failedLast24h,
            long sentLast7d,
            long failedLast7d
    ) {
    }

    public record SearchSummaryRow(
            long searchesLast24h,
            long searchesLast7d,
            long zeroResultSearchesLast7d,
            long uniqueFingerprintsLast7d,
            BigDecimal averageResultCountLast7d
    ) {
    }

    public record SearchKeywordSnapshotRow(
            String keyword,
            long searchCount
    ) {
    }
}
