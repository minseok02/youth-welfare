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
public class AdminDashboardRecommendationReadRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    private static final String RECOMMENDATION_USER_ORIGIN_SQL = "coalesce(nullif(u.account_origin, ''), 'REAL_USER')";

    public AdminDashboardReadRows.RecommendationSummaryRow fetchRecommendationSummary(LocalDateTime dayAgo, LocalDateTime weekAgo) {
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
                (rs, rowNum) -> new AdminDashboardReadRows.RecommendationSummaryRow(
                        rs.getLong("total_logs"),
                        rs.getLong("sent_last_24h"),
                        rs.getLong("sent_in_window"),
                        rs.getLong("clicked_in_window"),
                        rs.getLong("fallback_in_window"),
                        AdminDashboardJdbcSupport.getLocalDateTime(rs, "latest_clicked_at")
                )
        );
    }

    public AdminDashboardReadRows.RecommendationTrendRow fetchRecommendationTrend(LocalDateTime windowAgo) {
        return jdbcTemplate.queryForObject("""
                select coalesce(sum(case when sent_at >= :windowAgo then 1 else 0 end), 0) as sent_count,
                       coalesce(sum(case when is_clicked = true and clicked_at >= :windowAgo then 1 else 0 end), 0) as clicked_count,
                       coalesce(sum(case when is_fallback = true and sent_at >= :windowAgo then 1 else 0 end), 0) as fallback_count
                  from recommendation_logs
                """,
                new MapSqlParameterSource("windowAgo", windowAgo),
                (rs, rowNum) -> new AdminDashboardReadRows.RecommendationTrendRow(
                        rs.getLong("sent_count"),
                        rs.getLong("clicked_count"),
                        rs.getLong("fallback_count")
                )
        );
    }

    public AdminDashboardReadRows.RecommendationTrafficMixRow fetchRecommendationTrafficMix(LocalDateTime windowAgo) {
        return jdbcTemplate.queryForObject("""
                with cohorted_logs as (
                    select rl.user_key,
                           rl.sent_at,
                           rl.clicked_at,
                           rl.is_clicked,
                           %s as user_origin
                      from recommendation_logs rl
                      left join users u on u.user_key = rl.user_key
                )
                select coalesce(sum(case
                                       when sent_at >= :windowAgo and user_origin = 'EXAMPLE_SMOKE'
                                           then 1
                                       else 0
                                   end), 0) as example_logs_in_window,
                       coalesce(sum(case
                                       when sent_at >= :windowAgo and user_origin = 'BOUNDED_LOCAL'
                                           then 1
                                       else 0
                                   end), 0) as bounded_local_logs_in_window,
                       coalesce(sum(case
                                       when sent_at >= :windowAgo and user_origin = 'LOCAL_REAL_NON_EXAMPLE_SEED'
                                           then 1
                                       else 0
                                   end), 0) as local_real_non_example_seed_logs_in_window,
                       coalesce(sum(case
                                       when sent_at >= :windowAgo and user_origin = 'REAL_USER'
                                           then 1
                                       else 0
                                   end), 0) as real_user_logs_in_window,
                       coalesce(sum(case
                                       when sent_at >= :windowAgo and user_origin in ('LOCAL_REAL_NON_EXAMPLE_SEED', 'REAL_USER')
                                           then 1
                                       else 0
                                   end), 0) as real_non_example_logs_in_window,
                       count(distinct case
                                          when sent_at >= :windowAgo and user_origin = 'EXAMPLE_SMOKE'
                                              then user_key
                                       end) as example_users_in_window,
                       count(distinct case
                                          when sent_at >= :windowAgo and user_origin = 'BOUNDED_LOCAL'
                                              then user_key
                                       end) as bounded_local_users_in_window,
                       count(distinct case
                                          when sent_at >= :windowAgo and user_origin = 'LOCAL_REAL_NON_EXAMPLE_SEED'
                                              then user_key
                                       end) as local_real_non_example_seed_users_in_window,
                       count(distinct case
                                          when sent_at >= :windowAgo and user_origin = 'REAL_USER'
                                              then user_key
                                       end) as real_user_users_in_window,
                       count(distinct case
                                          when sent_at >= :windowAgo and user_origin in ('LOCAL_REAL_NON_EXAMPLE_SEED', 'REAL_USER')
                                              then user_key
                                       end) as real_non_example_users_in_window,
                       count(distinct case
                                          when is_clicked = true and clicked_at >= :windowAgo and user_origin = 'EXAMPLE_SMOKE'
                                              then user_key
                                       end) as example_clicked_users_in_window,
                       count(distinct case
                                          when is_clicked = true and clicked_at >= :windowAgo and user_origin = 'BOUNDED_LOCAL'
                                              then user_key
                                       end) as bounded_local_clicked_users_in_window,
                       count(distinct case
                                          when is_clicked = true and clicked_at >= :windowAgo and user_origin = 'LOCAL_REAL_NON_EXAMPLE_SEED'
                                              then user_key
                                       end) as local_real_non_example_seed_clicked_users_in_window,
                       count(distinct case
                                          when is_clicked = true and clicked_at >= :windowAgo and user_origin = 'REAL_USER'
                                              then user_key
                                       end) as real_user_clicked_users_in_window,
                       count(distinct case
                                          when is_clicked = true and clicked_at >= :windowAgo and user_origin in ('LOCAL_REAL_NON_EXAMPLE_SEED', 'REAL_USER')
                                              then user_key
                                       end) as real_non_example_clicked_users_in_window
                  from cohorted_logs
                """.formatted(RECOMMENDATION_USER_ORIGIN_SQL),
                new MapSqlParameterSource("windowAgo", windowAgo),
                (rs, rowNum) -> new AdminDashboardReadRows.RecommendationTrafficMixRow(
                        rs.getLong("example_logs_in_window"),
                        rs.getLong("bounded_local_logs_in_window"),
                        rs.getLong("local_real_non_example_seed_logs_in_window"),
                        rs.getLong("real_user_logs_in_window"),
                        rs.getLong("real_non_example_logs_in_window"),
                        rs.getLong("example_users_in_window"),
                        rs.getLong("bounded_local_users_in_window"),
                        rs.getLong("local_real_non_example_seed_users_in_window"),
                        rs.getLong("real_user_users_in_window"),
                        rs.getLong("real_non_example_users_in_window"),
                        rs.getLong("example_clicked_users_in_window"),
                        rs.getLong("bounded_local_clicked_users_in_window"),
                        rs.getLong("local_real_non_example_seed_clicked_users_in_window"),
                        rs.getLong("real_user_clicked_users_in_window"),
                        rs.getLong("real_non_example_clicked_users_in_window")
                )
        );
    }

    public List<AdminDashboardReadRows.RecommendationWeightSnapshotRow> fetchRecommendationWeightBuckets(LocalDateTime weekAgo) {
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
                (rs, rowNum) -> new AdminDashboardReadRows.RecommendationWeightSnapshotRow(
                        rs.getString("weight_key"),
                        rs.getBigDecimal("rule_weight_used"),
                        rs.getBigDecimal("ai_weight_used"),
                        rs.getLong("log_count")
                )
        );
    }

    public List<AdminDashboardReadRows.RecommendationSourceBreakdownRow> fetchRecommendationSourceBreakdowns(LocalDateTime windowAgo, int limit) {
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
                (rs, rowNum) -> new AdminDashboardReadRows.RecommendationSourceBreakdownRow(
                        rs.getString("source_type"),
                        rs.getLong("sent_count"),
                        rs.getLong("clicked_count"),
                        rs.getLong("fallback_count")
                )
        );
    }

    public List<AdminDashboardReadRows.RecommendationCategoryBreakdownRow> fetchRecommendationCategoryBreakdowns(LocalDateTime windowAgo, int limit) {
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
                (rs, rowNum) -> new AdminDashboardReadRows.RecommendationCategoryBreakdownRow(
                        rs.getString("unified_category"),
                        rs.getLong("sent_count"),
                        rs.getLong("clicked_count"),
                        rs.getLong("fallback_count")
                )
        );
    }

    public List<AdminDashboardReadRows.RecommendationWeightBreakdownRow> fetchRecommendationWeightBreakdowns(LocalDateTime windowAgo, int limit) {
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
                (rs, rowNum) -> new AdminDashboardReadRows.RecommendationWeightBreakdownRow(
                        rs.getString("weight_key"),
                        rs.getBigDecimal("rule_weight_used"),
                        rs.getBigDecimal("ai_weight_used"),
                        rs.getLong("sent_count"),
                        rs.getLong("clicked_count"),
                        rs.getLong("fallback_count")
                )
        );
    }

    public List<AdminDashboardReadRows.RecommendationSampleRow> fetchRecentFallbackRecommendationSamples(LocalDateTime windowAgo, int limit) {
        return jdbcTemplate.query("""
                select rl.id as log_id,
                       ws.id as service_id,
                       ws.title,
                       ws.source_type,
                       ws.unified_category,
                       %s as user_cohort,
                       rl.final_score,
                       rl.is_fallback,
                       rl.is_clicked,
                       rl.sent_at,
                       rl.clicked_at
                  from recommendation_logs rl
                  join welfare_services ws on ws.id = rl.service_id
                  left join users u on u.user_key = rl.user_key
                 where rl.sent_at >= :windowAgo
                   and rl.is_fallback = true
              order by rl.sent_at desc, rl.id desc
                 limit %d
                """.formatted(RECOMMENDATION_USER_ORIGIN_SQL, limit),
                new MapSqlParameterSource("windowAgo", windowAgo),
                (rs, rowNum) -> new AdminDashboardReadRows.RecommendationSampleRow(
                        rs.getLong("log_id"),
                        rs.getLong("service_id"),
                        rs.getString("title"),
                        rs.getString("source_type"),
                        rs.getString("unified_category"),
                        rs.getString("user_cohort"),
                        rs.getBigDecimal("final_score"),
                        rs.getBoolean("is_fallback"),
                        rs.getBoolean("is_clicked"),
                        AdminDashboardJdbcSupport.getLocalDateTime(rs, "sent_at"),
                        AdminDashboardJdbcSupport.getLocalDateTime(rs, "clicked_at")
                )
        );
    }

    public List<AdminDashboardReadRows.RecommendationSampleRow> fetchRecentClickedRecommendationSamples(LocalDateTime windowAgo, int limit) {
        return jdbcTemplate.query("""
                select rl.id as log_id,
                       ws.id as service_id,
                       ws.title,
                       ws.source_type,
                       ws.unified_category,
                       %s as user_cohort,
                       rl.final_score,
                       rl.is_fallback,
                       rl.is_clicked,
                       rl.sent_at,
                       rl.clicked_at
                  from recommendation_logs rl
                  join welfare_services ws on ws.id = rl.service_id
                  left join users u on u.user_key = rl.user_key
                 where rl.sent_at >= :windowAgo
                   and rl.is_clicked = true
              order by rl.clicked_at desc, rl.id desc
                 limit %d
                """.formatted(RECOMMENDATION_USER_ORIGIN_SQL, limit),
                new MapSqlParameterSource("windowAgo", windowAgo),
                (rs, rowNum) -> new AdminDashboardReadRows.RecommendationSampleRow(
                        rs.getLong("log_id"),
                        rs.getLong("service_id"),
                        rs.getString("title"),
                        rs.getString("source_type"),
                        rs.getString("unified_category"),
                        rs.getString("user_cohort"),
                        rs.getBigDecimal("final_score"),
                        rs.getBoolean("is_fallback"),
                        rs.getBoolean("is_clicked"),
                        AdminDashboardJdbcSupport.getLocalDateTime(rs, "sent_at"),
                        AdminDashboardJdbcSupport.getLocalDateTime(rs, "clicked_at")
                )
        );
    }

    public List<AdminDashboardReadRows.RecommendationRepeatExposureGroupRow> fetchRecommendationRepeatExposureGroups(LocalDateTime windowAgo, int limit) {
        return jdbcTemplate.query("""
                select rl.user_key,
                       ws.id as service_id,
                       ws.title,
                       ws.source_type,
                       ws.unified_category,
                       %s as user_cohort,
                       count(*) as exposure_count,
                       coalesce(sum(case when rl.is_clicked = true then 1 else 0 end), 0) as clicked_count,
                       coalesce(sum(case when rl.is_fallback = true then 1 else 0 end), 0) as fallback_count,
                       min(rl.sent_at) as first_sent_at,
                       max(rl.sent_at) as latest_sent_at,
                       max(rl.clicked_at) as latest_clicked_at
                  from recommendation_logs rl
                  join welfare_services ws on ws.id = rl.service_id
                  left join users u on u.user_key = rl.user_key
                 where rl.sent_at >= :windowAgo
              group by rl.user_key, ws.id, ws.title, ws.source_type, ws.unified_category, %s
                having count(*) > 1
              order by exposure_count desc,
                       latest_sent_at desc,
                       rl.user_key asc,
                       ws.id asc
                 limit %d
                """.formatted(RECOMMENDATION_USER_ORIGIN_SQL, RECOMMENDATION_USER_ORIGIN_SQL, limit),
                new MapSqlParameterSource("windowAgo", windowAgo),
                (rs, rowNum) -> new AdminDashboardReadRows.RecommendationRepeatExposureGroupRow(
                        rs.getString("user_key"),
                        rs.getLong("service_id"),
                        rs.getString("title"),
                        rs.getString("source_type"),
                        rs.getString("unified_category"),
                        rs.getString("user_cohort"),
                        rs.getLong("exposure_count"),
                        rs.getLong("clicked_count"),
                        rs.getLong("fallback_count"),
                        AdminDashboardJdbcSupport.getLocalDateTime(rs, "first_sent_at"),
                        AdminDashboardJdbcSupport.getLocalDateTime(rs, "latest_sent_at"),
                        AdminDashboardJdbcSupport.getLocalDateTime(rs, "latest_clicked_at")
                )
        );
    }
}
