package com.example.welfare.policy.repository;

import com.example.welfare.global.util.SearchKeywordSupport;
import com.example.welfare.policy.entity.WelfareService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Repository
@RequiredArgsConstructor
public class WelfareServiceSearchRepositoryImpl implements WelfareServiceSearchRepository {

    private static final double TRIGRAM_THRESHOLD = 0.2d;
    private static final String SEARCH_DOCUMENT_SQL = """
            lower(
                coalesce(ws.title, '')
                || ' ' || coalesce(ws.description, '')
                || ' ' || coalesce(ws.support_content, '')
                || ' ' || coalesce(ws.keyword, '')
            )
            """;
    private static final String SEARCH_DOCUMENT_TRGM_SQL = """
            similarity(%s, :normalizedKeyword)
            """.formatted(SEARCH_DOCUMENT_SQL);
    private static final String SEARCH_VECTOR_SQL = "to_tsvector('simple', " + SEARCH_DOCUMENT_SQL + ")";
    private static final String SEARCH_QUERY_SQL = "to_tsquery('simple', :tsQuery)";
    private static final String SEARCH_MATCH_SQL = """
            (
                %s @@ %s
                OR similarity(lower(coalesce(ws.title, '')), :normalizedKeyword) >= :trigramThreshold
                OR similarity(lower(coalesce(ws.description, '')), :normalizedKeyword) >= :trigramThreshold
                OR similarity(lower(coalesce(ws.support_content, '')), :normalizedKeyword) >= :trigramThreshold
                OR similarity(lower(coalesce(ws.keyword, '')), :normalizedKeyword) >= :trigramThreshold
                OR %s >= :trigramThreshold
            )
            """.formatted(SEARCH_VECTOR_SQL, SEARCH_QUERY_SQL, SEARCH_DOCUMENT_TRGM_SQL);
    private static final String SEARCH_RANK_SQL = """
            (
                CASE
                    WHEN %s @@ %s THEN ts_rank_cd(%s, %s)
                    ELSE 0
                END
                + greatest(
                    similarity(lower(coalesce(ws.title, '')), :normalizedKeyword),
                    similarity(lower(coalesce(ws.description, '')), :normalizedKeyword),
                    similarity(lower(coalesce(ws.support_content, '')), :normalizedKeyword),
                    %s,
                    similarity(lower(coalesce(ws.keyword, '')), :normalizedKeyword)
                )
            )
            """.formatted(SEARCH_VECTOR_SQL, SEARCH_QUERY_SQL, SEARCH_VECTOR_SQL, SEARCH_QUERY_SQL, SEARCH_DOCUMENT_TRGM_SQL);
    private static final String ACTIVE_UPCOMING_STATUS_SQL = "ws.status IN ('ACTIVE', 'UPCOMING')";
    private static final String STATUS_FILTER_SQL = """
            (
                (:status IS NULL AND (
                    (:statusFilter = 'ALL' AND ws.status IN ('ACTIVE', 'UPCOMING', 'CLOSED'))
                    OR (:statusFilter = 'EXPIRED_ONLY' AND (ws.status = 'CLOSED' OR (ws.apply_end_date IS NOT NULL AND ws.apply_end_date < CURRENT_DATE)))
                    OR ((:statusFilter IS NULL OR :statusFilter = 'ACTIVE_ONLY') AND ws.status IN ('ACTIVE', 'UPCOMING') AND (ws.apply_end_date IS NULL OR ws.apply_end_date >= CURRENT_DATE))
                ))
                OR (:status IS NOT NULL AND ws.status = :status)
            )
            """;
    private static final String POLICY_FILTER_SQL = """
            ws.search_youth_relevant IS TRUE
            AND (:category IS NULL OR ws.unified_category = :category)
            AND (:sourceType IS NULL OR ws.source_type = :sourceType)
            AND (:onlineApply IS NULL OR ws.is_online_apply = :onlineApply)
            AND (:targetGroup IS NULL OR EXISTS (
                SELECT 1 FROM service_tags st
                WHERE st.service_id = ws.id
                  AND st.tag_type = 'TARGET_GROUP'
                  AND st.tag_value = :targetGroup
            ))
            AND (:incomeMaxWon IS NULL OR ws.max_income IS NULL OR ws.max_income = 0 OR ws.max_income > :incomeMaxWon)
            """;

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    private final WelfareServiceRepository welfareServiceRepository;

