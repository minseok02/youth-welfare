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
    private static final String YOUTH_FACET_ORDER_CASE = """
            case facet_key
                when 'YOUTH_INCOME_CONDITION_TYPE' then 1
                when 'YOUTH_EMPLOYMENT_REQUIREMENT' then 2
                when 'YOUTH_EDUCATION_REQUIREMENT' then 3
                when 'YOUTH_SPECIAL_REQUIREMENT' then 4
                when 'YOUTH_MARITAL_STATUS' then 5
                else 99
            end
            """;
    private static final String GOV24_FACET_ORDER_CASE = """
            case facet_key
                when 'GOV24_USER_TYPE_TOKEN' then 1
                when 'GOV24_BENEFIT_TYPE_TOKEN' then 2
                else 99
            end
            """;

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

    public AdminDashboardReadRows.RecommendationConcentrationRow fetchRecommendationConcentration() {
        return jdbcTemplate.queryForObject("""
                with latest as (
                    select user_key, max(recommended_at) as recommended_at
                      from user_recommendations
                     group by user_key
                ),
                base_rows as (
                    select ur.id as recommendation_id,
                           ur.user_key,
                           ur.service_id,
                           ur.final_score,
                           %1$s as user_origin,
                           case
                               when %1$s = 'EXAMPLE_SMOKE' then 'EXAMPLE_SMOKE'
                               when %1$s = 'BOUNDED_LOCAL' then 'BOUNDED_LOCAL'
                               when %1$s = 'LOCAL_REAL_NON_EXAMPLE_SEED' then 'LOCAL_REAL_NON_EXAMPLE_SEED'
                               else 'REAL_USER'
                           end as user_cohort
                      from user_recommendations ur
                      join latest l
                        on l.user_key = ur.user_key
                       and l.recommended_at = ur.recommended_at
                      join users u
                        on u.user_key = ur.user_key
                ),
                priority_profiles as (
                    select up.user_key,
                           string_agg(po.code, '>' order by up.priority_rank) as profile
                      from user_priorities up
                      join priority_options po
                        on po.id = up.priority_option_id
                     group by up.user_key
                ),
                priority_states as (
                    select distinct br.user_key,
                           case when pp.user_key is null then 'NO_PRIORITY' else 'HAS_PRIORITY' end as priority_state
                      from base_rows br
                      left join priority_profiles pp
                        on pp.user_key = br.user_key
                ),
                top1 as (
                    select *
                      from (
                        select br.user_key,
                               br.service_id,
                               br.final_score,
                               row_number() over (
                                   partition by br.user_key
                                   order by br.final_score desc, br.recommendation_id desc
                               ) as rn
                          from base_rows br
                      ) ranked
                     where rn = 1
                ),
                top1_summary as (
                    select t.service_id,
                           ws.title,
                           ws.source_type,
                           coalesce(ws.unified_category, '기타') as category,
                           count(*) as users_as_top1,
                           count(*) filter (where br.user_cohort = 'EXAMPLE_SMOKE') as example_users,
                           count(*) filter (where br.user_cohort = 'BOUNDED_LOCAL') as bounded_local_users,
                           count(*) filter (where br.user_cohort = 'LOCAL_REAL_NON_EXAMPLE_SEED') as local_real_non_example_seed_users,
                           count(*) filter (where br.user_cohort = 'REAL_USER') as real_user_users,
                           count(*) filter (where br.user_cohort in ('LOCAL_REAL_NON_EXAMPLE_SEED', 'REAL_USER')) as real_non_example_users
                      from top1 t
                      join base_rows br
                        on br.user_key = t.user_key
                       and br.service_id = t.service_id
                      join welfare_services ws
                        on ws.id = t.service_id
                     group by t.service_id, ws.title, ws.source_type, coalesce(ws.unified_category, '기타')
                ),
                top1_leader as (
                    select service_id,
                           title,
                           source_type,
                           category,
                           users_as_top1,
                           example_users,
                           bounded_local_users,
                           local_real_non_example_seed_users,
                           real_user_users,
                           real_non_example_users,
                           round(users_as_top1 * 100.0 / nullif((select count(*) from top1), 0), 2) as share_pct
                      from top1_summary
                     order by users_as_top1 desc, service_id
                     limit 1
                ),
                mix as (
                    select count(*) as latest_batch_rows,
                           count(distinct user_key) as latest_batch_users,
                           count(distinct service_id) as latest_batch_distinct_services,
                           count(distinct user_key) filter (where user_cohort = 'EXAMPLE_SMOKE') as example_users,
                           count(distinct user_key) filter (where user_cohort = 'BOUNDED_LOCAL') as bounded_local_users,
                           count(distinct user_key) filter (where user_cohort = 'LOCAL_REAL_NON_EXAMPLE_SEED') as local_real_non_example_seed_users,
                           count(distinct user_key) filter (where user_cohort = 'REAL_USER') as real_user_users
                      from base_rows
                ),
                base as (
                    select (select count(*) from top1) as top1_users,
                           (select users_as_top1 from top1_leader) as top1_leader_users,
                           (select count(*) from priority_states where priority_state = 'HAS_PRIORITY') as priority_users,
                           (select count(*) from priority_states where priority_state = 'NO_PRIORITY') as no_priority_users
                )
                select mix.latest_batch_rows,
                       mix.latest_batch_users,
                       mix.latest_batch_distinct_services,
                       top1_leader.service_id as top1_leader_service_id,
                       top1_leader.title as top1_leader_title,
                       top1_leader.source_type as top1_leader_source,
                       top1_leader.category as top1_leader_category,
                       coalesce(top1_leader.users_as_top1, 0) as top1_leader_users,
                       coalesce(top1_leader.share_pct, 0) as top1_leader_share_pct,
                       coalesce(top1_leader.example_users, 0) as top1_leader_example_users,
                       coalesce(top1_leader.bounded_local_users, 0) as top1_leader_bounded_local_users,
                       coalesce(top1_leader.local_real_non_example_seed_users, 0) as top1_leader_local_real_non_example_seed_users,
                       coalesce(top1_leader.real_user_users, 0) as top1_leader_real_user_users,
                       coalesce(top1_leader.real_non_example_users, 0) as top1_leader_real_non_example_users,
                       case
                           when base.top1_users = 0 then 'DEFERRED_EMPTY_COHORT'
                           when base.top1_leader_users * 100.0 / nullif(base.top1_users, 0) >= 50 then 'CONCENTRATED_TOP1'
                           when base.no_priority_users > base.priority_users * 2 then 'NO_PRIORITY_DOMINANT'
                           else 'BALANCED_ENOUGH_FOR_LOGIC_REVIEW'
                       end as concentration_readiness,
                       case
                           when mix.latest_batch_users = 0 then 'DEFERRED_EMPTY_COHORT'
                           when mix.real_user_users = 0 then 'DEFERRED_NO_REAL_USER_COHORT'
                           when mix.real_user_users < 3 then 'DEFERRED_REAL_USER_SAMPLE_THIN'
                           else 'READY_REAL_USER_COHORT'
                       end as real_user_cohort_gate,
                       case
                           when mix.latest_batch_users = 0 then 'EMPTY_COHORT'
                           when mix.real_user_users = 0 and mix.local_real_non_example_seed_users = 0 and mix.bounded_local_users = 0
                               then 'SYNTHETIC_ONLY_LATEST_BATCH'
                           when mix.real_user_users = 0 and mix.local_real_non_example_seed_users = 0 and mix.bounded_local_users > 0
                               then 'BOUNDED_LOCAL_WITH_SYNTHETIC_BATCH'
                           when mix.real_user_users = 0 and mix.local_real_non_example_seed_users > 0 and (mix.example_users > 0 or mix.bounded_local_users > 0)
                               then 'LOCAL_REAL_NON_EXAMPLE_SEED_WITH_NON_REAL_BATCH'
                           when mix.real_user_users = 0 and mix.local_real_non_example_seed_users > 0
                               then 'LOCAL_REAL_NON_EXAMPLE_SEED_ONLY_BATCH'
                           when mix.real_user_users > 0 and (mix.example_users > 0 or mix.bounded_local_users > 0 or mix.local_real_non_example_seed_users > 0)
                               then 'MIXED_WITH_NON_REAL_BATCH'
                           else 'REAL_USER_ONLY_BATCH'
                       end as signal_quality
                  from mix
                  cross join base
                  left join top1_leader on true
                """.formatted(RECOMMENDATION_USER_ORIGIN_SQL),
                new MapSqlParameterSource(),
                (rs, rowNum) -> new AdminDashboardReadRows.RecommendationConcentrationRow(
                        rs.getLong("latest_batch_rows"),
                        rs.getLong("latest_batch_users"),
                        rs.getLong("latest_batch_distinct_services"),
                        AdminDashboardJdbcSupport.getLong(rs, "top1_leader_service_id"),
                        rs.getString("top1_leader_title"),
                        rs.getString("top1_leader_source"),
                        rs.getString("top1_leader_category"),
                        rs.getLong("top1_leader_users"),
                        rs.getBigDecimal("top1_leader_share_pct"),
                        rs.getLong("top1_leader_example_users"),
                        rs.getLong("top1_leader_bounded_local_users"),
                        rs.getLong("top1_leader_local_real_non_example_seed_users"),
                        rs.getLong("top1_leader_real_user_users"),
                        rs.getLong("top1_leader_real_non_example_users"),
                        rs.getString("concentration_readiness"),
                        rs.getString("real_user_cohort_gate"),
                        rs.getString("signal_quality")
                )
        );
    }

    public List<AdminDashboardReadRows.RecommendationRepeatedServiceRow> fetchTopRepeatedRecommendationServices(int limit) {
        return jdbcTemplate.query("""
                with latest as (
                    select user_key, max(recommended_at) as recommended_at
                      from user_recommendations
                     group by user_key
                ),
                latest_rows as (
                    select ur.user_key,
                           ur.service_id,
                           %1$s as user_origin
                      from user_recommendations ur
                      join latest l
                        on l.user_key = ur.user_key
                       and l.recommended_at = ur.recommended_at
                      join users u
                        on u.user_key = ur.user_key
                )
                select ws.id as service_id,
                       ws.title,
                       ws.source_type,
                       coalesce(ws.unified_category, '기타') as category,
                       count(*) as row_count,
                       count(distinct lr.user_key) as distinct_users,
                       count(distinct lr.user_key) filter (where lr.user_origin = 'EXAMPLE_SMOKE') as example_users,
                       count(distinct lr.user_key) filter (where lr.user_origin = 'BOUNDED_LOCAL') as bounded_local_users,
                       count(distinct lr.user_key) filter (where lr.user_origin = 'LOCAL_REAL_NON_EXAMPLE_SEED') as local_real_non_example_seed_users,
                       count(distinct lr.user_key) filter (where lr.user_origin = 'REAL_USER') as real_user_users,
                       count(distinct lr.user_key) filter (where lr.user_origin in ('LOCAL_REAL_NON_EXAMPLE_SEED', 'REAL_USER')) as real_non_example_users
                  from latest_rows lr
                  join welfare_services ws on ws.id = lr.service_id
              group by ws.id, ws.title, ws.source_type, coalesce(ws.unified_category, '기타')
              order by row_count desc, service_id asc
                 limit %2$d
                """.formatted(RECOMMENDATION_USER_ORIGIN_SQL, limit),
                new MapSqlParameterSource(),
                (rs, rowNum) -> new AdminDashboardReadRows.RecommendationRepeatedServiceRow(
                        rs.getLong("service_id"),
                        rs.getString("title"),
                        rs.getString("source_type"),
                        rs.getString("category"),
                        rs.getLong("row_count"),
                        rs.getLong("distinct_users"),
                        rs.getLong("example_users"),
                        rs.getLong("bounded_local_users"),
                        rs.getLong("local_real_non_example_seed_users"),
                        rs.getLong("real_user_users"),
                        rs.getLong("real_non_example_users")
                )
        );
    }

    public List<AdminDashboardReadRows.RecommendationTop1ServiceRow> fetchTop1RecommendationServices(int limit) {
        return jdbcTemplate.query("""
                with latest as (
                    select user_key, max(recommended_at) as recommended_at
                      from user_recommendations
                     group by user_key
                ),
                top1 as (
                    select *
                      from (
                        select ur.id,
                               ur.user_key,
                               ur.service_id,
                               ur.final_score,
                               %1$s as user_origin,
                               row_number() over (
                                   partition by ur.user_key
                                   order by ur.final_score desc, ur.id desc
                               ) as rn
                          from user_recommendations ur
                          join latest l
                            on l.user_key = ur.user_key
                           and l.recommended_at = ur.recommended_at
                          join users u
                            on u.user_key = ur.user_key
                      ) ranked
                     where rn = 1
                )
                select ws.id as service_id,
                       ws.title,
                       ws.source_type,
                       coalesce(ws.unified_category, '기타') as category,
                       count(*) as users_as_top1,
                       count(*) filter (where t.user_origin = 'EXAMPLE_SMOKE') as example_users,
                       count(*) filter (where t.user_origin = 'BOUNDED_LOCAL') as bounded_local_users,
                       count(*) filter (where t.user_origin = 'LOCAL_REAL_NON_EXAMPLE_SEED') as local_real_non_example_seed_users,
                       count(*) filter (where t.user_origin = 'REAL_USER') as real_user_users,
                       count(*) filter (where t.user_origin in ('LOCAL_REAL_NON_EXAMPLE_SEED', 'REAL_USER')) as real_non_example_users
                  from top1 t
                  join welfare_services ws on ws.id = t.service_id
              group by ws.id, ws.title, ws.source_type, coalesce(ws.unified_category, '기타')
              order by users_as_top1 desc, service_id asc
                 limit %2$d
                """.formatted(RECOMMENDATION_USER_ORIGIN_SQL, limit),
                new MapSqlParameterSource(),
                (rs, rowNum) -> new AdminDashboardReadRows.RecommendationTop1ServiceRow(
                        rs.getLong("service_id"),
                        rs.getString("title"),
                        rs.getString("source_type"),
                        rs.getString("category"),
                        rs.getLong("users_as_top1"),
                        rs.getLong("example_users"),
                        rs.getLong("bounded_local_users"),
                        rs.getLong("local_real_non_example_seed_users"),
                        rs.getLong("real_user_users"),
                        rs.getLong("real_non_example_users")
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

    public List<AdminDashboardReadRows.RecommendationFacetRow> fetchLatestBatchYouthOfficialFacetRows(int limitPerFacet) {
        return jdbcTemplate.query("""
                with latest as (
                    select user_key, max(recommended_at) as recommended_at
                      from user_recommendations
                     group by user_key
                ),
                latest_rows as (
                    select ur.user_key,
                           ur.service_id
                      from user_recommendations ur
                      join latest l
                        on l.user_key = ur.user_key
                       and l.recommended_at = ur.recommended_at
                ),
                expanded as (
                    select distinct lr.user_key,
                           lr.service_id,
                           sf.fact_code_set_key as facet_key,
                           btrim(token_parts.bucket_label) as bucket_label
                      from latest_rows lr
                      join welfare_services ws
                        on ws.id = lr.service_id
                       and ws.source_type = 'YOUTH'
                      join service_facts sf
                        on sf.service_id = ws.id
                       and sf.fact_code_set_key in (
                           'YOUTH_INCOME_CONDITION_TYPE',
                           'YOUTH_EMPLOYMENT_REQUIREMENT',
                           'YOUTH_EDUCATION_REQUIREMENT',
                           'YOUTH_SPECIAL_REQUIREMENT',
                           'YOUTH_MARITAL_STATUS'
                       )
                     cross join lateral regexp_split_to_table(coalesce(sf.text_value, ''), '\\s*,\\s*') as token_parts(bucket_label)
                     where nullif(btrim(token_parts.bucket_label), '') is not null
                       and btrim(token_parts.bucket_label) not in ('제한없음', '무관')
                ),
                aggregated as (
                    select facet_key,
                           bucket_label,
                           count(*) as row_count,
                           count(distinct service_id) as distinct_services
                      from expanded
                     group by facet_key, bucket_label
                ),
                ranked as (
                    select facet_key,
                           bucket_label,
                           row_count,
                           distinct_services,
                           row_number() over (
                               partition by facet_key
                               order by row_count desc, distinct_services desc, bucket_label asc
                           ) as rn
                      from aggregated
                )
                select facet_key,
                       bucket_label,
                       row_count,
                       distinct_services
                  from ranked
                 where rn <= :limitPerFacet
              order by %s, row_count desc, distinct_services desc, bucket_label asc
                """.formatted(YOUTH_FACET_ORDER_CASE),
                new MapSqlParameterSource("limitPerFacet", limitPerFacet),
                (rs, rowNum) -> new AdminDashboardReadRows.RecommendationFacetRow(
                        rs.getString("facet_key"),
                        rs.getString("bucket_label"),
                        rs.getLong("row_count"),
                        rs.getLong("distinct_services")
                )
        );
    }

    public List<AdminDashboardReadRows.RecommendationFacetRow> fetchLatestBatchGov24FacetRows(int limitPerFacet) {
        return jdbcTemplate.query("""
                with latest as (
                    select user_key, max(recommended_at) as recommended_at
                      from user_recommendations
                     group by user_key
                ),
                latest_rows as (
                    select ur.user_key,
                           ur.service_id
                      from user_recommendations ur
                      join latest l
                        on l.user_key = ur.user_key
                       and l.recommended_at = ur.recommended_at
                ),
                expanded as (
                    select distinct lr.user_key,
                           lr.service_id,
                           tokens.facet_key,
                           tokens.bucket_label
                      from latest_rows lr
                      join welfare_services ws
                        on ws.id = lr.service_id
                       and ws.source_type = 'GOV24'
                      left join service_taxonomies st
                        on st.service_id = ws.id
                     cross join lateral (
                         select 'GOV24_USER_TYPE_TOKEN' as facet_key, btrim(token_parts.bucket_label) as bucket_label
                           from regexp_split_to_table(coalesce(st.gov24_user_type_label, ''), '\\|\\|') as token_parts(bucket_label)
                         union all
                         select 'GOV24_BENEFIT_TYPE_TOKEN' as facet_key, btrim(token_parts.bucket_label) as bucket_label
                           from regexp_split_to_table(coalesce(st.gov24_benefit_type_label, ''), '\\|\\|') as token_parts(bucket_label)
                     ) tokens
                     where nullif(tokens.bucket_label, '') is not null
                ),
                aggregated as (
                    select facet_key,
                           bucket_label,
                           count(*) as row_count,
                           count(distinct service_id) as distinct_services
                      from expanded
                     group by facet_key, bucket_label
                ),
                ranked as (
                    select facet_key,
                           bucket_label,
                           row_count,
                           distinct_services,
                           row_number() over (
                               partition by facet_key
                               order by row_count desc, distinct_services desc, bucket_label asc
                           ) as rn
                      from aggregated
                )
                select facet_key,
                       bucket_label,
                       row_count,
                       distinct_services
                  from ranked
                 where rn <= :limitPerFacet
              order by %s, row_count desc, distinct_services desc, bucket_label asc
                """.formatted(GOV24_FACET_ORDER_CASE),
                new MapSqlParameterSource("limitPerFacet", limitPerFacet),
                (rs, rowNum) -> new AdminDashboardReadRows.RecommendationFacetRow(
                        rs.getString("facet_key"),
                        rs.getString("bucket_label"),
                        rs.getLong("row_count"),
                        rs.getLong("distinct_services")
                )
        );
    }
}
