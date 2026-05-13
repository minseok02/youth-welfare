package com.example.welfare.integration;

import com.example.welfare.collect.normalization.NormalizedPolicySidecarBackfillService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@SpringBootTest
@ActiveProfiles("integration")
class NormalizedPolicySidecarBackfillDensityIntegrationTest {

    @Autowired
    private NormalizedPolicySidecarBackfillService backfillService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("local draft sidecar migration 상태에서 stored 복지로 raw payload replay 후 service_facts density 가 증가하거나 유지된다")
    void backfillBokjiroSidecarsOnLocalStoredPayloads() {
        assumeTrue(tableExists("service_facts"), "draft sidecar migration 이 적용된 local DB 에서만 실행");

        long bokjiroServiceCount = count("""
                SELECT COUNT(*)
                FROM welfare_services
                WHERE source_type IN ('BOKJIRO_CENTRAL', 'BOKJIRO_LOCAL')
                """);
        long detailPayloadCount = count("""
                SELECT COUNT(*)
                FROM raw_api_payloads
                WHERE source_type IN ('BOKJIRO_CENTRAL', 'BOKJIRO_LOCAL')
                  AND api_category = 'DETAIL'
                """);

        assumeTrue(bokjiroServiceCount > 0, "복지로 적재 데이터가 있어야 density smoke 를 실행할 수 있음");
        assumeTrue(detailPayloadCount > 0, "복지로 detail raw payload 가 있어야 density smoke 를 실행할 수 있음");

        long beforeFacts = countBokjiroFacts();
        long beforeServicesWithFacts = countBokjiroServicesWithFacts();

        NormalizedPolicySidecarBackfillService.BackfillResult listResult =
                backfillService.backfillBokjiroListSidecars(0);
        NormalizedPolicySidecarBackfillService.BackfillResult detailResult =
                backfillService.backfillBokjiroDetailSidecars(0);

        long afterFacts = countBokjiroFacts();
        long afterServicesWithFacts = countBokjiroServicesWithFacts();
        long ageFacts = count("""
                SELECT COUNT(*)
                FROM service_facts sf
                JOIN welfare_services ws ON ws.id = sf.service_id
                WHERE ws.source_type IN ('BOKJIRO_CENTRAL', 'BOKJIRO_LOCAL')
                  AND sf.fact_merge_key = 'BK_AGE_ELIGIBILITY'
                """);
        long deadlineFacts = count("""
                SELECT COUNT(*)
                FROM service_facts sf
                JOIN welfare_services ws ON ws.id = sf.service_id
                WHERE ws.source_type IN ('BOKJIRO_CENTRAL', 'BOKJIRO_LOCAL')
                  AND sf.fact_merge_key = 'BK_APPLY_END_DATE'
                """);
        long dateLikeApplyMethodPayloads = count("""
                SELECT COUNT(*)
                FROM raw_api_payloads
                WHERE source_type IN ('BOKJIRO_CENTRAL', 'BOKJIRO_LOCAL')
                  AND api_category = 'DETAIL'
                  AND COALESCE(payload_json, '')::json ->> 'applyMethodDetail'
                      ~ '[0-9]{4}[.-][0-9]{1,2}[.-][0-9]{1,2}|[0-9]{8}'
                """);

        assertThat(listResult.scannedCount()).isGreaterThan(0);
        assertThat(detailResult.scannedCount()).isGreaterThan(0);
        assertThat(detailResult.upsertedCount()).isGreaterThan(0);
        assertThat(afterFacts).isGreaterThanOrEqualTo(beforeFacts);
        assertThat(afterServicesWithFacts).isGreaterThanOrEqualTo(beforeServicesWithFacts);
        assertThat(afterFacts).isGreaterThan(0);
        assertThat(afterServicesWithFacts).isGreaterThan(0);
        assertThat(ageFacts).isGreaterThan(0);
        if (dateLikeApplyMethodPayloads > 0) {
            assertThat(deadlineFacts).isGreaterThan(0);
        }
    }

    private boolean tableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = current_schema()
                  AND table_name = ?
                """, Integer.class, tableName);
        return count != null && count > 0;
    }

    private long countBokjiroFacts() {
        return count("""
                SELECT COUNT(*)
                FROM service_facts sf
                JOIN welfare_services ws ON ws.id = sf.service_id
                WHERE ws.source_type IN ('BOKJIRO_CENTRAL', 'BOKJIRO_LOCAL')
                """);
    }

    private long countBokjiroServicesWithFacts() {
        return count("""
                SELECT COUNT(DISTINCT sf.service_id)
                FROM service_facts sf
                JOIN welfare_services ws ON ws.id = sf.service_id
                WHERE ws.source_type IN ('BOKJIRO_CENTRAL', 'BOKJIRO_LOCAL')
                """);
    }

    private long count(String sql) {
        Long count = jdbcTemplate.queryForObject(sql, Long.class);
        return count == null ? 0L : count;
    }
}