    @Override
    public Page<WelfareService> search(PolicySearchReadCondition condition, Pageable pageable) {
        SearchKeyword keyword = SearchKeyword.from(condition.keyword());
        if (keyword.isEmpty()) {
            return Page.empty(pageable);
        }

        MapSqlParameterSource params = policyParams(condition, keyword);
        SearchSqlParts sqlParts = buildPolicySearchSql(condition);
        params.addValue("limit", pageable.getPageSize(), Types.INTEGER);
        params.addValue("offset", pageable.getOffset(), Types.BIGINT);
        List<SearchPageRow> rows = namedParameterJdbcTemplate.query(
                sqlParts.selectSql(),
                params,
                (rs, rowNum) -> new SearchPageRow(
                        rs.getLong("id"),
                        rs.getLong("total_count")
                )
        );
        if (rows.isEmpty()) {
            return Page.empty(pageable);
        }
        List<Long> ids = rows.stream()
                .map(SearchPageRow::id)
                .toList();
        long total = rows.get(0).totalCount();
        return new PageImpl<>(loadOrderedServices(ids), pageable, total);
    }

    @Override
    public List<WelfareService> searchChatCandidates(String keyword, int limit) {
        SearchKeyword normalizedKeyword = SearchKeyword.from(keyword);
        if (normalizedKeyword.isEmpty() || limit <= 0) {
            return List.of();
        }

        MapSqlParameterSource params = baseKeywordParams(normalizedKeyword)
                .addValue("limit", limit);
        List<Long> ids = namedParameterJdbcTemplate.query("""
                        SELECT ws.id
                        FROM welfare_services ws
                        WHERE %s
                          AND ws.search_youth_relevant IS TRUE
                          AND %s
                        ORDER BY %s DESC,
                                 COALESCE(ws.api_view_count, 0) DESC,
                                 COALESCE(ws.view_count, 0) DESC,
                                 COALESCE(ws.last_modified_at, ws.registered_at, ws.created_at) DESC,
                                 ws.id DESC
                        LIMIT :limit
                        """.formatted(ACTIVE_UPCOMING_STATUS_SQL, SEARCH_MATCH_SQL, SEARCH_RANK_SQL),
                params,
                (rs, rowNum) -> rs.getLong("id"));
        return loadOrderedServices(ids);
    }

    private SearchSqlParts buildPolicySearchSql(PolicySearchReadCondition condition) {
        StringBuilder whereSql = new StringBuilder()
                .append(STATUS_FILTER_SQL)
                .append("\n  AND ")
                .append(POLICY_FILTER_SQL)
                .append("\n  AND ")
                .append(SEARCH_MATCH_SQL);

        boolean hasSido = condition.sido() != null;
        boolean hasSgg = condition.sgg() != null;
        if (hasSido && hasSgg) {
            whereSql.append("""

                      AND (
                            NOT EXISTS (
                                SELECT 1 FROM service_regions sr1
                                WHERE sr1.service_id = ws.id
                            )
                            OR EXISTS (
                                SELECT 1 FROM service_regions sr2
                                WHERE sr2.service_id = ws.id
                                  AND sr2.sido_name = :sido
                                  AND sr2.sgg_name = :sgg
                            )
                          )
                    """);
        } else if (hasSido) {
            whereSql.append("""

                      AND (
                            NOT EXISTS (
                                SELECT 1 FROM service_regions sr1
                                WHERE sr1.service_id = ws.id
                            )
                            OR EXISTS (
                                SELECT 1 FROM service_regions sr2
                                WHERE sr2.service_id = ws.id
                                  AND sr2.sido_name = :sido
                            )
                          )
                    """);
        }

        String orderBySql = buildPolicyOrderBySql(condition, hasSido, hasSgg);
        String selectSql = """
                SELECT ws.id,
                       COUNT(*) OVER() AS total_count
                FROM welfare_services ws
                WHERE %s
                ORDER BY %s
                LIMIT :limit OFFSET :offset
                """.formatted(whereSql, orderBySql);
        return new SearchSqlParts(selectSql);
    }

