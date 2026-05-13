package com.example.welfare.collect.repository;

import com.example.welfare.collect.normalization.NormalizedPolicyAggregate;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.util.List;
import java.util.Set;

@Repository
@RequiredArgsConstructor
public class DeferredNormalizedPolicySidecarReadRepositoryImpl implements DeferredNormalizedPolicySidecarReadRepository {

    private static final Set<String> REQUIRED_TABLES = Set.of(
            "normalization_code_sets",
            "service_taxonomies",
            "service_taxonomy_terms",
            "service_facts"
    );
    private static final String SUMMARY_SLOT_TABLE = "service_taxonomy_summary_slots";

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Override
    public boolean sidecarTablesReady() {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = current_schema()
                  AND table_name IN ('normalization_code_sets', 'service_taxonomies', 'service_taxonomy_terms', 'service_facts')
                """, Integer.class);
        return count != null && count == REQUIRED_TABLES.size();
    }

    @Override
    public boolean summarySlotTableReady() {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = current_schema()
                  AND table_name = ?
                """, Integer.class, SUMMARY_SLOT_TABLE);
        return count != null && count > 0;
    }

    @Override
    public List<NormalizedPolicyAggregate.Fact> findExistingFacts(Long serviceId) {
        return namedParameterJdbcTemplate.query("""
                        SELECT
                            fact_group,
                            fact_code_set_key,
                            fact_code,
                            fact_merge_key,
                            fact_label,
                            operator,
                            value_type,
                            bool_value,
                            int_value,
                            decimal_value,
                            text_value,
                            date_value,
                            range_min_int,
                            range_max_int,
                            unit,
                            source_field,
                            authority,
                            confidence,
                            raw_value,
                            evidence_text
                        FROM service_facts
                        WHERE service_id = :serviceId
                        """,
                new MapSqlParameterSource("serviceId", serviceId),
                factRowMapper());
    }

    private RowMapper<NormalizedPolicyAggregate.Fact> factRowMapper() {
        return (rs, rowNum) -> NormalizedPolicyAggregate.Fact.builder()
                .factGroup(rs.getString("fact_group"))
                .factCodeSetKey(rs.getString("fact_code_set_key"))
                .factCode(rs.getString("fact_code"))
                .factMergeKey(rs.getString("fact_merge_key"))
                .factLabel(rs.getString("fact_label"))
                .operator(NormalizedPolicyAggregate.Operator.valueOf(rs.getString("operator")))
                .valueType(NormalizedPolicyAggregate.ValueType.valueOf(rs.getString("value_type")))
                .boolValue((Boolean) rs.getObject("bool_value"))
                .intValue((Integer) rs.getObject("int_value"))
                .decimalValue(rs.getBigDecimal("decimal_value"))
                .textValue(rs.getString("text_value"))
                .dateValue(rs.getObject("date_value", Date.class) == null
                        ? null
                        : rs.getObject("date_value", Date.class).toLocalDate())
                .rangeMinInt((Integer) rs.getObject("range_min_int"))
                .rangeMaxInt((Integer) rs.getObject("range_max_int"))
                .unit(rs.getString("unit"))
                .sourceField(rs.getString("source_field"))
                .authority(NormalizedPolicyAggregate.Authority.valueOf(rs.getString("authority")))
                .confidence(rs.getBigDecimal("confidence"))
                .rawValue(rs.getString("raw_value"))
                .evidenceText(rs.getString("evidence_text"))
                .build();
    }
}
