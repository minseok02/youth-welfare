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
    @DisplayName("대표 education target region 은 DB age-pass pool 이 있어도 YOUTH income gate 때문에 raw candidates 와 retrieval 결과에서 모두 빠질 수 있다")
    void representativeEducationTargetRegionFallsOutBeforeScoring() {
        assumeTrue(tableExists("service_taxonomies"), "canonical sidecar summary 가 있는 local DB 에서만 실행");

        String representativeRegion = jdbcTemplate.queryForObject("""
                SELECT sr.region_code
                FROM welfare_services ws
                JOIN service_taxonomies st ON st.service_id = ws.id
                JOIN service_regions sr ON sr.service_id = ws.id
                WHERE ws.unified_category = '기타'
                  AND st.youth_major_label = '교육'
                  AND ws.status IN ('ACTIVE', 'UPCOMING')
                  AND ws.min_age <= ?
                  AND ws.max_age >= ?
                GROUP BY sr.region_code
                HAVING COUNT(DISTINCT ws.id) > 0
                   AND COUNT(DISTINCT CASE
                       WHEN ws.source_type = 'YOUTH'
                        AND ws.min_income <= ?
                        AND ws.max_income >= ?
                       THEN ws.id END) = 0
                ORDER BY COUNT(DISTINCT ws.id) DESC, sr.region_code
                LIMIT 1
                """, String.class, USER_AGE, USER_AGE, USER_INCOME_LEVEL, USER_INCOME_LEVEL);

        assumeTrue(representativeRegion != null && !representativeRegion.isBlank(),
                "age-pass target pool 은 있지만 income gate 때문에 retrieval 전부 탈락하는 대표 region 이 있어야 함");

        Set<Long> targetServiceIds = jdbcTemplate.queryForList("""
                SELECT DISTINCT ws.id
                FROM welfare_services ws
                JOIN service_taxonomies st ON st.service_id = ws.id
                JOIN service_regions sr ON sr.service_id = ws.id
                WHERE ws.unified_category = '기타'
                  AND st.youth_major_label = '교육'
                  AND ws.status IN ('ACTIVE', 'UPCOMING')
                  AND ws.min_age <= ?
                  AND ws.max_age >= ?
                  AND sr.region_code = ?
                """, Long.class, USER_AGE, USER_AGE, representativeRegion).stream().collect(Collectors.toSet());

        Long incomePassCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(DISTINCT ws.id)
                FROM welfare_services ws
                JOIN service_taxonomies st ON st.service_id = ws.id
                JOIN service_regions sr ON sr.service_id = ws.id
                WHERE ws.unified_category = '기타'
                  AND st.youth_major_label = '교육'
                  AND ws.status IN ('ACTIVE', 'UPCOMING')
                  AND ws.min_age <= ?
                  AND ws.max_age >= ?
                  AND ws.source_type = 'YOUTH'
                  AND ws.min_income <= ?
                  AND ws.max_income >= ?
                  AND sr.region_code = ?
                """, Long.class, USER_AGE, USER_AGE, USER_INCOME_LEVEL, USER_INCOME_LEVEL, representativeRegion);

        List<WelfareService> rawCandidates = welfareServiceRepository.findCandidatesWithRegionCode(
                USER_AGE,
                USER_INCOME_LEVEL,
                representativeRegion,
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
        assertThat(incomePassCount).isZero();
        assertThat(rawTargetHits).isZero();
        assertThat(retrievedTargetHits).isZero();
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
