package com.example.welfare.integration;

import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.repository.WelfareServiceRepository;
import com.example.welfare.recommend.dto.RecommendationCandidateProjection;
import com.example.welfare.recommend.repository.CanonicalRecommendationReadModelRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("integration")
class CanonicalRecommendationReadModelIntegrationTest {

    private static final String TEST_SOURCE_PREFIX = "IT-READMODEL-";

    @Autowired
    private WelfareServiceRepository welfareServiceRepository;

    @Autowired
    private CanonicalRecommendationReadModelRepository canonicalRecommendationReadModelRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long createdServiceId;

    @BeforeEach
    void setup() {
        cleanup();
    }

    @AfterEach
    void cleanup() {
        List<Long> testServiceIds = welfareServiceRepository.findAll().stream()
                .filter(service -> service.getSourceId() != null && service.getSourceId().startsWith(TEST_SOURCE_PREFIX))
                .map(WelfareService::getId)
                .toList();

        if (createdServiceId != null && !testServiceIds.contains(createdServiceId)) {
            testServiceIds = new java.util.ArrayList<>(testServiceIds);
            testServiceIds.add(createdServiceId);
        }

        if (testServiceIds.isEmpty()) {
            createdServiceId = null;
            return;
        }
        for (Long serviceId : testServiceIds) {
            jdbcTemplate.update("DELETE FROM service_taxonomy_summary_slots WHERE service_id = ?", serviceId);
            jdbcTemplate.update("DELETE FROM service_taxonomies WHERE service_id = ?", serviceId);
            jdbcTemplate.update("DELETE FROM service_taxonomy_terms WHERE service_id = ?", serviceId);
            jdbcTemplate.update("DELETE FROM service_facts WHERE service_id = ?", serviceId);
            jdbcTemplate.update("DELETE FROM welfare_service_details WHERE service_id = ?", serviceId);
            jdbcTemplate.update("DELETE FROM service_tags WHERE service_id = ?", serviceId);
            jdbcTemplate.update("DELETE FROM service_regions WHERE service_id = ?", serviceId);
            jdbcTemplate.update("DELETE FROM welfare_services WHERE id = ?", serviceId);
        }
        createdServiceId = null;
    }

