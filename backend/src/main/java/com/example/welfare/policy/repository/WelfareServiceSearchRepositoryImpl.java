package com.example.welfare.policy.repository;

import com.example.welfare.global.util.SearchKeywordSupport;
import com.example.welfare.global.util.RegionCodeUtil;
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
    private static final String SEARCH_TITLE_TRGM_SQL = "similarity(lower(coalesce(ws.title, '')), :normalizedKeyword)";
    private static final String SEARCH_KEYWORD_TRGM_SQL = "similarity(lower(coalesce(ws.keyword, '')), :normalizedKeyword)";
    private static final String SEARCH_TITLE_LIKE_SQL = "lower(coalesce(ws.title, '')) LIKE :normalizedKeywordLike";
    private static final String SEARCH_KEYWORD_LIKE_SQL = "lower(coalesce(ws.keyword, '')) LIKE :normalizedKeywordLike";
    private static final String SEARCH_VECTOR_SQL = "to_tsvector('simple', " + SEARCH_DOCUMENT_SQL + ")";
    private static final String GENERATED_SEARCH_VECTOR_SQL = "ws.search_document_vector";
    private static final String GENERATED_SEARCH_TITLE_TRGM_SQL = "similarity(ws.title_l, :normalizedKeyword)";
    private static final String GENERATED_SEARCH_KEYWORD_TRGM_SQL = "similarity(ws.keyword_l, :normalizedKeyword)";
    private static final String GENERATED_SEARCH_TITLE_LIKE_SQL = "ws.title_l LIKE :normalizedKeywordLike";
    private static final String GENERATED_SEARCH_KEYWORD_LIKE_SQL = "ws.keyword_l LIKE :normalizedKeywordLike";
    private static final String GENERATED_SEARCH_TITLE_TRGM_OPERATOR_SQL = "ws.title_l % :normalizedKeyword";
    private static final String GENERATED_SEARCH_KEYWORD_TRGM_OPERATOR_SQL = "ws.keyword_l % :normalizedKeyword";
    private static final String SEARCH_QUERY_SQL = "to_tsquery('simple', :tsQuery)";
    private static final String SEARCH_MATCH_SQL = """
            (
                %s @@ %s
                OR %s
                OR %s
                OR %s >= :trigramThreshold
                OR %s >= :trigramThreshold
            )
            """.formatted(
            SEARCH_VECTOR_SQL,
            SEARCH_QUERY_SQL,
            SEARCH_TITLE_LIKE_SQL,
            SEARCH_KEYWORD_LIKE_SQL,
            SEARCH_TITLE_TRGM_SQL,
            SEARCH_KEYWORD_TRGM_SQL
    );
    private static final String SEARCH_RANK_SQL = """
            (
                CASE
                    WHEN %s @@ %s THEN ts_rank_cd(%s, %s)
                    ELSE 0
                END
                + CASE WHEN %s THEN 2.5 ELSE 0 END
                + CASE WHEN %s THEN 1.5 ELSE 0 END
                + greatest(%s, %s)
            )
            """.formatted(
            SEARCH_VECTOR_SQL,
            SEARCH_QUERY_SQL,
            SEARCH_VECTOR_SQL,
            SEARCH_QUERY_SQL,
            SEARCH_TITLE_LIKE_SQL,
            SEARCH_KEYWORD_LIKE_SQL,
            SEARCH_TITLE_TRGM_SQL,
            SEARCH_KEYWORD_TRGM_SQL
    );
    private static final String GENERATED_SEARCH_RANK_SQL = """
            (
                CASE
                    WHEN %s @@ %s THEN ts_rank_cd(%s, %s)
                    ELSE 0
                END
                + CASE WHEN %s THEN 2.5 ELSE 0 END
                + CASE WHEN %s THEN 1.5 ELSE 0 END
                + greatest(%s, %s)
            )
            """.formatted(
            GENERATED_SEARCH_VECTOR_SQL,
            SEARCH_QUERY_SQL,
            GENERATED_SEARCH_VECTOR_SQL,
            SEARCH_QUERY_SQL,
            GENERATED_SEARCH_TITLE_LIKE_SQL,
            GENERATED_SEARCH_KEYWORD_LIKE_SQL,
            GENERATED_SEARCH_TITLE_TRGM_SQL,
            GENERATED_SEARCH_KEYWORD_TRGM_SQL
    );
    private static final String ACTIVE_UPCOMING_STATUS_SQL = "ws.status IN ('ACTIVE', 'UPCOMING')";
    private static final String ACTIVE_ONLY_VISIBLE_STATUS_SQL = """
            ws.status IN ('ACTIVE', 'UPCOMING')
            AND (ws.apply_end_date IS NULL OR ws.apply_end_date >= CURRENT_DATE)
            """;
    private static final String DEFAULT_SEARCH_FAST_PATH_BASE_SQL = """
            %s
            AND ws.search_youth_relevant IS TRUE
            """.formatted(ACTIVE_ONLY_VISIBLE_STATUS_SQL);
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
            AND (
                :gov24ServiceField IS NULL
                OR EXISTS (
                    SELECT 1
                    FROM service_taxonomy_terms stt
                    WHERE stt.service_id = ws.id
                      AND stt.term_group = 'GOV24_SERVICE_FIELD'
                      AND stt.term_label = :gov24ServiceField
                )
                OR (
                    NOT EXISTS (
                        SELECT 1
                        FROM service_taxonomy_terms stt1
                        WHERE stt1.service_id = ws.id
                          AND stt1.term_group = 'GOV24_SERVICE_FIELD'
                    )
                    AND EXISTS (
                        SELECT 1
                        FROM service_taxonomy_summary_slots stss
                        WHERE stss.service_id = ws.id
                          AND stss.slot_key = 'GOV24_SERVICE_FIELD'
                          AND stss.slot_label = :gov24ServiceField
                    )
                )
            )
            AND (
                :gov24UserType IS NULL
                OR EXISTS (
                    SELECT 1
                    FROM service_taxonomy_terms stt
                    WHERE stt.service_id = ws.id
                      AND stt.term_group = 'GOV24_USER_TYPE_TOKEN'
                      AND stt.term_label = :gov24UserType
                )
                OR (
                    NOT EXISTS (
                        SELECT 1
                        FROM service_taxonomy_terms stt1
                        WHERE stt1.service_id = ws.id
                          AND stt1.term_group = 'GOV24_USER_TYPE_TOKEN'
                    )
                    AND EXISTS (
                        SELECT 1
                        FROM service_taxonomies stx
                        CROSS JOIN LATERAL regexp_split_to_table(coalesce(stx.gov24_user_type_label, ''), '\\|\\|') AS token_parts(bucket_label)
                        WHERE stx.service_id = ws.id
                          AND btrim(token_parts.bucket_label) = :gov24UserType
                    )
                )
            )
            AND (
                :gov24BenefitType IS NULL
                OR EXISTS (
                    SELECT 1
                    FROM service_taxonomy_terms stt
                    WHERE stt.service_id = ws.id
                      AND stt.term_group = 'GOV24_BENEFIT_TYPE_TOKEN'
                      AND stt.term_label = :gov24BenefitType
                )
                OR (
                    NOT EXISTS (
                        SELECT 1
                        FROM service_taxonomy_terms stt1
                        WHERE stt1.service_id = ws.id
                          AND stt1.term_group = 'GOV24_BENEFIT_TYPE_TOKEN'
                    )
                    AND EXISTS (
                        SELECT 1
                        FROM service_taxonomies stx
                        CROSS JOIN LATERAL regexp_split_to_table(coalesce(stx.gov24_benefit_type_label, ''), '\\|\\|') AS token_parts(bucket_label)
                        WHERE stx.service_id = ws.id
                          AND btrim(token_parts.bucket_label) = :gov24BenefitType
                    )
                )
            )
            AND (:incomeMaxWon IS NULL OR ws.max_income IS NULL OR ws.max_income = 0 OR ws.max_income > :incomeMaxWon)
            """;
    private static final String DEFAULT_RELEVANCE_SEARCH_SQL = """
            WITH trgm_threshold AS MATERIALIZED (
                SELECT set_config('pg_trgm.similarity_threshold', :trigramThresholdText, true)
            ),
            matched_ids AS MATERIALIZED (
                SELECT ws.id
                FROM welfare_services ws
                CROSS JOIN trgm_threshold
                WHERE %s
                  AND %s @@ %s
                UNION
                SELECT ws.id
                FROM welfare_services ws
                CROSS JOIN trgm_threshold
                WHERE %s
                  AND %s
                UNION
                SELECT ws.id
                FROM welfare_services ws
                CROSS JOIN trgm_threshold
                WHERE %s
                  AND %s
                UNION
                SELECT ws.id
                FROM welfare_services ws
                CROSS JOIN trgm_threshold
                WHERE %s
                  AND %s
                UNION
                SELECT ws.id
                FROM welfare_services ws
                CROSS JOIN trgm_threshold
                WHERE %s
                  AND %s
            )
            SELECT ws.id,
                   COUNT(*) OVER() AS total_count
            FROM welfare_services ws
            JOIN matched_ids mi ON mi.id = ws.id
            ORDER BY %s DESC,
                     COALESCE(ws.last_modified_at, ws.registered_at, ws.created_at) DESC,
                     ws.id DESC
            LIMIT :limit OFFSET :offset
            """.formatted(
            DEFAULT_SEARCH_FAST_PATH_BASE_SQL,
            GENERATED_SEARCH_VECTOR_SQL,
            SEARCH_QUERY_SQL,
            DEFAULT_SEARCH_FAST_PATH_BASE_SQL,
            GENERATED_SEARCH_TITLE_LIKE_SQL,
            DEFAULT_SEARCH_FAST_PATH_BASE_SQL,
            GENERATED_SEARCH_KEYWORD_LIKE_SQL,
            DEFAULT_SEARCH_FAST_PATH_BASE_SQL,
            GENERATED_SEARCH_TITLE_TRGM_OPERATOR_SQL,
            DEFAULT_SEARCH_FAST_PATH_BASE_SQL,
            GENERATED_SEARCH_KEYWORD_TRGM_OPERATOR_SQL,
            GENERATED_SEARCH_RANK_SQL
    );

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    private final WelfareServiceRepository welfareServiceRepository;

    @Override
    public Page<WelfareService> search(PolicySearchReadCondition condition, Pageable pageable) {
        SearchKeyword keyword = SearchKeyword.from(condition.keyword());
        if (keyword.isEmpty()) {
            return Page.empty(pageable);
        }

        MapSqlParameterSource params = policyParams(condition, keyword);
        SearchSqlParts sqlParts = shouldUseDefaultRelevanceSearchFastPath(condition, pageable)
                ? new SearchSqlParts(DEFAULT_RELEVANCE_SEARCH_SQL)
                : buildPolicySearchSql(condition);
        boolean applyGov24DiscoveryBalance = shouldApplyGov24DiscoveryBalance(condition, pageable);
        int pageSize = pageable.getPageSize();
        int queryLimit = applyGov24DiscoveryBalance ? discoveryBalanceQueryLimit(pageSize) : pageSize;
        params.addValue("limit", queryLimit, Types.INTEGER);
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
        List<WelfareService> services = loadOrderedServices(ids);
        if (applyGov24DiscoveryBalance) {
            services = applyGov24DiscoveryBalance(services, pageSize);
        }
        return new PageImpl<>(services, pageable, total);
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

    @Override
    public List<WelfareService> searchSuggestionTitleCandidates(String keyword, int limit) {
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
                          AND (
                              %s
                              OR %s
                          )
                        ORDER BY CASE
                                     WHEN lower(COALESCE(ws.title, '')) = :normalizedKeyword THEN 4
                                     WHEN lower(COALESCE(ws.title, '')) LIKE CONCAT(:normalizedKeyword, '%%') THEN 3
                                     WHEN %s THEN 2
                                     WHEN %s THEN 1
                                     ELSE 0
                                 END DESC,
                                 COALESCE(ws.api_view_count, 0) DESC,
                                 COALESCE(ws.view_count, 0) DESC,
                                 COALESCE(ws.last_modified_at, ws.registered_at, ws.created_at) DESC,
                                 ws.id DESC
                        LIMIT :limit
                        """.formatted(
                        ACTIVE_UPCOMING_STATUS_SQL,
                        SEARCH_TITLE_LIKE_SQL,
                        SEARCH_KEYWORD_LIKE_SQL,
                        SEARCH_TITLE_LIKE_SQL,
                        SEARCH_KEYWORD_LIKE_SQL
                ),
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
                                  AND (
                                      sr2.sido_name = :sido
                                      OR (:sidoCode IS NOT NULL AND sr2.region_code LIKE CONCAT(:sidoCode, '%'))
                                  )
                                  AND (
                                      sr2.sgg_name = :sgg
                                      OR (:regionCode IS NOT NULL AND sr2.region_code = :regionCode)
                                  )
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
                                  AND (
                                      sr2.sido_name = :sido
                                      OR (:sidoCode IS NOT NULL AND sr2.region_code LIKE CONCAT(:sidoCode, '%'))
                                  )
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
                              AND (
                                  sr_ord.sido_name = :sido
                                  OR (:sidoCode IS NOT NULL AND sr_ord.region_code LIKE CONCAT(:sidoCode, '%'))
                              )
                              AND (
                                  sr_ord.sgg_name = :sgg
                                  OR (:regionCode IS NOT NULL AND sr_ord.region_code = :regionCode)
                              )
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
                          AND (
                              sr_ord.sido_name = :sido
                              OR (:sidoCode IS NOT NULL AND sr_ord.region_code LIKE CONCAT(:sidoCode, '%'))
                          )
                    ) THEN 0
                    ELSE 1
                END
                """;
    }

    private MapSqlParameterSource policyParams(PolicySearchReadCondition condition, SearchKeyword keyword) {
        String sido = RegionCodeUtil.fullSidoName(condition.sido());
        String sidoCode = RegionCodeUtil.getSidoCode(sido);
        String regionCode = RegionCodeUtil.getRegionCode(sido, condition.sgg());
        return baseKeywordParams(keyword)
                .addValue("status", condition.status(), Types.VARCHAR)
                .addValue("statusFilter", condition.statusFilter(), Types.VARCHAR)
                .addValue("category", condition.category(), Types.VARCHAR)
                .addValue("sourceType", condition.sourceType(), Types.VARCHAR)
                .addValue("onlineApply", toBoolean(condition.onlineApply()), Types.BOOLEAN)
                .addValue("sido", sido, Types.VARCHAR)
                .addValue("sgg", condition.sgg(), Types.VARCHAR)
                .addValue("sidoCode", sidoCode, Types.VARCHAR)
                .addValue("regionCode", regionCode, Types.VARCHAR)
                .addValue("incomeMaxWon", condition.incomeMaxWon(), Types.INTEGER)
                .addValue("targetGroup", condition.targetGroup(), Types.VARCHAR)
                .addValue("gov24ServiceField", condition.gov24ServiceField(), Types.VARCHAR)
                .addValue("gov24UserType", condition.gov24UserType(), Types.VARCHAR)
                .addValue("gov24BenefitType", condition.gov24BenefitType(), Types.VARCHAR);
    }

    private MapSqlParameterSource baseKeywordParams(SearchKeyword keyword) {
        return new MapSqlParameterSource()
                .addValue("normalizedKeyword", keyword.normalizedText(), Types.VARCHAR)
                .addValue("normalizedKeywordLike", "%" + keyword.normalizedText() + "%", Types.VARCHAR)
                .addValue("tsQuery", keyword.tsQuery(), Types.VARCHAR)
                .addValue("trigramThresholdText", Double.toString(TRIGRAM_THRESHOLD), Types.VARCHAR)
                .addValue("trigramThreshold", TRIGRAM_THRESHOLD, Types.DOUBLE);
    }

    private boolean shouldUseDefaultRelevanceSearchFastPath(PolicySearchReadCondition condition, Pageable pageable) {
        String sort = condition.sort() == null ? "RELEVANCE" : condition.sort();
        String statusFilter = condition.statusFilter() == null ? "ACTIVE_ONLY" : condition.statusFilter();
        return pageable.getOffset() == 0
                && "RELEVANCE".equals(sort)
                && condition.status() == null
                && "ACTIVE_ONLY".equals(statusFilter)
                && condition.category() == null
                && condition.sourceType() == null
                && condition.onlineApply() == null
                && condition.sido() == null
                && condition.sgg() == null
                && condition.incomeMaxWon() == null
                && condition.targetGroup() == null
                && condition.gov24ServiceField() == null
                && condition.gov24UserType() == null
                && condition.gov24BenefitType() == null;
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

    private boolean shouldApplyGov24DiscoveryBalance(PolicySearchReadCondition condition, Pageable pageable) {
        String sort = condition.sort() == null ? "RELEVANCE" : condition.sort();
        return !SearchKeywordSupport.extractTokens(condition.keyword()).isEmpty()
                && pageable.getOffset() == 0
                && pageable.getPageSize() >= 10
                && "RELEVANCE".equals(sort)
                && condition.sourceType() == null
                && condition.gov24ServiceField() == null
                && condition.gov24UserType() == null
                && condition.gov24BenefitType() == null;
    }

    private int discoveryBalanceQueryLimit(int pageSize) {
        return Math.min(100, Math.max(pageSize * 5, pageSize + 20));
    }

    private List<WelfareService> applyGov24DiscoveryBalance(List<WelfareService> candidates, int pageSize) {
        if (candidates.size() <= pageSize) {
            return candidates;
        }
        List<WelfareService> firstPage = candidates.subList(0, pageSize);
        boolean alreadyHasGov24 = firstPage.stream()
                .anyMatch(service -> service.getSourceType() == WelfareService.SourceType.GOV24);
        if (alreadyHasGov24) {
            return List.copyOf(firstPage);
        }
        WelfareService firstGov24 = candidates.stream()
                .filter(service -> service.getSourceType() == WelfareService.SourceType.GOV24)
                .findFirst()
                .orElse(null);
        if (firstGov24 == null) {
            return List.copyOf(firstPage);
        }

        int insertIndex = Math.min(pageSize - 1, Math.max(4, pageSize / 3));
        List<WelfareService> balanced = new ArrayList<>(pageSize);
        for (WelfareService service : candidates) {
            if (Objects.equals(service.getId(), firstGov24.getId())) {
                continue;
            }
            if (balanced.size() == insertIndex) {
                balanced.add(firstGov24);
            }
            if (balanced.size() >= pageSize) {
                break;
            }
            balanced.add(service);
        }
        if (balanced.size() < pageSize && balanced.stream().noneMatch(service -> Objects.equals(service.getId(), firstGov24.getId()))) {
            balanced.add(firstGov24);
        }
        return balanced.size() > pageSize ? balanced.subList(0, pageSize) : balanced;
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
