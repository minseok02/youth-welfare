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
class NormalizedPolicySidecarBeneficiaryTermDensityIntegrationTest {

    @Autowired
    private NormalizedPolicySidecarBackfillService backfillService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("local draft sidecar migration 상태에서 stored 복지로 detail replay 후 beneficiary TARGET_GROUP term density 가 증가하거나 유지된다")
    void backfillBokjiroDetailBeneficiaryTermsOnLocalStoredPayloads() {
        assumeTrue(tableExists("service_taxonomy_terms"), "draft sidecar migration 이 적용된 local DB 에서만 실행");

        long candidatePayloadCount = count("""
                SELECT COUNT(*)
                FROM raw_api_payloads
                WHERE source_type IN ('BOKJIRO_CENTRAL', 'BOKJIRO_LOCAL')
                  AND api_category = 'DETAIL'
                  AND (
                        JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.targetDetail')) REGEXP '국민기초생활보장수급자|기초생활수급자|생계급여 수급자|의료급여 수급자|주거급여 수급자|교육급여 수급자|수급권자|차상위'
                     OR JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.selectionCriteria')) REGEXP '국민기초생활보장수급자|기초생활수급자|생계급여 수급자|의료급여 수급자|주거급여 수급자|교육급여 수급자|수급권자|차상위'
                  )
                """);

        assumeTrue(candidatePayloadCount > 0, "beneficiary-like 복지로 detail raw payload 가 있어야 density smoke 를 실행할 수 있음");

        long beforeTermRows = countBeneficiaryTermRows();
        long beforeServices = countBeneficiaryTermServices();

        NormalizedPolicySidecarBackfillService.BackfillResult detailResult =
                backfillService.backfillBokjiroDetailSidecars(0);

        long afterTermRows = countBeneficiaryTermRows();
        long afterServices = countBeneficiaryTermServices();
        long basicLivelihoodTerms = count("""
                SELECT COUNT(*)
                FROM service_taxonomy_terms st
                JOIN welfare_services ws ON ws.id = st.service_id
                WHERE ws.source_type IN ('BOKJIRO_CENTRAL', 'BOKJIRO_LOCAL')
                  AND st.term_group = 'TARGET_GROUP'
                  AND st.source_field = 'targetDetail/selectionCriteria'
                  AND st.term_label = '기초생활수급자'
                """);
        long nearPoorTerms = count("""
                SELECT COUNT(*)
                FROM service_taxonomy_terms st
                JOIN welfare_services ws ON ws.id = st.service_id
                WHERE ws.source_type IN ('BOKJIRO_CENTRAL', 'BOKJIRO_LOCAL')
                  AND st.term_group = 'TARGET_GROUP'
                  AND st.source_field = 'targetDetail/selectionCriteria'
                  AND st.term_label = '차상위계층'
                """);

        assertThat(detailResult.scannedCount()).isGreaterThan(0);
        assertThat(detailResult.upsertedCount()).isGreaterThan(0);
        assertThat(afterTermRows).isGreaterThanOrEqualTo(beforeTermRows);
        assertThat(afterServices).isGreaterThanOrEqualTo(beforeServices);
        assertThat(afterTermRows).isGreaterThan(0);
        assertThat(afterServices).isGreaterThan(0);
        assertThat(basicLivelihoodTerms + nearPoorTerms).isEqualTo(afterTermRows);
        assertThat(basicLivelihoodTerms).isGreaterThan(0);
    }

    private boolean tableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = DATABASE()
                  AND table_name = ?
                """, Integer.class, tableName);
        return count != null && count > 0;
    }

    private long countBeneficiaryTermRows() {
        return count("""
                SELECT COUNT(*)
                FROM service_taxonomy_terms st
                JOIN welfare_services ws ON ws.id = st.service_id
                WHERE ws.source_type IN ('BOKJIRO_CENTRAL', 'BOKJIRO_LOCAL')
                  AND st.term_group = 'TARGET_GROUP'
                  AND st.source_field = 'targetDetail/selectionCriteria'
                  AND st.term_label IN ('기초생활수급자', '차상위계층')
                """);
    }

    private long countBeneficiaryTermServices() {
        return count("""
                SELECT COUNT(DISTINCT st.service_id)
                FROM service_taxonomy_terms st
                JOIN welfare_services ws ON ws.id = st.service_id
                WHERE ws.source_type IN ('BOKJIRO_CENTRAL', 'BOKJIRO_LOCAL')
                  AND st.term_group = 'TARGET_GROUP'
                  AND st.source_field = 'targetDetail/selectionCriteria'
                  AND st.term_label IN ('기초생활수급자', '차상위계층')
                """);
    }

    private long count(String sql) {
        Long count = jdbcTemplate.queryForObject(sql, Long.class);
        return count == null ? 0L : count;
    }
}
