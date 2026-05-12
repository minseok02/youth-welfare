package com.example.welfare.policy.repository;

import com.example.welfare.policy.dto.PolicyCategoryAuditResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class PolicyCategoryAuditReadRepositoryImpl implements PolicyCategoryAuditReadRepository {

    private static final List<String> YOUTH_BROAD_CATEGORIES = List.of("복지문화", "금융·복지·문화");

    private final NamedParameterJdbcTemplate jdbcTemplate;

    @Override
    public long fetchTotalPolicyCount() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM welfare_services",
                Map.of(),
                Long.class
        );
    }

    @Override
    public long fetchSearchablePolicyCount() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM welfare_services WHERE search_youth_relevant = TRUE",
                Map.of(),
                Long.class
        );
    }

    @Override
    public List<PolicyCategoryAuditResponse.CategoryCount> fetchUnifiedCategoryCounts() {
        return jdbcTemplate.query("""
                        SELECT COALESCE(unified_category, 'UNKNOWN') AS unified_category,
                               COUNT(*) AS total_count,
                               COUNT(*) FILTER (WHERE search_youth_relevant = TRUE) AS searchable_count
                          FROM welfare_services
                      GROUP BY COALESCE(unified_category, 'UNKNOWN')
                      ORDER BY total_count DESC, unified_category ASC
                        """,
                categoryCountRowMapper()
        );
    }

    @Override
    public List<PolicyCategoryAuditResponse.SourceCategoryMappingCount> fetchYouthBroadCategoryMappings() {
        return jdbcTemplate.query("""
                        SELECT category_main,
                               COALESCE(unified_category, 'UNKNOWN') AS unified_category,
                               COUNT(*) AS total_count
                          FROM welfare_services
                         WHERE source_type = 'YOUTH'
                           AND category_main IN (:categories)
                      GROUP BY category_main, COALESCE(unified_category, 'UNKNOWN')
                      ORDER BY category_main ASC, total_count DESC, unified_category ASC
                        """,
                Map.of("categories", YOUTH_BROAD_CATEGORIES),
                sourceCategoryMappingRowMapper()
        );
    }

    private RowMapper<PolicyCategoryAuditResponse.CategoryCount> categoryCountRowMapper() {
        return (rs, rowNum) -> new PolicyCategoryAuditResponse.CategoryCount(
                rs.getString("unified_category"),
                rs.getLong("total_count"),
                rs.getLong("searchable_count")
        );
    }

    private RowMapper<PolicyCategoryAuditResponse.SourceCategoryMappingCount> sourceCategoryMappingRowMapper() {
        return (rs, rowNum) -> new PolicyCategoryAuditResponse.SourceCategoryMappingCount(
                rs.getString("category_main"),
                rs.getString("unified_category"),
                rs.getLong("total_count")
        );
    }
}