    private String buildPolicyOrderBySql(PolicySearchReadCondition condition, boolean hasSido, boolean hasSgg) {
        List<String> clauses = new ArrayList<>();
        String sort = condition.sort() == null ? "RELEVANCE" : condition.sort();
        String regionPrioritySql = regionPrioritySql(hasSgg);

        if (hasSido && "LATEST".equals(sort)) {
            clauses.add(regionPrioritySql + " ASC");
        }

        switch (sort) {
            case "VIEWS" -> {
                clauses.add("COALESCE(ws.api_view_count, 0) DESC");
                clauses.add("COALESCE(ws.view_count, 0) DESC");
            }
            case "LATEST" -> clauses.add("COALESCE(ws.last_modified_at, ws.registered_at, ws.created_at) DESC");
            case "NAME" -> clauses.add("ws.title ASC");
            case "DEADLINE" -> clauses.add("COALESCE(ws.apply_end_date, DATE '9999-12-31') ASC");
            case "RELEVANCE" -> clauses.add(SEARCH_RANK_SQL + " DESC");
            default -> clauses.add(SEARCH_RANK_SQL + " DESC");
        }

        if (hasSido && !"LATEST".equals(sort)) {
            clauses.add(regionPrioritySql + " ASC");
        }

        if (!"RELEVANCE".equals(sort)) {
            clauses.add(SEARCH_RANK_SQL + " DESC");
        }
        if (!"LATEST".equals(sort)) {
            clauses.add("COALESCE(ws.last_modified_at, ws.registered_at, ws.created_at) DESC");
        }
        clauses.add("ws.id DESC");
        return String.join(",\n         ", clauses);
    }

    private String regionPrioritySql(boolean hasSgg) {
        if (hasSgg) {
            return """
                    CASE
                        WHEN EXISTS (
                            SELECT 1 FROM service_regions sr_ord
                            WHERE sr_ord.service_id = ws.id
                              AND sr_ord.sido_name = :sido
                              AND sr_ord.sgg_name = :sgg
                        ) THEN 0
                        ELSE 1
                    END
                    """;
        }
        return """
                CASE
                    WHEN EXISTS (
                        SELECT 1 FROM service_regions sr_ord
                        WHERE sr_ord.service_id = ws.id
                          AND sr_ord.sido_name = :sido
                    ) THEN 0
                    ELSE 1
                END
                """;
    }

    private MapSqlParameterSource policyParams(PolicySearchReadCondition condition, SearchKeyword keyword) {
        return baseKeywordParams(keyword)
                .addValue("status", condition.status(), Types.VARCHAR)
                .addValue("statusFilter", condition.statusFilter(), Types.VARCHAR)
                .addValue("category", condition.category(), Types.VARCHAR)
                .addValue("sourceType", condition.sourceType(), Types.VARCHAR)
                .addValue("onlineApply", toBoolean(condition.onlineApply()), Types.BOOLEAN)
                .addValue("sido", condition.sido(), Types.VARCHAR)
                .addValue("sgg", condition.sgg(), Types.VARCHAR)
                .addValue("incomeMaxWon", condition.incomeMaxWon(), Types.INTEGER)
                .addValue("targetGroup", condition.targetGroup(), Types.VARCHAR);
    }

    private MapSqlParameterSource baseKeywordParams(SearchKeyword keyword) {
        return new MapSqlParameterSource()
                .addValue("normalizedKeyword", keyword.normalizedText(), Types.VARCHAR)
                .addValue("tsQuery", keyword.tsQuery(), Types.VARCHAR)
                .addValue("trigramThreshold", TRIGRAM_THRESHOLD, Types.DOUBLE);
    }

    private Boolean toBoolean(Integer onlineApply) {
        if (onlineApply == null) {
            return null;
        }
        return onlineApply == 1;
    }

    private List<WelfareService> loadOrderedServices(List<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        List<WelfareService> services = welfareServiceRepository.findAllById(ids);
        Map<Long, WelfareService> byId = new LinkedHashMap<>();
        for (WelfareService service : services) {
            byId.put(service.getId(), service);
        }
        return ids.stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .toList();
    }

    private record SearchSqlParts(String selectSql) {
    }

    private record SearchPageRow(Long id, Long totalCount) {
    }

    private record SearchKeyword(String normalizedText, String tsQuery) {

        private static SearchKeyword from(String keyword) {
            String normalizedText = SearchKeywordSupport.normalizeText(keyword);
            return new SearchKeyword(
                    normalizedText,
                    SearchKeywordSupport.buildTsQuery(keyword)
            );
        }

        private boolean isEmpty() {
            return normalizedText.isBlank() || tsQuery.isBlank();
        }
    }
}
