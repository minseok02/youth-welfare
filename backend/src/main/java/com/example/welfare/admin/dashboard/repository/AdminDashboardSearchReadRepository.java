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
public class AdminDashboardSearchReadRepository {

    private static final int DEFAULT_TOP_KEYWORD_LIMIT = 5;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public AdminDashboardReadRows.SearchSummaryRow fetchSearchSummary(LocalDateTime dayAgo, LocalDateTime weekAgo) {
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
                (rs, rowNum) -> new AdminDashboardReadRows.SearchSummaryRow(
                        rs.getLong("searches_last_24h"),
                        rs.getLong("searches_in_window"),
                        rs.getLong("zero_result_searches_in_window"),
                        rs.getLong("unique_fingerprints_in_window"),
                        rs.getBigDecimal("avg_result_count_in_window")
                )
        );
    }

    public AdminDashboardReadRows.SearchTrendRow fetchSearchTrend(LocalDateTime windowAgo) {
        return jdbcTemplate.queryForObject("""
                select coalesce(sum(case when searched_at >= :windowAgo then 1 else 0 end), 0) as searches,
                       coalesce(sum(case when searched_at >= :windowAgo and result_count = 0 then 1 else 0 end), 0) as zero_result_searches
                  from search_logs
                """,
                new MapSqlParameterSource("windowAgo", windowAgo),
                (rs, rowNum) -> new AdminDashboardReadRows.SearchTrendRow(
                        rs.getLong("searches"),
                        rs.getLong("zero_result_searches")
                )
        );
    }

    public List<AdminDashboardReadRows.SearchKeywordSnapshotRow> fetchTopSearchKeywords(LocalDateTime weekAgo) {
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
                (rs, rowNum) -> new AdminDashboardReadRows.SearchKeywordSnapshotRow(
                        rs.getString("keyword"),
                        rs.getLong("search_count")
                )
        );
    }

    public List<AdminDashboardReadRows.SearchKeywordSnapshotRow> fetchTopZeroResultSearchKeywords(LocalDateTime weekAgo) {
        return fetchTopZeroResultSearchKeywords(weekAgo, DEFAULT_TOP_KEYWORD_LIMIT);
    }

    public List<AdminDashboardReadRows.SearchKeywordSnapshotRow> fetchTopZeroResultSearchKeywords(LocalDateTime weekAgo, int limit) {
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
                (rs, rowNum) -> new AdminDashboardReadRows.SearchKeywordSnapshotRow(
                        rs.getString("keyword"),
                        rs.getLong("search_count")
                )
        );
    }

    public List<AdminDashboardReadRows.SearchRegionSnapshotRow> fetchTopZeroResultRegions(LocalDateTime windowAgo, int limit) {
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
                (rs, rowNum) -> new AdminDashboardReadRows.SearchRegionSnapshotRow(
                        rs.getString("sido"),
                        rs.getString("sgg"),
                        rs.getLong("search_count")
                )
        );
    }

    public List<AdminDashboardReadRows.SearchFilterPatternSnapshotRow> fetchTopZeroResultFilterPatterns(LocalDateTime windowAgo, int limit) {
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
                (rs, rowNum) -> new AdminDashboardReadRows.SearchFilterPatternSnapshotRow(
                        rs.getString("status_filter"),
                        rs.getString("category"),
                        rs.getString("source_type"),
                        AdminDashboardJdbcSupport.getNullableBoolean(rs, "online_apply"),
                        rs.getBoolean("include_closed"),
                        rs.getString("sort_key"),
                        rs.getLong("search_count")
                )
        );
    }

    public List<AdminDashboardReadRows.SearchFailureSampleRow> fetchRecentZeroResultSearchSamples(LocalDateTime windowAgo, int limit) {
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
                (rs, rowNum) -> new AdminDashboardReadRows.SearchFailureSampleRow(
                        rs.getString("keyword"),
                        rs.getString("sido"),
                        rs.getString("sgg"),
                        rs.getString("status_filter"),
                        rs.getString("category"),
                        rs.getString("source_type"),
                        AdminDashboardJdbcSupport.getNullableBoolean(rs, "online_apply"),
                        rs.getBoolean("include_closed"),
                        rs.getString("sort_key"),
                        AdminDashboardJdbcSupport.getLocalDateTime(rs, "searched_at")
                )
        );
    }

    public List<AdminDashboardReadRows.SearchRetryGroupRow> fetchZeroResultRetryGroups(LocalDateTime windowAgo, int limit) {
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
                (rs, rowNum) -> new AdminDashboardReadRows.SearchRetryGroupRow(
                        rs.getString("actor_type"),
                        rs.getString("actor_key"),
                        rs.getString("keyword"),
                        rs.getString("sido"),
                        rs.getString("sgg"),
                        rs.getString("status_filter"),
                        rs.getString("category"),
                        rs.getString("source_type"),
                        AdminDashboardJdbcSupport.getNullableBoolean(rs, "online_apply"),
                        rs.getBoolean("include_closed"),
                        rs.getString("sort_key"),
                        rs.getLong("retry_count"),
                        AdminDashboardJdbcSupport.getLocalDateTime(rs, "first_searched_at"),
                        AdminDashboardJdbcSupport.getLocalDateTime(rs, "latest_searched_at")
                )
        );
    }

    public List<AdminDashboardReadRows.RecoveredSearchGroupRow> fetchRecoveredSearchGroups(LocalDateTime windowAgo, int limit) {
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
                       coalesce(sum(case when result_count = 0 then 1 else 0 end), 0) as zero_result_count,
                       coalesce(sum(case when result_count > 0 then 1 else 0 end), 0) as recovered_result_count,
                       max(case when result_count > 0 then searched_at end) as latest_recovered_at
                  from search_logs
                 where searched_at >= :windowAgo
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
                having zero_result_count > 0
                   and recovered_result_count > 0
              order by zero_result_count desc,
                       recovered_result_count desc,
                       latest_recovered_at desc,
                       actor_type asc,
                       actor_key asc
                 limit %d
                """.formatted(limit),
                new MapSqlParameterSource("windowAgo", windowAgo),
                (rs, rowNum) -> new AdminDashboardReadRows.RecoveredSearchGroupRow(
                        rs.getString("actor_type"),
                        rs.getString("actor_key"),
                        rs.getString("keyword"),
                        rs.getString("sido"),
                        rs.getString("sgg"),
                        rs.getString("status_filter"),
                        rs.getString("category"),
                        rs.getString("source_type"),
                        AdminDashboardJdbcSupport.getNullableBoolean(rs, "online_apply"),
                        rs.getBoolean("include_closed"),
                        rs.getString("sort_key"),
                        rs.getLong("zero_result_count"),
                        rs.getLong("recovered_result_count"),
                        AdminDashboardJdbcSupport.getLocalDateTime(rs, "latest_recovered_at")
                )
        );
    }
}