    @Test
    @DisplayName("canonical read-model은 summary slot이 있으면 GOV24 축도 slot-first로 읽고, 없는 축은 legacy summary로 fallback한다")
    void findByServiceIds_prefersGov24SlotsAndFallsBackPerField() {
        WelfareService service = welfareServiceRepository.save(WelfareService.builder()
                .sourceType(WelfareService.SourceType.BOKJIRO_CENTRAL)
                .sourceId(TEST_SOURCE_PREFIX + UUID.randomUUID().toString().substring(0, 8))
                .title("복지로 정책 read-model smoke")
                .description("legacy와 slot 우선순위 확인")
                .status(WelfareService.ServiceStatus.ACTIVE)
                .unifiedCategory("기타")
                .applyMethodName("legacy apply method")
                .minAge(19)
                .maxAge(34)
                .minIncome(0)
                .maxIncome(0)
                .apiViewCount(0L)
                .searchYouthRelevant(true)
                .build());
        createdServiceId = service.getId();

        jdbcTemplate.update("""
                INSERT INTO service_taxonomies (
                    service_id,
                    primary_source_system,
                    compat_unified_category_label,
                    youth_major_label,
                    youth_mid_label,
                    provision_method_label,
                    gov24_service_field_label,
                    gov24_user_type_label,
                    gov24_benefit_type_label,
                    authority
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                createdServiceId,
                "BOKJIRO",
                "주거",
                "legacy-major",
                "legacy-mid",
                "legacy-provision",
                "legacy-service-field",
                "legacy-user-type",
                "legacy-benefit-type",
                "OFFICIAL");

        insertSummarySlot(createdServiceId, "YOUTH_MAJOR", "slot-major");
        insertSummarySlot(createdServiceId, "PROVISION_METHOD", "slot-provision");
        insertSummarySlot(createdServiceId, "GOV24_SERVICE_FIELD", "slot-service-field");
        insertSummarySlot(createdServiceId, "GOV24_BENEFIT_TYPE", "slot-benefit-type");
        jdbcTemplate.update("""
                INSERT INTO service_facts (
                    service_id,
                    fact_group,
                    fact_code_set_key,
                    fact_code,
                    fact_merge_key,
                    fact_label,
                    operator,
                    value_type,
                    text_value,
                    source_field,
                    authority,
                    confidence,
                    raw_value,
                    evidence_text
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                createdServiceId,
                "INCOME",
                "YOUTH_INCOME_CONDITION_TYPE",
                "0043002",
                "YOUTH_INCOME_CONDITION_TYPE",
                "소득조건 구분",
                "EQ",
                "STRING",
                "연소득",
                "earnCndSeCd",
                "OFFICIAL",
                1.0,
                "0043002",
                "연소득");
        jdbcTemplate.update("""
                INSERT INTO service_facts (
                    service_id,
                    fact_group,
                    fact_code_set_key,
                    fact_code,
                    fact_merge_key,
                    fact_label,
                    operator,
                    value_type,
                    text_value,
                    source_field,
                    authority,
                    confidence,
                    raw_value,
                    evidence_text
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                createdServiceId,
                "MARITAL_STATUS",
                "YOUTH_MARITAL_STATUS",
                "0055003",
                "YOUTH_MARITAL_STATUS",
                "결혼 상태",
                "EQ",
                "STRING",
                "제한없음",
                "mrgSttsCd",
                "OFFICIAL",
                1.0,
                "0055003",
                "제한없음");
        jdbcTemplate.update("""
                INSERT INTO service_facts (
                    service_id,
                    fact_group,
                    fact_code_set_key,
                    fact_code,
                    fact_merge_key,
                    fact_label,
                    operator,
                    value_type,
                    text_value,
                    source_field,
                    authority,
                    confidence,
                    raw_value,
                    evidence_text
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                createdServiceId,
                "EMPLOYMENT",
                "YOUTH_EMPLOYMENT_REQUIREMENT",
                "0013003,0013006",
                "YOUTH_EMPLOYMENT_REQUIREMENT",
                "취업 요건",
                "MEMBER",
                "STRING",
                "미취업자, (예비)창업자",
                "jobCd",
                "OFFICIAL",
                1.0,
                "0013003,0013006",
                "미취업자, (예비)창업자");
        jdbcTemplate.update("""
                INSERT INTO service_facts (
                    service_id,
                    fact_group,
                    fact_code_set_key,
                    fact_code,
                    fact_merge_key,
                    fact_label,
                    operator,
                    value_type,
                    text_value,
                    source_field,
                    authority,
                    confidence,
                    raw_value,
                    evidence_text
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                createdServiceId,
                "EDUCATION",
                "YOUTH_EDUCATION_REQUIREMENT",
                "0049005,0049006",
                "YOUTH_EDUCATION_REQUIREMENT",
                "학력 요건",
                "MEMBER",
                "STRING",
                "대학 재학, 대졸 예정",
                "schoolCd",
                "OFFICIAL",
                1.0,
                "0049005,0049006",
                "대학 재학, 대졸 예정");
        jdbcTemplate.update("""
                INSERT INTO service_facts (
                    service_id,
                    fact_group,
                    fact_code_set_key,
                    fact_code,
                    fact_merge_key,
                    fact_label,
                    operator,
                    value_type,
                    text_value,
                    source_field,
                    authority,
                    confidence,
                    raw_value,
                    evidence_text
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                createdServiceId,
                "SPECIAL_REQUIREMENT",
                "YOUTH_SPECIAL_REQUIREMENT",
                "0014003,0014008",
                "YOUTH_SPECIAL_REQUIREMENT",
                "특화 요건",
                "MEMBER",
                "STRING",
                "기초생활수급자, 지역인재",
                "sbizCd",
                "OFFICIAL",
                1.0,
                "0014003,0014008",
                "기초생활수급자, 지역인재");

        Map<Long, RecommendationCandidateProjection> projections =
                canonicalRecommendationReadModelRepository.findByServiceIds(List.of(createdServiceId));

        assertThat(projections).containsOnlyKeys(createdServiceId);
        RecommendationCandidateProjection projection = projections.get(createdServiceId);
        assertThat(projection.unifiedCategoryCompat()).isEqualTo("기타");
        assertThat(projection.youthMajorLabel()).isEqualTo("slot-major");
        assertThat(projection.youthMidLabel()).isEqualTo("legacy-mid");
        assertThat(projection.provisionMethodLabel()).isEqualTo("slot-provision");
        assertThat(projection.gov24ServiceFieldLabel()).isEqualTo("slot-service-field");
        assertThat(projection.gov24UserTypeLabel()).isEqualTo("legacy-user-type");
        assertThat(projection.gov24BenefitTypeLabel()).isEqualTo("slot-benefit-type");
        assertThat(projection.youthEmploymentRequirementCodes()).containsExactly("0013003", "0013006");
        assertThat(projection.youthEmploymentRequirementLabels()).containsExactly("미취업자", "(예비)창업자");
        assertThat(projection.youthEducationRequirementCodes()).containsExactly("0049005", "0049006");
        assertThat(projection.youthEducationRequirementLabels()).containsExactly("대학 재학", "대졸 예정");
        assertThat(projection.youthSpecialRequirementCodes()).containsExactly("0014003", "0014008");
        assertThat(projection.youthSpecialRequirementLabels()).containsExactly("기초생활수급자", "지역인재");
        assertThat(projection.youthMaritalStatusCode()).isEqualTo("0055003");
        assertThat(projection.youthMaritalStatusLabel()).isEqualTo("제한없음");
        assertThat(projection.youthIncomeConditionTypeCode()).isEqualTo("0043002");
        assertThat(projection.youthIncomeConditionTypeLabel()).isEqualTo("연소득");
        assertThat(projection.factKeys()).contains("YOUTH_MARITAL_STATUS");
        assertThat(projection.factKeys()).contains("YOUTH_EDUCATION_REQUIREMENT");
        assertThat(projection.factKeys()).contains("YOUTH_EMPLOYMENT_REQUIREMENT");
        assertThat(projection.factKeys()).contains("YOUTH_SPECIAL_REQUIREMENT");
        assertThat(projection.factKeys()).contains("YOUTH_INCOME_CONDITION_TYPE");
    }

    private void insertSummarySlot(Long serviceId, String slotKey, String slotLabel) {
        jdbcTemplate.update("""
                INSERT INTO service_taxonomy_summary_slots (
                    service_id,
                    slot_key,
                    code_set_key,
                    slot_code,
                    slot_label,
                    source_field,
                    authority,
                    confidence
                ) VALUES (?, ?, NULL, '', ?, '', 'OFFICIAL', 1.000)
                """, serviceId, slotKey, slotLabel);
    }
}
