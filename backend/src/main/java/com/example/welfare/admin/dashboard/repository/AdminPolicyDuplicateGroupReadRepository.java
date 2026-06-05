package com.example.welfare.admin.dashboard.repository;

import com.example.welfare.admin.dashboard.dto.AdminQueueStatusFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class AdminPolicyDuplicateGroupReadRepository {

    private final @Qualifier("primaryNamedParameterJdbcTemplate")
    NamedParameterJdbcTemplate jdbcTemplate;

    private static final RowMapper<DuplicateGroupRow> ROW_MAPPER = (rs, rowNum) -> new DuplicateGroupRow(
            rs.getString("source_type"),
            rs.getString("title"),
            rs.getString("host_org_key"),
            rs.getString("host_org_label"),
            rs.getString("review_class"),
            rs.getInt("duplicate_count"),
            rs.getString("source_ids"),
            toLocalDateTime(rs.getTimestamp("latest_created_at")),
            rs.getString("review_note"),
            rs.getString("reviewed_by_user_key"),
            toLocalDateTime(rs.getTimestamp("reviewed_at"))
    );

    public List<DuplicateGroupRow> findGroups(AdminQueueStatusFilter statusFilter, int limit) {
        MapSqlParameterSource params = baseParams(statusFilter).addValue("limit", limit);
        return jdbcTemplate.query("""
                with duplicate_groups as (
                    select
                        ws.source_type,
                        ws.title,
                        coalesce(ws.host_org, '') as host_org_key,
                        nullif(max(coalesce(ws.host_org, '')), '') as host_org_label,
                        count(distinct coalesce(ws.detail_url, '')) as distinct_detail_url_count,
                        count(distinct coalesce(ws.apply_start_date::text, '')) as distinct_apply_start_count,
                        count(distinct coalesce(ws.apply_end_date::text, '')) as distinct_apply_end_count,
                        count(*) as duplicate_count,
                        string_agg(ws.source_id, ', ' order by ws.source_id) as source_ids,
                        max(ws.created_at) as latest_created_at
                    from welfare_services ws
                    where ws.source_type in ('YOUTH', 'BOKJIRO_LOCAL')
                    group by ws.source_type, ws.title, coalesce(ws.host_org, '')
                    having count(*) > 1
                ),
                classified_groups as (
                    select
                        dg.*,
                        case
                            when dg.source_type = 'YOUTH'
                                and dg.distinct_apply_start_count <= 1
                                and dg.distinct_apply_end_count <= 1
                                and dg.distinct_detail_url_count <= 1
                                then 'exact_duplicate_candidate'
                            when dg.source_type = 'YOUTH'
                                and dg.distinct_apply_start_count <= 1
                                and dg.distinct_apply_end_count <= 1
                                and dg.distinct_detail_url_count > 1
                                then 'mirror_or_channel_variant_candidate'
                            when dg.source_type = 'BOKJIRO_LOCAL'
                                and dg.host_org_key = ''
                                then 'title_only_false_positive_risk'
                            else 'date_or_contract_drift_candidate'
                        end as review_class
                    from duplicate_groups dg
                )
                select
                    dg.source_type,
                    dg.title,
                    dg.host_org_key,
                    dg.host_org_label,
                    dg.review_class,
                    dg.duplicate_count,
                    dg.source_ids,
                    dg.latest_created_at,
                    pr.review_note,
                    pr.reviewed_by_user_key,
                    pr.reviewed_at
                from classified_groups dg
                left join policy_duplicate_review_records pr
                  on pr.source_type = dg.source_type
                 and pr.title = dg.title
                 and pr.host_org_key = dg.host_org_key
                where (
                    :statusFilter = 'ALL'
                    or (:statusFilter = 'OPEN' and pr.id is null)
                    or (:statusFilter = 'REVIEWED' and pr.id is not null)
                )
                order by
                    case dg.review_class
                        when 'exact_duplicate_candidate' then 1
                        when 'mirror_or_channel_variant_candidate' then 2
                        when 'date_or_contract_drift_candidate' then 3
                        when 'title_only_false_positive_risk' then 4
                        else 5
                    end asc,
                    dg.duplicate_count desc,
                    dg.source_type asc,
                    dg.title asc
                limit :limit
                """, params, ROW_MAPPER);
    }

    public long countOpenGroups() {
        return countGroups(" and pr.id is null ", baseParams(AdminQueueStatusFilter.OPEN));
    }

    public long countRecentOpenGroups24h() {
        return countGroups(" and pr.id is null and dg.latest_created_at >= :recentThreshold ",
                baseParams(AdminQueueStatusFilter.OPEN).addValue("recentThreshold", Timestamp.valueOf(LocalDateTime.now().minusHours(24))));
    }

    public long countOpenDuplicateRows() {
        Long value = jdbcTemplate.queryForObject("""
                with duplicate_groups as (
                    select
                        ws.source_type,
                        ws.title,
                        coalesce(ws.host_org, '') as host_org_key,
                        count(*) as duplicate_count
                    from welfare_services ws
                    where ws.source_type in ('YOUTH', 'BOKJIRO_LOCAL')
                    group by ws.source_type, ws.title, coalesce(ws.host_org, '')
                    having count(*) > 1
                )
                select coalesce(sum(dg.duplicate_count), 0)
                from duplicate_groups dg
                left join policy_duplicate_review_records pr
                  on pr.source_type = dg.source_type
                 and pr.title = dg.title
                 and pr.host_org_key = dg.host_org_key
                where pr.id is null
                """, new MapSqlParameterSource(), Long.class);
        return value == null ? 0L : value;
    }

    private long countGroups(String extraPredicate, MapSqlParameterSource params) {
        Long value = jdbcTemplate.queryForObject("""
                with duplicate_groups as (
                    select
                        ws.source_type,
                        ws.title,
                        coalesce(ws.host_org, '') as host_org_key,
                        max(ws.created_at) as latest_created_at
                    from welfare_services ws
                    where ws.source_type in ('YOUTH', 'BOKJIRO_LOCAL')
                    group by ws.source_type, ws.title, coalesce(ws.host_org, '')
                    having count(*) > 1
                )
                select count(*)
                from duplicate_groups dg
                left join policy_duplicate_review_records pr
                  on pr.source_type = dg.source_type
                 and pr.title = dg.title
                 and pr.host_org_key = dg.host_org_key
                where 1 = 1
                """ + extraPredicate, params, Long.class);
        return value == null ? 0L : value;
    }

    private MapSqlParameterSource baseParams(AdminQueueStatusFilter statusFilter) {
        return new MapSqlParameterSource()
                .addValue("statusFilter", statusFilter.name());
    }

    private static LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    public record DuplicateGroupRow(
            String sourceType,
            String title,
            String hostOrgKey,
            String hostOrgLabel,
            String reviewClass,
            int duplicateCount,
            String sourceIds,
            LocalDateTime latestCreatedAt,
            String reviewNote,
            String reviewedByUserKey,
            LocalDateTime reviewedAt
    ) {
    }
}
