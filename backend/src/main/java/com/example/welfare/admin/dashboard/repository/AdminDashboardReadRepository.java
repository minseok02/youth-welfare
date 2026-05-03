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

    public CollectTrendRow fetchCollectTrend(LocalDateTime windowAgo) {
        return jdbcTemplate.queryForObject("""
                select coalesce(sum(case when status = 'SUCCESS' and started_at >= :windowAgo then 1 else 0 end), 0) as success_jobs,
                       coalesce(sum(case when status = 'PARTIAL_SUCCESS' and started_at >= :windowAgo then 1 else 0 end), 0) as partial_success_jobs,
                       coalesce(sum(case when status = 'FAILED' and started_at >= :windowAgo then 1 else 0 end), 0) as failed_jobs
                  from api_sync_logs
                """,
                new MapSqlParameterSource("windowAgo", windowAgo),
                (rs, rowNum) -> new CollectTrendRow(
                        rs.getLong("success_jobs"),
                        rs.getLong("partial_success_jobs"),
                        rs.getLong("failed_jobs")
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

    public CollectFailureSummaryRow fetchCollectFailureSummary(LocalDateTime windowAgo) {
        return jdbcTemplate.queryForObject("""
                select coalesce(sum(case when status = 'FAILED' then 1 else 0 end), 0) as total_failed_jobs,
                       coalesce(sum(case when status = 'PARTIAL_SUCCESS' then 1 else 0 end), 0) as total_partial_success_jobs
                  from api_sync_logs
                 where started_at >= :windowAgo
                """,
                new MapSqlParameterSource("windowAgo", windowAgo),
                (rs, rowNum) -> new CollectFailureSummaryRow(
                        rs.getLong("total_failed_jobs"),
                        rs.getLong("total_partial_success_jobs")
                )
        );
    }

    public List<CollectFailureJobBreakdownRow> fetchCollectFailureJobBreakdowns(LocalDateTime windowAgo, int limit) {
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
                (rs, rowNum) -> new CollectFailureJobBreakdownRow(
                        rs.getString("job_name"),
                        rs.getLong("failed_count"),
                        rs.getLong("partial_success_count"),
                        getLocalDateTime(rs, "latest_started_at")
                )
        );
    }

    public List<CollectFailureErrorCodeBreakdownRow> fetchCollectFailureErrorCodeBreakdowns(LocalDateTime windowAgo, int limit) {
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
                (rs, rowNum) -> new CollectFailureErrorCodeBreakdownRow(
                        rs.getString("error_code"),
                        rs.getLong("failed_count")
                )
        );
    }

    public List<CollectFailureSampleRow> fetchRecentCollectFailureSamples(LocalDateTime windowAgo, int limit) {
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
                (rs, rowNum) -> new CollectFailureSampleRow(
                        rs.getString("job_name"),
                        rs.getString("status"),
                        rs.getString("error_code"),
                        rs.getString("error_message"),
                        getLocalDateTime(rs, "started_at"),
                        getLocalDateTime(rs, "finished_at"),
                        rs.getInt("requested_count"),
                        rs.getInt("saved_count"),
                        rs.getInt("failed_count")
                )
        );
    }

    public List<CollectJobRunRow> fetchRecentCollectJobRuns(LocalDateTime windowAgo, int perJobLimit) {
        return jdbcTemplate.query("""
                select ranked.job_name,
                       ranked.status,
                       ranked.started_at
                  from (
                        select log.job_name,
                               log.status,
                               log.started_at,
                               row_number() over (
                                   partition by log.job_name
                                   order by log.started_at desc, log.id desc
                               ) as rn
                          from api_sync_logs log
                         where log.started_at >= :windowAgo
                  ) ranked
                 where ranked.rn <= :perJobLimit
              order by ranked.job_name asc, ranked.started_at desc
                """,
                new MapSqlParameterSource()
                        .addValue("windowAgo", windowAgo)
                        .addValue("perJobLimit", perJobLimit),
                (rs, rowNum) -> new CollectJobRunRow(
                        rs.getString("job_name"),
                        rs.getString("status"),
                        getLocalDateTime(rs, "started_at")
                )
        );
    }

    public RecommendationSummaryRow fetchRecommendationSummary(LocalDateTime dayAgo, LocalDateTime weekAgo) {
        return jdbcTemplate.queryForObject("""
                select count(*) as total_logs,
                       coalesce(sum(case when sent_at >= :dayAgo then 1 else 0 end), 0) as sent_last_24h,
                       coalesce(sum(case when sent_at >= :weekAgo then 1 else 0 end), 0) as sent_in_window,
                       coalesce(sum(case when is_clicked = true and clicked_at >= :weekAgo then 1 else 0 end), 0) as clicked_in_window,
                       coalesce(sum(case when is_fallback = true and sent_at >= :weekAgo then 1 else 0 end), 0) as fallback_in_window,
                       max(clicked_at) as latest_clicked_at
                  from recommendation_logs
                """,
                new MapSqlParameterSource()
                        .addValue("dayAgo", dayAgo)
                        .addValue("weekAgo", weekAgo),
                (rs, rowNum) -> new RecommendationSummaryRow(
                        rs.getLong("total_logs"),
                        rs.getLong("sent_last_24h"),
                        rs.getLong("sent_in_window"),
                        rs.getLong("clicked_in_window"),
                        rs.getLong("fallback_in_window"),
                        getLocalDateTime(rs, "latest_clicked_at")
                )
        );
    }

    public RecommendationTrendRow fetchRecommendationTrend(LocalDateTime windowAgo) {
        return jdbcTemplate.queryForObject("""
                select coalesce(sum(case when sent_at >= :windowAgo then 1 else 0 end), 0) as sent_count,
                       coalesce(sum(case when is_clicked = true and clicked_at >= :windowAgo then 1 else 0 end), 0) as clicked_count,
                       coalesce(sum(case when is_fallback = true and sent_at >= :windowAgo then 1 else 0 end), 0) as fallback_count
                  from recommendation_logs
                """,
                new MapSqlParameterSource("windowAgo", windowAgo),
                (rs, rowNum) -> new RecommendationTrendRow(
                        rs.getLong("sent_count"),
                        rs.getLong("clicked_count"),
                        rs.getLong("fallback_count")
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

    public List<RecommendationSourceBreakdownRow> fetchRecommendationSourceBreakdowns(LocalDateTime windowAgo, int limit) {
        return jdbcTemplate.query("""
                select ws.source_type,
                       count(*) as sent_count,
                       coalesce(sum(case when rl.is_clicked = true then 1 else 0 end), 0) as clicked_count,
                       coalesce(sum(case when rl.is_fallback = true then 1 else 0 end), 0) as fallback_count
                  from recommendation_logs rl
                  join welfare_services ws on ws.id = rl.service_id
                 where rl.sent_at >= :windowAgo
              group by ws.source_type
              order by sent_count desc, ws.source_type asc
                 limit %d
                """.formatted(limit),
                new MapSqlParameterSource("windowAgo", windowAgo),
                (rs, rowNum) -> new RecommendationSourceBreakdownRow(
                        rs.getString("source_type"),
                        rs.getLong("sent_count"),
                        rs.getLong("clicked_count"),
                        rs.getLong("fallback_count")
                )
        );
    }

    public List<RecommendationCategoryBreakdownRow> fetchRecommendationCategoryBreakdowns(LocalDateTime windowAgo, int limit) {
        return jdbcTemplate.query("""
                select ws.unified_category,
                       count(*) as sent_count,
                       coalesce(sum(case when rl.is_clicked = true then 1 else 0 end), 0) as clicked_count,
                       coalesce(sum(case when rl.is_fallback = true then 1 else 0 end), 0) as fallback_count
                  from recommendation_logs rl
                  join welfare_services ws on ws.id = rl.service_id
                 where rl.sent_at >= :windowAgo
              group by ws.unified_category
              order by sent_count desc, coalesce(ws.unified_category, '') asc
                 limit %d
                """.formatted(limit),
                new MapSqlParameterSource("windowAgo", windowAgo),
                (rs, rowNum) -> new RecommendationCategoryBreakdownRow(
                        rs.getString("unified_category"),
                        rs.getLong("sent_count"),
                        rs.getLong("clicked_count"),
                        rs.getLong("fallback_count")
                )
        );
    }

    public List<RecommendationWeightBreakdownRow> fetchRecommendationWeightBreakdowns(LocalDateTime windowAgo, int limit) {
        return jdbcTemplate.query("""
                select case
                           when rl.rule_weight_used = 0.80 and rl.ai_weight_used = 0.20 then 'COLD_START'
                           when rl.rule_weight_used = 0.60 and rl.ai_weight_used = 0.40 then 'GROWTH'
                           when rl.rule_weight_used = 0.40 and rl.ai_weight_used = 0.60 then 'STABLE'
                           else 'CUSTOM'
                       end as weight_key,
                       rl.rule_weight_used,
                       rl.ai_weight_used,
                       count(*) as sent_count,
                       coalesce(sum(case when rl.is_clicked = true then 1 else 0 end), 0) as clicked_count,
                       coalesce(sum(case when rl.is_fallback = true then 1 else 0 end), 0) as fallback_count
                  from recommendation_logs rl
                 where rl.sent_at >= :windowAgo
              group by weight_key, rl.rule_weight_used, rl.ai_weight_used
              order by sent_count desc, rl.rule_weight_used desc, rl.ai_weight_used asc
                 limit %d
                """.formatted(limit),
                new MapSqlParameterSource("windowAgo", windowAgo),
                (rs, rowNum) -> new RecommendationWeightBreakdownRow(
                        rs.getString("weight_key"),
                        rs.getBigDecimal("rule_weight_used"),
                        rs.getBigDecimal("ai_weight_used"),
                        rs.getLong("sent_count"),
                        rs.getLong("clicked_count"),
                        rs.getLong("fallback_count")
                )
        );
    }

    public List<RecommendationSampleRow> fetchRecentFallbackRecommendationSamples(LocalDateTime windowAgo, int limit) {
        return jdbcTemplate.query("""
                select rl.id as log_id,
                       ws.id as service_id,
                       ws.title,
                       ws.source_type,
                       ws.unified_category,
                       rl.final_score,
                       rl.is_fallback,
                       rl.is_clicked,
                       rl.sent_at,
                       rl.clicked_at
                  from recommendation_logs rl
                  join welfare_services ws on ws.id = rl.service_id
                 where rl.sent_at >= :windowAgo
                   and rl.is_fallback = true
              order by rl.sent_at desc, rl.id desc
                 limit %d
                """.formatted(limit),
                new MapSqlParameterSource("windowAgo", windowAgo),
                (rs, rowNum) -> new RecommendationSampleRow(
                        rs.getLong("log_id"),
                        rs.getLong("service_id"),
                        rs.getString("title"),
                        rs.getString("source_type"),
                        rs.getString("unified_category"),
                        rs.getBigDecimal("final_score"),
                        rs.getBoolean("is_fallback"),
                        rs.getBoolean("is_clicked"),
                        getLocalDateTime(rs, "sent_at"),
                        getLocalDateTime(rs, "clicked_at")
                )
        );
    }

    public List<RecommendationSampleRow> fetchRecentClickedRecommendationSamples(LocalDateTime windowAgo, int limit) {
        return jdbcTemplate.query("""
                select rl.id as log_id,
                       ws.id as service_id,
                       ws.title,
                       ws.source_type,
                       ws.unified_category,
                       rl.final_score,
                       rl.is_fallback,
                       rl.is_clicked,
                       rl.sent_at,
                       rl.clicked_at
                  from recommendation_logs rl
                  join welfare_services ws on ws.id = rl.service_id
                 where rl.sent_at >= :windowAgo
                   and rl.is_clicked = true
              order by rl.clicked_at desc, rl.id desc
                 limit %d
                """.formatted(limit),
                new MapSqlParameterSource("windowAgo", windowAgo),
                (rs, rowNum) -> new RecommendationSampleRow(
                        rs.getLong("log_id"),
                        rs.getLong("service_id"),
                        rs.getString("title"),
                        rs.getString("source_type"),
                        rs.getString("unified_category"),
                        rs.getBigDecimal("final_score"),
                        rs.getBoolean("is_fallback"),
                        rs.getBoolean("is_clicked"),
                        getLocalDateTime(rs, "sent_at"),
                        getLocalDateTime(rs, "clicked_at")
                )
        );
    }

    public List<RecommendationRepeatExposureGroupRow> fetchRecommendationRepeatExposureGroups(LocalDateTime windowAgo, int limit) {
        return jdbcTemplate.query("""
                select rl.user_key,
                       ws.id as service_id,
                       ws.title,
                       ws.source_type,
                       ws.unified_category,
                       count(*) as exposure_count,
                       coalesce(sum(case when rl.is_clicked = true then 1 else 0 end), 0) as clicked_count,
                       coalesce(sum(case when rl.is_fallback = true then 1 else 0 end), 0) as fallback_count,
                       min(rl.sent_at) as first_sent_at,
                       max(rl.sent_at) as latest_sent_at,
                       max(rl.clicked_at) as latest_clicked_at
                  from recommendation_logs rl
                  join welfare_services ws on ws.id = rl.service_id
                 where rl.sent_at >= :windowAgo
              group by rl.user_key, ws.id, ws.title, ws.source_type, ws.unified_category
                having count(*) > 1
              order by exposure_count desc,
                       latest_sent_at desc,
                       rl.user_key asc,
                       ws.id asc
                 limit %d
                """.formatted(limit),
                new MapSqlParameterSource("windowAgo", windowAgo),
                (rs, rowNum) -> new RecommendationRepeatExposureGroupRow(
                        rs.getString("user_key"),
                        rs.getLong("service_id"),
                        rs.getString("title"),
                        rs.getString("source_type"),
                        rs.getString("unified_category"),
                        rs.getLong("exposure_count"),
                        rs.getLong("clicked_count"),
                        rs.getLong("fallback_count"),
                        getLocalDateTime(rs, "first_sent_at"),
                        getLocalDateTime(rs, "latest_sent_at"),
                        getLocalDateTime(rs, "latest_clicked_at")
                )
        );
    }

    public NotificationSummaryRow fetchNotificationSummary(LocalDateTime dayAgo, LocalDateTime weekAgo) {
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
                (rs, rowNum) -> new NotificationSummaryRow(
                        rs.getLong("sent_last_24h"),
                        rs.getLong("failed_last_24h"),
                        rs.getLong("sent_in_window"),
                        rs.getLong("failed_in_window")
                )
        );
    }

    public SearchSummaryRow fetchSearchSummary(LocalDateTime dayAgo, LocalDateTime weekAgo) {
        return jdbcTemplate.queryForObject("""
                select coalesce(sum(case when searched_at >= :dayAgo then 1 else 0 end), 0) as searches_last_24h,
                       coalesce(sum(case when searched_at >= :weekAgo then 1 else 0 end), 0) as searches_in_window,
                       coalesce(sum(case when searched_at >= :weekAgo and result_count = 0 then 1 else 0 end), 0) as zero_result_searches_in_window,
                       coalesce(count(distinct case when searched_at >= :weekAgo then client_fingerprint end), 0) as unique_fingerprints_in_window,
                       coalesce(avg(case when searched_at >= :weekAgo then result_count end), 0) as avg_result_count_in_window
                  from search_logs
                """,
                new MapSqlParameterSource()
                        .addValue("dayAgo", dayAgo)
                        .addValue("weekAgo", weekAgo),
                (rs, rowNum) -> new SearchSummaryRow(
                        rs.getLong("searches_last_24h"),
                        rs.getLong("searches_in_window"),
                        rs.getLong("zero_result_searches_in_window"),
                        rs.getLong("unique_fingerprints_in_window"),
                        rs.getBigDecimal("avg_result_count_in_window")
                )
        );
    }

    public SearchTrendRow fetchSearchTrend(LocalDateTime windowAgo) {
        return jdbcTemplate.queryForObject("""
                select coalesce(sum(case when searched_at >= :windowAgo then 1 else 0 end), 0) as searches,
                       coalesce(sum(case when searched_at >= :windowAgo and result_count = 0 then 1 else 0 end), 0) as zero_result_searches
                  from search_logs
                """,
                new MapSqlParameterSource("windowAgo", windowAgo),
                (rs, rowNum) -> new SearchTrendRow(
                        rs.getLong("searches"),
                        rs.getLong("zero_result_searches")
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

    public List<SearchKeywordSnapshotRow> fetchTopZeroResultSearchKeywords(LocalDateTime weekAgo) {
        return fetchTopZeroResultSearchKeywords(weekAgo, DEFAULT_TOP_KEYWORD_LIMIT);
    }

    public List<SearchKeywordSnapshotRow> fetchTopZeroResultSearchKeywords(LocalDateTime weekAgo, int limit) {
        return jdbcTemplate.query("""
                select keyword,
                       count(*) as search_count
                  from search_logs
                 where searched_at >= :weekAgo
                   and result_count = 0
                   and keyword is not null
                   and trim(keyword) <> ''
              group by keyword
              order by search_count desc, keyword asc
                 limit %d
                """.formatted(limit),
                new MapSqlParameterSource("weekAgo", weekAgo),
                (rs, rowNum) -> new SearchKeywordSnapshotRow(
                        rs.getString("keyword"),
                        rs.getLong("search_count")
                )
        );
    }

    public List<SearchRegionSnapshotRow> fetchTopZeroResultRegions(LocalDateTime windowAgo, int limit) {
        return jdbcTemplate.query("""
                select sido,
                       sgg,
                       count(*) as search_count
                  from search_logs
                 where searched_at >= :windowAgo
                   and result_count = 0
                   and (sido is not null or sgg is not null)
              group by sido, sgg
              order by search_count desc,
                       coalesce(sido, '') asc,
                       coalesce(sgg, '') asc
                 limit %d
                """.formatted(limit),
                new MapSqlParameterSource("windowAgo", windowAgo),
                (rs, rowNum) -> new SearchRegionSnapshotRow(
                        rs.getString("sido"),
                        rs.getString("sgg"),
                        rs.getLong("search_count")
                )
        );
    }

    public List<SearchFilterPatternSnapshotRow> fetchTopZeroResultFilterPatterns(LocalDateTime windowAgo, int limit) {
        return jdbcTemplate.query("""
                select status_filter,
                       category,
                       source_type,
                       online_apply,
                       include_closed,
                       sort_key,
                       count(*) as search_count
                  from search_logs
                 where searched_at >= :windowAgo
                   and result_count = 0
              group by status_filter, category, source_type, online_apply, include_closed, sort_key
              order by search_count desc,
                       coalesce(status_filter, '') asc,
                       coalesce(category, '') asc,
                       coalesce(source_type, '') asc,
                       include_closed asc,
                       coalesce(sort_key, '') asc
                 limit %d
                """.formatted(limit),
                new MapSqlParameterSource("windowAgo", windowAgo),
                (rs, rowNum) -> new SearchFilterPatternSnapshotRow(
                        rs.getString("status_filter"),
                        rs.getString("category"),
                        rs.getString("source_type"),
                        getNullableBoolean(rs, "online_apply"),
                        rs.getBoolean("include_closed"),
                        rs.getString("sort_key"),
                        rs.getLong("search_count")
                )
        );
    }

    public List<SearchFailureSampleRow> fetchRecentZeroResultSearchSamples(LocalDateTime windowAgo, int limit) {
        return jdbcTemplate.query("""
                select keyword,
                       sido,
                       sgg,
                       status_filter,
                       category,
                       source_type,
                       online_apply,
                       include_closed,
                       sort_key,
                       searched_at
                  from search_logs
                 where searched_at >= :windowAgo
                   and result_count = 0
              order by searched_at desc, id desc
                 limit %d
                """.formatted(limit),
                new MapSqlParameterSource("windowAgo", windowAgo),
                (rs, rowNum) -> new SearchFailureSampleRow(
                        rs.getString("keyword"),
                        rs.getString("sido"),
                        rs.getString("sgg"),
                        rs.getString("status_filter"),
                        rs.getString("category"),
                        rs.getString("source_type"),
                        getNullableBoolean(rs, "online_apply"),
                        rs.getBoolean("include_closed"),
                        rs.getString("sort_key"),
                        getLocalDateTime(rs, "searched_at")
                )
        );
    }

    public List<SearchRetryGroupRow> fetchZeroResultRetryGroups(LocalDateTime windowAgo, int limit) {
        return jdbcTemplate.query("""
                select case
                           when user_key is not null and trim(user_key) <> '' then 'USER_KEY'
                           else 'FINGERPRINT'
                       end as actor_type,
                       coalesce(nullif(trim(user_key), ''), client_fingerprint) as actor_key,
                       keyword,
                       sido,
                       sgg,
                       status_filter,
                       category,
                       source_type,
                       online_apply,
                       include_closed,
                       sort_key,
                       count(*) as retry_count,
                       min(searched_at) as first_searched_at,
                       max(searched_at) as latest_searched_at
                  from search_logs
                 where searched_at >= :windowAgo
                   and result_count = 0
              group by actor_type,
                       actor_key,
                       keyword,
                       sido,
                       sgg,
                       status_filter,
                       category,
                       source_type,
                       online_apply,
                       include_closed,
                       sort_key
                having count(*) > 1
              order by retry_count desc,
                       latest_searched_at desc,
                       actor_type asc,
                       actor_key asc
                 limit %d
                """.formatted(limit),
                new MapSqlParameterSource("windowAgo", windowAgo),
                (rs, rowNum) -> new SearchRetryGroupRow(
                        rs.getString("actor_type"),
                        rs.getString("actor_key"),
                        rs.getString("keyword"),
                        rs.getString("sido"),
                        rs.getString("sgg"),
                        rs.getString("status_filter"),
                        rs.getString("category"),
                        rs.getString("source_type"),
                        getNullableBoolean(rs, "online_apply"),
                        rs.getBoolean("include_closed"),
                        rs.getString("sort_key"),
                        rs.getLong("retry_count"),
                        getLocalDateTime(rs, "first_searched_at"),
                        getLocalDateTime(rs, "latest_searched_at")
                )
        );
    }

    private LocalDateTime getLocalDateTime(ResultSet rs, String columnName) throws SQLException {
        java.sql.Timestamp timestamp = rs.getTimestamp(columnName);
        return timestamp != null ? timestamp.toLocalDateTime() : null;
    }

    private Boolean getNullableBoolean(ResultSet rs, String columnName) throws SQLException {
        boolean value = rs.getBoolean(columnName);
        return rs.wasNull() ? null : value;
    }

    public record CollectSummaryRow(
            long runningJobs,
            long successJobsLast24h,
            long partialSuccessJobsLast24h,
            long failedJobsLast24h
    ) {
    }

    public record CollectFailureSummaryRow(
            long totalFailedJobs,
            long totalPartialSuccessJobs
    ) {
    }

    public record CollectTrendRow(
            long successJobs,
            long partialSuccessJobs,
            long failedJobs
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

    public record CollectFailureJobBreakdownRow(
            String jobName,
            long failedCount,
            long partialSuccessCount,
            LocalDateTime latestStartedAt
    ) {
    }

    public record CollectFailureErrorCodeBreakdownRow(
            String errorCode,
            long failedCount
    ) {
    }

    public record CollectFailureSampleRow(
            String jobName,
            String status,
            String errorCode,
            String errorMessage,
            LocalDateTime startedAt,
            LocalDateTime finishedAt,
            int requestedCount,
            int savedCount,
            int failedCount
    ) {
    }

    public record CollectJobRunRow(
            String jobName,
            String status,
            LocalDateTime startedAt
    ) {
    }

    public record RecommendationSummaryRow(
            long totalLogs,
            long sentLast24h,
            long sentInWindow,
            long clickedInWindow,
            long fallbackInWindow,
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

    public record RecommendationTrendRow(
            long sentCount,
            long clickedCount,
            long fallbackCount
    ) {
    }

    public record RecommendationSourceBreakdownRow(
            String sourceType,
            long sentCount,
            long clickedCount,
            long fallbackCount
    ) {
    }

    public record RecommendationCategoryBreakdownRow(
            String category,
            long sentCount,
            long clickedCount,
            long fallbackCount
    ) {
    }

    public record RecommendationWeightBreakdownRow(
            String weightKey,
            BigDecimal ruleWeight,
            BigDecimal aiWeight,
            long sentCount,
            long clickedCount,
            long fallbackCount
    ) {
    }

    public record RecommendationSampleRow(
            Long logId,
            Long serviceId,
            String title,
            String sourceType,
            String category,
            BigDecimal finalScore,
            boolean fallback,
            boolean clicked,
            LocalDateTime sentAt,
            LocalDateTime clickedAt
    ) {
    }

    public record RecommendationRepeatExposureGroupRow(
            String userKey,
            Long serviceId,
            String title,
            String sourceType,
            String category,
            long exposureCount,
            long clickedCount,
            long fallbackCount,
            LocalDateTime firstSentAt,
            LocalDateTime latestSentAt,
            LocalDateTime latestClickedAt
    ) {
    }

    public record NotificationSummaryRow(
            long sentLast24h,
            long failedLast24h,
            long sentInWindow,
            long failedInWindow
    ) {
    }

    public record SearchSummaryRow(
            long searchesLast24h,
            long searchesInWindow,
            long zeroResultSearchesInWindow,
            long uniqueFingerprintsInWindow,
            BigDecimal averageResultCountInWindow
    ) {
    }

    public record SearchTrendRow(
            long searches,
            long zeroResultSearches
    ) {
    }

    public record SearchKeywordSnapshotRow(
            String keyword,
            long searchCount
    ) {
    }

    public record SearchRegionSnapshotRow(
            String sido,
            String sgg,
            long searchCount
    ) {
    }

    public record SearchFilterPatternSnapshotRow(
            String statusFilter,
            String category,
            String sourceType,
            Boolean onlineApply,
            boolean includeClosed,
            String sortKey,
            long searchCount
    ) {
    }

    public record SearchFailureSampleRow(
            String keyword,
            String sido,
            String sgg,
            String statusFilter,
            String category,
            String sourceType,
            Boolean onlineApply,
            boolean includeClosed,
            String sortKey,
            LocalDateTime searchedAt
    ) {
    }

    public record SearchRetryGroupRow(
            String actorType,
            String actorKey,
            String keyword,
            String sido,
            String sgg,
            String statusFilter,
            String category,
            String sourceType,
            Boolean onlineApply,
            boolean includeClosed,
            String sortKey,
            long retryCount,
            LocalDateTime firstSearchedAt,
            LocalDateTime latestSearchedAt
    ) {
    }
}
