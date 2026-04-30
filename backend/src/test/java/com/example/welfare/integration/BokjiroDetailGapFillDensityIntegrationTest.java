package com.example.welfare.integration;

import com.example.welfare.collect.service.BokjiroDetailCollectService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@SpringBootTest(properties = {
        "collect.detail.request-interval-ms=0",
        "collect.detail.retry.max-attempts=1",
        "collect.detail.retry.base-backoff-ms=0",
        "collect.detail.max-consecutive-rate-limit-hits=1"
})
@ActiveProfiles("integration")
class BokjiroDetailGapFillDensityIntegrationTest {

    @Autowired
    private BokjiroDetailCollectService bokjiroDetailCollectService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("local live Bokjiro gap fill 실행 후 stored detail payload coverage 와 sidecar density 는 감소하지 않는다")
    void gapFillImprovesOrPreservesLocalCoverage() {
        assumeTrue(hasLiveBokjiroApiKey(), "실제 BOKJIRO_API_KEY 가 있는 local 환경에서만 실행");
        assumeTrue(tableExists("service_facts"), "draft sidecar migration 이 적용된 local DB 에서만 실행");

        long bokjiroServiceCount = count("""
                SELECT COUNT(*)
                FROM welfare_services
                WHERE source_type IN ('BOKJIRO_CENTRAL', 'BOKJIRO_LOCAL')
                """);
        long beforeDetailPayloadCount = count("""
                SELECT COUNT(*)
                FROM raw_api_payloads
                WHERE source_type IN ('BOKJIRO_CENTRAL', 'BOKJIRO_LOCAL')
                  AND api_category = 'DETAIL'
                """);
        long beforeMissingDetailPayloadServices = count("""
                SELECT COUNT(DISTINCT ws.id)
                FROM welfare_services ws
                LEFT JOIN raw_api_payloads rp
                  ON rp.source_type = ws.source_type
                 AND rp.source_id = ws.source_id
                 AND rp.api_category = 'DETAIL'
                WHERE ws.source_type IN ('BOKJIRO_CENTRAL', 'BOKJIRO_LOCAL')
                  AND rp.id IS NULL
                """);
        long beforeFacts = countBokjiroFacts();
        long beforeServicesWithFacts = countBokjiroServicesWithFacts();

        assumeTrue(bokjiroServiceCount > 0, "복지로 적재 데이터가 있어야 gap fill smoke 를 실행할 수 있음");
        assumeTrue(beforeMissingDetailPayloadServices > 0, "missing detail backlog 가 있어야 gap fill smoke 를 실행할 수 있음");

        BokjiroDetailCollectService.GapFillResult result =
                bokjiroDetailCollectService.collectBokjiroDetailGapFillResult(2, 20);

        long afterDetailPayloadCount = count("""
                SELECT COUNT(*)
                FROM raw_api_payloads
                WHERE source_type IN ('BOKJIRO_CENTRAL', 'BOKJIRO_LOCAL')
                  AND api_category = 'DETAIL'
                """);
        long afterMissingDetailPayloadServices = count("""
                SELECT COUNT(DISTINCT ws.id)
                FROM welfare_services ws
                LEFT JOIN raw_api_payloads rp
                  ON rp.source_type = ws.source_type
                 AND rp.source_id = ws.source_id
                 AND rp.api_category = 'DETAIL'
                WHERE ws.source_type IN ('BOKJIRO_CENTRAL', 'BOKJIRO_LOCAL')
                  AND rp.id IS NULL
                """);
        long afterFacts = countBokjiroFacts();
        long afterServicesWithFacts = countBokjiroServicesWithFacts();

        assertThat(result.roundsExecuted()).isGreaterThan(0);
        assertThat(result.requestedCount()).isGreaterThanOrEqualTo(result.savedCount());
        assertThat(afterDetailPayloadCount).isGreaterThanOrEqualTo(beforeDetailPayloadCount);
        assertThat(afterMissingDetailPayloadServices).isLessThanOrEqualTo(beforeMissingDetailPayloadServices);
        assertThat(afterFacts).isGreaterThanOrEqualTo(beforeFacts);
        assertThat(afterServicesWithFacts).isGreaterThanOrEqualTo(beforeServicesWithFacts);

        if (result.savedCount() > 0) {
            assertThat(afterDetailPayloadCount).isGreaterThan(beforeDetailPayloadCount);
            assertThat(afterMissingDetailPayloadServices).isLessThan(beforeMissingDetailPayloadServices);
        }
    }

    private boolean hasLiveBokjiroApiKey() {
        String apiKey = System.getenv("BOKJIRO_API_KEY");
        return apiKey != null
                && !apiKey.isBlank()
                && !"test-bokjiro-api-key".equals(apiKey);
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
