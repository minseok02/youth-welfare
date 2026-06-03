package com.example.welfare.integration;

import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.repository.WelfareServiceRepository;
import com.example.welfare.recommend.dto.RecommendationUserSnapshot;
import com.example.welfare.recommend.dto.RetrievedRecommendationCandidates;
import com.example.welfare.recommend.service.RetrievalService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@SpringBootTest
@ActiveProfiles("integration")
class EducationPriorityTargetCandidateCompositionIntegrationTest {

    private static final int USER_AGE = 25;
    private static final int USER_INCOME_LEVEL = 5;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private WelfareServiceRepository welfareServiceRepository;

    @Autowired
    private RetrievalService retrievalService;

    @Test
    @DisplayName("대표 education target region 은 0/0 income pass-through 적용 후 raw candidates 와 retrieval 결과에 target row가 들어온다")
    void representativeEducationTargetRegionSurvivesIncomeGate() {
        assumeTrue(tableExists("service_taxonomies"), "canonical sidecar summary 가 있는 local DB 에서만 실행");
        String youthMajorJoin = youthMajorJoinSql();
        String youthMajorFilter = youthMajorFilterSql();

        String representativeRegion = jdbcTemplate.queryForList("""
                SELECT sr.region_code
                FROM welfare_services ws
                JOIN service_taxonomies st ON st.service_id = ws.id
                %s
                JOIN service_regions sr ON sr.service_id = ws.id
                WHERE ws.unified_category = '기타'
                  AND %s
                  AND ws.status IN ('ACTIVE', 'UPCOMING')
                  AND ws.min_age <= ?
                  AND ws.max_age >= ?
                GROUP BY sr.region_code
                HAVING COUNT(DISTINCT ws.id) > 0
                   AND COUNT(DISTINCT CASE
                       WHEN ws.source_type = 'YOUTH' AND (
                           (ws.min_income IS NULL AND ws.max_income IS NULL)
                           OR (ws.min_income = 0 AND ws.max_income = 0)
                           OR (ws.min_income <= ? AND ws.max_income >= ?)
                       )
                       THEN ws.id END) = 0
                ORDER BY COUNT(DISTINCT ws.id) DESC, sr.region_code
                LIMIT 1
                """.formatted(youthMajorJoin, youthMajorFilter), String.class, USER_AGE, USER_AGE, USER_INCOME_LEVEL, USER_INCOME_LEVEL)
                .stream()
                .findFirst()
                .orElse(null);

        if (representativeRegion == null || representativeRegion.isBlank()) {
            representativeRegion = jdbcTemplate.queryForList("""
                    SELECT sr.region_code
                    FROM welfare_services ws
                    JOIN service_taxonomies st ON st.service_id = ws.id
                    %s
                    JOIN service_regions sr ON sr.service_id = ws.id
                    WHERE ws.unified_category = '기타'
                      AND %s
                      AND ws.status IN ('ACTIVE', 'UPCOMING')
                      AND ws.min_age <= ?
                      AND ws.max_age >= ?
                    GROUP BY sr.region_code
                    HAVING COUNT(DISTINCT CASE
                           WHEN ws.source_type = 'YOUTH' AND (
                               (ws.min_income IS NULL AND ws.max_income IS NULL)
                               OR (ws.min_income = 0 AND ws.max_income = 0)
                               OR (ws.min_income <= ? AND ws.max_income >= ?)
                           )
                           THEN ws.id END) > 0
                    ORDER BY COUNT(DISTINCT CASE
                           WHEN ws.source_type = 'YOUTH' AND (
                               (ws.min_income IS NULL AND ws.max_income IS NULL)
                               OR (ws.min_income = 0 AND ws.max_income = 0)
                               OR (ws.min_income <= ? AND ws.max_income >= ?)
                           )
                           THEN ws.id END) DESC, sr.region_code
                    LIMIT 1
                    """.formatted(youthMajorJoin, youthMajorFilter), String.class,
                    USER_AGE, USER_AGE,
                    USER_INCOME_LEVEL, USER_INCOME_LEVEL,
                    USER_INCOME_LEVEL, USER_INCOME_LEVEL)
                    .stream()
                    .findFirst()
                    .orElse(null);
        }

        assumeTrue(representativeRegion != null && !representativeRegion.isBlank(),
                "0/0 pass-through 적용 후 target row 가 남는 대표 region 이 있어야 함");

        Set<Long> targetServiceIds = jdbcTemplate.queryForList("""
                SELECT DISTINCT ws.id
                FROM welfare_services ws
                JOIN service_taxonomies st ON st.service_id = ws.id
                %s
                JOIN service_regions sr ON sr.service_id = ws.id
                WHERE ws.unified_category = '기타'
                  AND %s
                  AND ws.status IN ('ACTIVE', 'UPCOMING')
                  AND ws.min_age <= ?
                  AND ws.max_age >= ?
                  AND sr.region_code = ?
                """.formatted(youthMajorJoin, youthMajorFilter), Long.class, USER_AGE, USER_AGE, representativeRegion).stream().collect(Collectors.toSet());

        Long effectiveIncomePassCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(DISTINCT ws.id)
                FROM welfare_services ws
                JOIN service_taxonomies st ON st.service_id = ws.id
                %s
                JOIN service_regions sr ON sr.service_id = ws.id
                WHERE ws.unified_category = '기타'
                  AND %s
                  AND ws.status IN ('ACTIVE', 'UPCOMING')
                  AND ws.min_age <= ?
                  AND ws.max_age >= ?
                  AND ws.source_type = 'YOUTH'
                  AND (
                      (ws.min_income IS NULL AND ws.max_income IS NULL)
                      OR (ws.min_income = 0 AND ws.max_income = 0)
                      OR (ws.min_income <= ? AND ws.max_income >= ?)
                  )
                  AND sr.region_code = ?
                """.formatted(youthMajorJoin, youthMajorFilter), Long.class, USER_AGE, USER_AGE, USER_INCOME_LEVEL, USER_INCOME_LEVEL, representativeRegion);

        String representativeSido = jdbcTemplate.queryForObject("""
                SELECT MIN(sido_name)
                FROM service_regions
                WHERE region_code = ?
                """, String.class, representativeRegion);

        List<WelfareService> rawCandidates = welfareServiceRepository.findCandidatesWithRegionCode(
                USER_AGE,
                USER_INCOME_LEVEL,
                representativeRegion,
                representativeSido,
                null,
                PageRequest.of(0, 150)
        );
        long rawTargetHits = rawCandidates.stream()
                .map(WelfareService::getId)
                .filter(targetServiceIds::contains)
                .count();

        RetrievedRecommendationCandidates retrieved = retrievalService.retrieve("youth_all", sampleUser(representativeRegion));
        long retrievedTargetHits = retrieved.candidates().stream()
                .map(WelfareService::getId)
                .filter(targetServiceIds::contains)
                .count();

        assertThat(targetServiceIds).isNotEmpty();
        assertThat(effectiveIncomePassCount).isGreaterThan(0);
        assertThat(rawTargetHits).isGreaterThan(0);
        assertThat(retrievedTargetHits).isGreaterThan(0);
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

    private String youthMajorJoinSql() {
        if (!tableExists("service_taxonomy_summary_slots")) {
            return "";
        }
        return """
                LEFT JOIN (
                    SELECT service_id, MAX(slot_label) AS slot_label
                    FROM service_taxonomy_summary_slots
                    WHERE slot_key = 'YOUTH_MAJOR'
                    GROUP BY service_id
                ) stss_youth_major ON stss_youth_major.service_id = ws.id
                """;
    }

    private String youthMajorFilterSql() {
        if (!tableExists("service_taxonomy_summary_slots")) {
            return "st.youth_major_label = '교육'";
        }
        return "COALESCE(stss_youth_major.slot_label, st.youth_major_label) = '교육'";
    }

    private RecommendationUserSnapshot sampleUser(String regionCode) {
        return new RecommendationUserSnapshot(
                999L,
                "education-target-scan-user",
                USER_AGE,
                "25_29",
                "sample-sido",
                "sample-sgg",
                regionCode,
                (byte) USER_INCOME_LEVEL,
                null,
                "미취업",
                30,
                0.5,
                List.of("교육"),
                List.of(),
                List.of()
        );
    }
}
