package com.example.welfare.admin.dashboard.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Repository
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminDashboardCollectReadRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public AdminDashboardReadRows.CollectSummaryRow fetchCollectSummary(LocalDateTime dayAgo) {
        return jdbcTemplate.queryForObject("""
                select coalesce(sum(case when status = 'RUNNING' then 1 else 0 end), 0) as running_jobs,
                       coalesce(sum(case when status = 'SUCCESS' and started_at >= :dayAgo then 1 else 0 end), 0) as success_jobs_last_24h,
                       coalesce(sum(case when status = 'PARTIAL_SUCCESS' and started_at >= :dayAgo then 1 else 0 end), 0) as partial_success_jobs_last_24h,
                       coalesce(sum(case when status = 'FAILED' and started_at >= :dayAgo then 1 else 0 end), 0) as failed_jobs_last_24h
                  from api_sync_logs
                """,
                new MapSqlParameterSource("dayAgo", dayAgo),
                (rs, rowNum) -> new AdminDashboardReadRows.CollectSummaryRow(
                        rs.getLong("running_jobs"),
                        rs.getLong("success_jobs_last_24h"),
                        rs.getLong("partial_success_jobs_last_24h"),
                        rs.getLong("failed_jobs_last_24h")
                )
        );
    }

    public AdminDashboardReadRows.CollectTrendRow fetchCollectTrend(LocalDateTime windowAgo) {
        return jdbcTemplate.queryForObject("""
                select coalesce(sum(case when status = 'SUCCESS' and started_at >= :windowAgo then 1 else 0 end), 0) as success_jobs,
                       coalesce(sum(case when status = 'PARTIAL_SUCCESS' and started_at >= :windowAgo then 1 else 0 end), 0) as partial_success_jobs,
                       coalesce(sum(case when status = 'FAILED' and started_at >= :windowAgo then 1 else 0 end), 0) as failed_jobs
                  from api_sync_logs
                """,
                new MapSqlParameterSource("windowAgo", windowAgo),
                (rs, rowNum) -> new AdminDashboardReadRows.CollectTrendRow(
                        rs.getLong("success_jobs"),
                        rs.getLong("partial_success_jobs"),
                        rs.getLong("failed_jobs")
                )
        );
    }

    public List<AdminDashboardReadRows.CollectJobSnapshotRow> fetchLatestCollectJobs() {
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
                (rs, rowNum) -> new AdminDashboardReadRows.CollectJobSnapshotRow(
                        rs.getString("job_name"),
                        rs.getString("status"),
                        AdminDashboardJdbcSupport.getLocalDateTime(rs, "started_at"),
                        AdminDashboardJdbcSupport.getLocalDateTime(rs, "finished_at"),
                        rs.getInt("requested_count"),
                        rs.getInt("saved_count"),
                        rs.getInt("failed_count")
                )
        );
    }

    public List<AdminDashboardReadRows.CollectFailureSnapshotRow> fetchLatestCollectFailures(LocalDateTime weekAgo) {
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
                (rs, rowNum) -> new AdminDashboardReadRows.CollectFailureSnapshotRow(
                        rs.getString("job_name"),
                        rs.getString("status"),
                        AdminDashboardJdbcSupport.getLocalDateTime(rs, "started_at"),
                        AdminDashboardJdbcSupport.getLocalDateTime(rs, "finished_at"),
                        rs.getString("error_code"),
                        rs.getString("error_message"),
                        rs.getInt("requested_count"),
                        rs.getInt("saved_count"),
                        rs.getInt("failed_count")
                )
        );
    }

    public AdminDashboardReadRows.CollectFailureSummaryRow fetchCollectFailureSummary(LocalDateTime windowAgo) {
        return jdbcTemplate.queryForObject("""
                select coalesce(sum(case when status = 'FAILED' then 1 else 0 end), 0) as total_failed_jobs,
                       coalesce(sum(case when status = 'PARTIAL_SUCCESS' then 1 else 0 end), 0) as total_partial_success_jobs
                  from api_sync_logs
                 where started_at >= :windowAgo
                """,
                new MapSqlParameterSource("windowAgo", windowAgo),
                (rs, rowNum) -> new AdminDashboardReadRows.CollectFailureSummaryRow(
                        rs.getLong("total_failed_jobs"),
                        rs.getLong("total_partial_success_jobs")
                )
        );
    }

    public List<AdminDashboardReadRows.CollectFailureJobBreakdownRow> fetchCollectFailureJobBreakdowns(LocalDateTime windowAgo, int limit) {
        return jdbcTemplate.query("""
                select job_name,
                       coalesce(sum(case when status = 'FAILED' then 1 else 0 end), 0) as failed_count,
                       coalesce(sum(case when status = 'PARTIAL_SUCCESS' then 1 else 0 end), 0) as partial_success_count,
                       max(started_at) as latest_started_at
                  from api_sync_logs
                 where started_at >= :windowAgo
                   and status in ('FAILED', 'PARTIAL_SUCCESS')
              group by job_name
              order by failed_count desc,
                       partial_success_count desc,
                       latest_started_at desc,
                       job_name asc
                 limit %d
                """.formatted(limit),
                new MapSqlParameterSource("windowAgo", windowAgo),
                (rs, rowNum) -> new AdminDashboardReadRows.CollectFailureJobBreakdownRow(
                        rs.getString("job_name"),
                        rs.getLong("failed_count"),
                        rs.getLong("partial_success_count"),
                        AdminDashboardJdbcSupport.getLocalDateTime(rs, "latest_started_at")
                )
        );
    }

    public List<AdminDashboardReadRows.CollectFailureErrorCodeBreakdownRow> fetchCollectFailureErrorCodeBreakdowns(LocalDateTime windowAgo, int limit) {
        return jdbcTemplate.query("""
                select coalesce(error_code, 'UNKNOWN') as error_code,
                       count(*) as failed_count
                  from api_sync_logs
                 where started_at >= :windowAgo
                   and status = 'FAILED'
              group by coalesce(error_code, 'UNKNOWN')
              order by failed_count desc, error_code asc
                 limit %d
                """.formatted(limit),
                new MapSqlParameterSource("windowAgo", windowAgo),
                (rs, rowNum) -> new AdminDashboardReadRows.CollectFailureErrorCodeBreakdownRow(
                        rs.getString("error_code"),
                        rs.getLong("failed_count")
                )
        );
    }

    public List<AdminDashboardReadRows.CollectFailureSampleRow> fetchRecentCollectFailureSamples(LocalDateTime windowAgo, int limit) {
        return jdbcTemplate.query("""
                select job_name,
                       status,
                       error_code,
                       error_message,
                       started_at,
                       finished_at,
                       requested_count,
                       saved_count,
                       failed_count
                  from api_sync_logs
                 where started_at >= :windowAgo
                   and status in ('FAILED', 'PARTIAL_SUCCESS')
              order by started_at desc, id desc
                 limit %d
                """.formatted(limit),
                new MapSqlParameterSource("windowAgo", windowAgo),
                (rs, rowNum) -> new AdminDashboardReadRows.CollectFailureSampleRow(
                        rs.getString("job_name"),
                        rs.getString("status"),
                        rs.getString("error_code"),
                        rs.getString("error_message"),
                        AdminDashboardJdbcSupport.getLocalDateTime(rs, "started_at"),
                        AdminDashboardJdbcSupport.getLocalDateTime(rs, "finished_at"),
                        rs.getInt("requested_count"),
                        rs.getInt("saved_count"),
                        rs.getInt("failed_count")
                )
        );
    }

    public List<AdminDashboardReadRows.CollectJobStreakRow> fetchCurrentCollectJobStreaks(int limit) {
        return jdbcTemplate.query("""
                with base as (
                    select log.job_name,
                           log.status,
                           log.started_at,
                           log.id,
                           row_number() over (
                               partition by log.job_name
                               order by log.started_at desc, log.id desc
                           ) as rn,
                           first_value(log.status) over (
                               partition by log.job_name
                               order by log.started_at desc, log.id desc
                           ) as latest_status
                      from api_sync_logs log
                ),
                streaks as (
                    select base.job_name,
                           base.status,
                           base.started_at,
                           base.latest_status,
                           sum(case when base.status <> base.latest_status then 1 else 0 end) over (
                               partition by base.job_name
                               order by base.rn
                               rows between unbounded preceding and current row
                           ) as change_seen
                      from base
                )
                select job_name,
                       latest_status as streak_status,
                       count(*) as streak_count,
                       max(started_at) as latest_started_at
                  from streaks
                 where latest_status in ('FAILED', 'PARTIAL_SUCCESS')
                   and change_seen = 0
              group by job_name, latest_status
              order by streak_count desc, latest_started_at desc, job_name asc
                 limit %d
                """.formatted(limit),
                (rs, rowNum) -> new AdminDashboardReadRows.CollectJobStreakRow(
                        rs.getString("job_name"),
                        rs.getString("streak_status"),
                        rs.getLong("streak_count"),
                        AdminDashboardJdbcSupport.getLocalDateTime(rs, "latest_started_at")
                )
        );
    }
}
