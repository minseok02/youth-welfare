package com.example.welfare.integration;

import com.example.welfare.admin.dashboard.repository.AdminDashboardReadRows;
import com.example.welfare.admin.dashboard.repository.AdminDashboardRecommendationReadRepository;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.repository.WelfareServiceRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("integration")
class AdminDashboardRecommendationReadRepositoryIntegrationTest {

    private static final String TEST_SOURCE_PREFIX = "IT-ADMIN-GOV24-";

    @Autowired
    private WelfareServiceRepository welfareServiceRepository;

    @Autowired
    private AdminDashboardRecommendationReadRepository adminDashboardRecommendationReadRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final List<Long> createdServiceIds = new ArrayList<>();

    @BeforeEach
    void setup() {
        cleanup();
    }

    @AfterEach
    void cleanup() {
        if (createdServiceIds.isEmpty()) {
            List<Long> discovered = welfareServiceRepository.findAll().stream()
                    .filter(service -> service.getSourceId() != null && service.getSourceId().startsWith(TEST_SOURCE_PREFIX))
                    .map(WelfareService::getId)
                    .toList();
            createdServiceIds.addAll(discovered);
        }
        if (createdServiceIds.isEmpty()) {
            return;
        }
        jdbcTemplate.update("DELETE FROM user_recommendations WHERE service_id IN (%s)".formatted(joinedIds(createdServiceIds)));
        jdbcTemplate.update("DELETE FROM service_taxonomy_terms WHERE service_id IN (%s)".formatted(joinedIds(createdServiceIds)));
        jdbcTemplate.update("DELETE FROM service_taxonomies WHERE service_id IN (%s)".formatted(joinedIds(createdServiceIds)));
        jdbcTemplate.update("DELETE FROM welfare_services WHERE id IN (%s)".formatted(joinedIds(createdServiceIds)));
        createdServiceIds.clear();
    }

    @Test
    @DisplayName("Gov24 facet 집계는 canonical term을 우선 읽고 term이 없을 때만 raw split으로 fallback한다")
    void fetchLatestBatchGov24FacetRows_prefersCanonicalTermsAndFallsBackToRawSplit() {
        WelfareService canonicalService = createGov24Service("canonical priority service");
        WelfareService fallbackService = createGov24Service("raw fallback service");

        insertGov24Summary(canonicalService.getId(), "보건·의료", "개인||가구", "현금||의료지원");
        insertGov24Summary(fallbackService.getId(), "생활안정", "소상공인||법인/시설/단체", "현금(융자)||상담/법률지원");

        insertTaxonomyTerm(canonicalService.getId(), "GOV24_USER_TYPE_TOKEN", "개인", 0);
        insertTaxonomyTerm(canonicalService.getId(), "GOV24_BENEFIT_TYPE_TOKEN", "현금(감면)", 0);

        LocalDateTime recommendedAt = LocalDateTime.of(2026, 5, 18, 12, 30);
        insertRecommendation("gov24-user-1", canonicalService.getId(), recommendedAt, new BigDecimal("0.81000"));
        insertRecommendation("gov24-user-2", fallbackService.getId(), recommendedAt, new BigDecimal("0.62000"));

        List<AdminDashboardReadRows.RecommendationFacetRow> rows =
                adminDashboardRecommendationReadRepository.fetchLatestBatchGov24FacetRows(10);

        Map<String, List<String>> labelsByFacet = rows.stream()
                .collect(Collectors.groupingBy(
                        AdminDashboardReadRows.RecommendationFacetRow::facetKey,
                        Collectors.mapping(AdminDashboardReadRows.RecommendationFacetRow::bucketLabel, Collectors.toList())
                ));

        assertThat(labelsByFacet.get("GOV24_USER_TYPE_TOKEN"))
                .containsExactlyInAnyOrder("개인", "소상공인", "법인/시설/단체")
                .doesNotContain("가구");
        assertThat(labelsByFacet.get("GOV24_BENEFIT_TYPE_TOKEN"))
                .containsExactlyInAnyOrder("현금(감면)", "현금(융자)", "상담/법률지원")
                .doesNotContain("현금", "의료지원");

        Map<String, Long> rowCountByFacetAndLabel = rows.stream()
                .collect(Collectors.toMap(
                        row -> row.facetKey() + "::" + row.bucketLabel(),
                        AdminDashboardReadRows.RecommendationFacetRow::rowCount
                ));
        assertThat(rowCountByFacetAndLabel.get("GOV24_USER_TYPE_TOKEN::개인")).isEqualTo(1L);
        assertThat(rowCountByFacetAndLabel.get("GOV24_BENEFIT_TYPE_TOKEN::현금(감면)")).isEqualTo(1L);
        assertThat(rowCountByFacetAndLabel.get("GOV24_USER_TYPE_TOKEN::소상공인")).isEqualTo(1L);
        assertThat(rowCountByFacetAndLabel.get("GOV24_BENEFIT_TYPE_TOKEN::현금(융자)")).isEqualTo(1L);
    }

    private WelfareService createGov24Service(String title) {
        WelfareService service = welfareServiceRepository.save(WelfareService.builder()
                .sourceType(WelfareService.SourceType.GOV24)
                .sourceId(TEST_SOURCE_PREFIX + UUID.randomUUID().toString().substring(0, 8))
                .title(title)
                .description("Gov24 facet integration test")
                .status(WelfareService.ServiceStatus.ACTIVE)
                .unifiedCategory("기타")
                .apiViewCount(0L)
                .searchYouthRelevant(false)
                .build());
        createdServiceIds.add(service.getId());
        return service;
    }

    private void insertGov24Summary(Long serviceId, String serviceField, String userType, String benefitType) {
        jdbcTemplate.update("""
                INSERT INTO service_taxonomies (
                    service_id,
                    primary_source_system,
                    compat_unified_category_label,
                    gov24_service_field_label,
                    gov24_user_type_label,
                    gov24_benefit_type_label,
                    authority
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                serviceId,
                "GOV24",
                "기타",
                serviceField,
                userType,
                benefitType,
                "OFFICIAL");
    }

    private void insertTaxonomyTerm(Long serviceId, String termGroup, String termLabel, int sortOrder) {
        jdbcTemplate.update("""
                INSERT INTO service_taxonomy_terms (
                    service_id,
                    term_group,
                    term_code,
                    term_label,
                    authority,
                    source_field,
                    sort_order
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                serviceId,
                termGroup,
                termLabel,
                termLabel,
                "OFFICIAL",
                "integration-test",
                sortOrder);
    }

    private void insertRecommendation(String userKey, Long serviceId, LocalDateTime recommendedAt, BigDecimal finalScore) {
        jdbcTemplate.update("""
                INSERT INTO user_recommendations (
                    user_key,
                    service_id,
                    recommended_at,
                    final_score,
                    ai_status,
                    rule_weight_used,
                    ai_weight_used
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                userKey,
                serviceId,
                recommendedAt,
                finalScore,
                "NOT_REQUESTED",
                new BigDecimal("1.00"),
                new BigDecimal("0.00"));
    }

    private String joinedIds(List<Long> ids) {
        return ids.stream()
                .sorted(Comparator.naturalOrder())
                .map(String::valueOf)
                .collect(Collectors.joining(","));
    }
}
