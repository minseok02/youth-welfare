package com.example.welfare.admin.dashboard.repository;

import com.example.welfare.admin.dashboard.dto.AdminQueueStatusFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class AdminPolicyLinkReviewReadRepository {

    private final JdbcTemplate jdbcTemplate;

    private static final RowMapper<LinkReviewRow> ROW_MAPPER = (rs, rowNum) -> new LinkReviewRow(
            rs.getLong("service_id"),
            rs.getString("title"),
            rs.getString("source_type"),
            rs.getString("source_id"),
            rs.getString("review_bucket"),
            rs.getString("host_org"),
            rs.getString("operating_org"),
            rs.getString("category_main"),
            rs.getString("category_sub"),
            toLocalDate(rs.getDate("apply_end_date")),
            toLocalDate(rs.getDate("end_date")),
            toLocalDateTime(rs.getTimestamp("created_at")),
            rs.getString("review_note"),
            rs.getString("reviewed_by_user_key"),
            toLocalDateTime(rs.getTimestamp("reviewed_at"))
    );

    public long countOpenReviews() {
        return jdbcTemplate.queryForObject(baseCountSql("and plrr.id is null"), Long.class);
    }

    public long countRecentOpenReviews24h() {
        return jdbcTemplate.queryForObject(baseCountSql("and plrr.id is null and ws.created_at >= now() - interval '24 hours'"), Long.class);
    }

    public List<LinkReviewRow> findRows(AdminQueueStatusFilter statusFilter, int limit) {
        String statusWhere = switch (statusFilter) {
            case OPEN -> "and plrr.id is null";
            case REVIEWED -> "and plrr.id is not null";
            case ALL -> "";
        };
        String sql = """
                with candidate as (
                  select ws.id as service_id,
                         ws.title,
                         ws.source_type,
                         ws.source_id,
                         case
                           when ws.title ~ '(모집|공고|선발|접수|신청자|참여자|참가자|추가모집|수강생)' then 'announcement_recruitment'
                           when ws.title ~ '(지원금|지원사업|지원 프로그램|수당|장학금|이자 지원|응시료|바우처|급여|보조금)' then 'benefit_support'
                           when ws.title ~ '(프로그램|교육|아카데미|캠프|멘토링|기획단|탐방|실험실|클래스|강좌)' then 'program_event'
                           when ws.title ~ '(대회|축제|행사|공연|전시|페스티벌)' then 'event_culture'
                           else 'other'
                         end as review_bucket,
                         coalesce(ws.host_org, '') as host_org,
                         coalesce(ws.operating_org, '') as operating_org,
                         coalesce(ws.category_main, '') as category_main,
                         coalesce(ws.category_sub, '') as category_sub,
                         ws.apply_end_date,
                         ws.end_date,
                         ws.created_at
                  from welfare_services ws
                  left join welfare_service_details wsd on wsd.service_id = ws.id
                  where coalesce(ws.detail_url, '') = ''
                    and (coalesce(wsd.reference_urls_json::text, '') = '' or coalesce(wsd.reference_urls_json::text, '') = '[]')
                    and ws.status in ('ACTIVE', 'UPCOMING')
                    and (ws.apply_end_date is null or ws.apply_end_date >= current_date)
                )
                select c.service_id,
                       c.title,
                       c.source_type,
                       c.source_id,
                       c.review_bucket,
                       c.host_org,
                       c.operating_org,
                       c.category_main,
                       c.category_sub,
                       c.apply_end_date,
                       c.end_date,
                       c.created_at,
                       plrr.review_note,
                       plrr.reviewed_by_user_key,
                       plrr.reviewed_at
                from candidate c
                left join policy_link_review_records plrr on plrr.service_id = c.service_id
                where 1=1
                """ + statusWhere + """
                order by c.created_at desc, c.service_id desc
                limit ?
                """;
        return jdbcTemplate.query(sql, ROW_MAPPER, limit);
    }

    private String baseCountSql(String extraWhere) {
        return """
                with candidate as (
                  select ws.id as service_id, ws.created_at
                  from welfare_services ws
                  left join welfare_service_details wsd on wsd.service_id = ws.id
                  left join policy_link_review_records plrr on plrr.service_id = ws.id
                  where coalesce(ws.detail_url, '') = ''
                    and (coalesce(wsd.reference_urls_json::text, '') = '' or coalesce(wsd.reference_urls_json::text, '') = '[]')
                    and ws.status in ('ACTIVE', 'UPCOMING')
                    and (ws.apply_end_date is null or ws.apply_end_date >= current_date)
                )
                select count(*)
                from welfare_services ws
                left join welfare_service_details wsd on wsd.service_id = ws.id
                left join policy_link_review_records plrr on plrr.service_id = ws.id
                where coalesce(ws.detail_url, '') = ''
                  and (coalesce(wsd.reference_urls_json::text, '') = '' or coalesce(wsd.reference_urls_json::text, '') = '[]')
                  and ws.status in ('ACTIVE', 'UPCOMING')
                  and (ws.apply_end_date is null or ws.apply_end_date >= current_date)
                """ + "\n" + extraWhere;
    }

    private static LocalDate toLocalDate(Date date) {
        return date == null ? null : date.toLocalDate();
    }

    private static LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    public record LinkReviewRow(
            Long serviceId,
            String title,
            String sourceType,
            String sourceId,
            String reviewBucket,
            String hostOrg,
            String operatingOrg,
            String categoryMain,
            String categorySub,
            LocalDate applyEndDate,
            LocalDate endDate,
            LocalDateTime createdAt,
            String reviewNote,
            String reviewedByUserKey,
            LocalDateTime reviewedAt
    ) {
    }
}
