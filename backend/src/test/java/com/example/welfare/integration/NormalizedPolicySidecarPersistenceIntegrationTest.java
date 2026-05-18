package com.example.welfare.integration;

import com.example.welfare.collect.dto.YouthApiDto;
import com.example.welfare.collect.mapper.WelfareServiceMapper;
import com.example.welfare.collect.normalization.NormalizedPolicyAggregate;
import com.example.welfare.collect.service.CollectItemSaver;
import com.example.welfare.collect.support.ListCollectSourceBindings;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.repository.WelfareServiceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("integration")
class NormalizedPolicySidecarPersistenceIntegrationTest {

    private static final String TEST_SOURCE_PREFIX = "IT-SIDECAR-";

    @Autowired
    private CollectItemSaver collectItemSaver;

    @Autowired
    private WelfareServiceMapper welfareServiceMapper;

    @Autowired
    private WelfareServiceRepository welfareServiceRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String sourceId;

    @BeforeEach
    void setup() {
        cleanup();
    }

    @AfterEach
    void cleanup() {
        welfareServiceRepository.findBySourceType(WelfareService.SourceType.YOUTH).stream()
                .filter(service -> service.getSourceId() != null && service.getSourceId().startsWith(TEST_SOURCE_PREFIX))
                .forEach(service -> {
                    Long serviceId = service.getId();
                    jdbcTemplate.update("DELETE FROM service_facts WHERE service_id = ?", serviceId);
                    jdbcTemplate.update("DELETE FROM service_taxonomy_terms WHERE service_id = ?", serviceId);
                    jdbcTemplate.update("DELETE FROM service_taxonomies WHERE service_id = ?", serviceId);
                    jdbcTemplate.update("DELETE FROM service_tags WHERE service_id = ?", serviceId);
                    jdbcTemplate.update("DELETE FROM service_regions WHERE service_id = ?", serviceId);
                    jdbcTemplate.update("DELETE FROM welfare_services WHERE id = ?", serviceId);
                });
    }

    @Test
    @DisplayName("local PostgreSQL sidecar tables 에 collect save path 가 taxonomy/facts upsert 를 실제로 반영한다")
    void saveYouthOncePersistsAndRefreshesSidecars() {
        sourceId = TEST_SOURCE_PREFIX + UUID.randomUUID();

        YouthApiDto.Item initialItem = youthItem(
                sourceId,
                "일자리",
                "취업,문화활동 및 생활지원",
                "청년일자리,직무훈련",
                "0013003,0013006",
                "0049005,0049006",
                "0014003,0014008",
                19,
                34,
                0,
                500,
                "20260401 ~ 20260531",
                "0043001"
        );

        NormalizedPolicyAggregate initialAggregate = welfareServiceMapper.toNormalizedYouth(initialItem);
        collectItemSaver.save(ListCollectSourceBindings.youth(welfareServiceMapper), initialItem, initialAggregate);

        YouthApiDto.Item refreshedItem = youthItem(
                sourceId,
                "일자리",
                "재직자",
                "청년일자리",
                "0013001",
                "0049010",
                "0014010",
                20,
                39,
                0,
                700,
                "20260401 ~ 20260630",
                "0043002"
        );

        NormalizedPolicyAggregate refreshedAggregate = welfareServiceMapper.toNormalizedYouth(refreshedItem);
        collectItemSaver.save(ListCollectSourceBindings.youth(welfareServiceMapper), refreshedItem, refreshedAggregate);

        WelfareService saved = welfareServiceRepository
                .findBySourceTypeAndSourceId(WelfareService.SourceType.YOUTH, sourceId)
                .orElseThrow();

        Map<String, Object> taxonomySummary = jdbcTemplate.queryForMap("""
                SELECT compat_unified_category_label,
                       youth_major_label,
                       youth_mid_label
                FROM service_taxonomies
                WHERE service_id = ?
                """, saved.getId());

        assertThat(taxonomySummary.get("compat_unified_category_label")).isEqualTo("일자리");
        assertThat(taxonomySummary.get("youth_major_label")).isEqualTo("일자리");
        assertThat(taxonomySummary.get("youth_mid_label")).isEqualTo("재직자");

        List<Map<String, Object>> taxonomyTerms = jdbcTemplate.queryForList("""
                SELECT term_group, term_label
                FROM service_taxonomy_terms
                WHERE service_id = ?
                ORDER BY term_group, sort_order
                """, saved.getId());

        Map<String, List<String>> termsByGroup = taxonomyTerms.stream()
                .collect(Collectors.groupingBy(
                        row -> (String) row.get("term_group"),
                        Collectors.mapping(row -> (String) row.get("term_label"), Collectors.toList())
                ));

        assertThat(termsByGroup.get("YOUTH_MAJOR")).containsExactly("일자리");
        assertThat(termsByGroup.get("YOUTH_MID")).containsExactly("재직자");
        assertThat(termsByGroup).doesNotContainKey("YOUTH_MID_RAW_ALIAS");
        assertThat(termsByGroup.get("YOUTH_KEYWORD")).containsExactly("청년일자리");

        List<Map<String, Object>> facts = jdbcTemplate.queryForList("""
                SELECT fact_merge_key, fact_code_set_key, fact_code, operator, int_value, text_value, range_min_int, range_max_int,
                       TO_CHAR(date_value, 'YYYY-MM-DD') AS date_value
                FROM service_facts
                WHERE service_id = ?
                ORDER BY fact_merge_key
                """, saved.getId());

        assertThat(facts).hasSize(8);
        assertThat(facts).anySatisfy(row -> {
            assertThat(row.get("fact_merge_key")).isEqualTo("YOUTH_AGE_ELIGIBILITY");
            assertThat(row.get("operator")).isEqualTo("RANGE");
            assertThat(((Number) row.get("range_min_int")).intValue()).isEqualTo(20);
            assertThat(((Number) row.get("range_max_int")).intValue()).isEqualTo(39);
        });
        assertThat(facts).anySatisfy(row -> {
            assertThat(row.get("fact_merge_key")).isEqualTo("YOUTH_INCOME_MAX");
            assertThat(row.get("operator")).isEqualTo("LTE");
            assertThat(((Number) row.get("int_value")).intValue()).isEqualTo(700);
        });
        assertThat(facts).anySatisfy(row -> {
            assertThat(row.get("fact_merge_key")).isEqualTo("YOUTH_APPLY_END_DATE");
            assertThat(row.get("operator")).isEqualTo("EQ");
            assertThat(row.get("date_value")).isEqualTo(LocalDate.of(2026, 6, 30).toString());
        });
        assertThat(facts).anySatisfy(row -> {
            assertThat(row.get("fact_merge_key")).isEqualTo("YOUTH_EMPLOYMENT_REQUIREMENT");
            assertThat(row.get("fact_code_set_key")).isEqualTo("YOUTH_EMPLOYMENT_REQUIREMENT");
            assertThat(row.get("fact_code")).isEqualTo("0013001");
            assertThat(row.get("operator")).isEqualTo("MEMBER");
            assertThat(row.get("text_value")).isEqualTo("재직자");
        });
        assertThat(facts).anySatisfy(row -> {
            assertThat(row.get("fact_merge_key")).isEqualTo("YOUTH_EDUCATION_REQUIREMENT");
            assertThat(row.get("fact_code_set_key")).isEqualTo("YOUTH_EDUCATION_REQUIREMENT");
            assertThat(row.get("fact_code")).isEqualTo("0049005,0049006");
            assertThat(row.get("operator")).isEqualTo("MEMBER");
            assertThat(row.get("text_value")).isEqualTo("대학 재학, 대졸 예정");
        });
        assertThat(facts).anySatisfy(row -> {
            assertThat(row.get("fact_merge_key")).isEqualTo("YOUTH_INCOME_CONDITION_TYPE");
            assertThat(row.get("fact_code_set_key")).isEqualTo("YOUTH_INCOME_CONDITION_TYPE");
            assertThat(row.get("fact_code")).isEqualTo("0043002");
            assertThat(row.get("operator")).isEqualTo("EQ");
            assertThat(row.get("text_value")).isEqualTo("연소득");
        });
        assertThat(facts).anySatisfy(row -> {
            assertThat(row.get("fact_merge_key")).isEqualTo("YOUTH_SPECIAL_REQUIREMENT");
            assertThat(row.get("fact_code_set_key")).isEqualTo("YOUTH_SPECIAL_REQUIREMENT");
            assertThat(row.get("fact_code")).isEqualTo("0014010");
            assertThat(row.get("operator")).isEqualTo("MEMBER");
            assertThat(row.get("text_value")).isEqualTo("제한없음");
        });
    }

    private YouthApiDto.Item youthItem(String sourceId,
                                       String major,
                                       String mid,
                                       String keywords,
                                       String employmentRequirementCodes,
                                       String educationRequirementCodes,
                                       String specialRequirementCodes,
                                       Integer minAge,
                                       Integer maxAge,
                                       Integer minIncome,
                                       Integer maxIncome,
                                       String applyRange,
                                       String incomeConditionTypeCode) {
        YouthApiDto.Item item = new YouthApiDto.Item();
        ReflectionTestUtils.setField(item, "plcyNo", sourceId);
        ReflectionTestUtils.setField(item, "plcyNm", "청년 정책 sidecar smoke");
        ReflectionTestUtils.setField(item, "plcyExplnCn", "sidecar smoke description");
        ReflectionTestUtils.setField(item, "plcySprtCn", "sidecar smoke support");
        ReflectionTestUtils.setField(item, "lclsfNm", major);
        ReflectionTestUtils.setField(item, "mclsfNm", mid);
        ReflectionTestUtils.setField(item, "plcyKywdNm", keywords);
        ReflectionTestUtils.setField(item, "jobCd", employmentRequirementCodes);
        ReflectionTestUtils.setField(item, "schoolCd", educationRequirementCodes);
        ReflectionTestUtils.setField(item, "sbizCd", specialRequirementCodes);
        ReflectionTestUtils.setField(item, "sprvsnInstCdNm", "고용노동부");
        ReflectionTestUtils.setField(item, "operInstCdNm", "청년센터");
        ReflectionTestUtils.setField(item, "sprtTrgtMinAge", minAge);
        ReflectionTestUtils.setField(item, "sprtTrgtMaxAge", maxAge);
        ReflectionTestUtils.setField(item, "earnMinAmt", minIncome);
        ReflectionTestUtils.setField(item, "earnMaxAmt", maxIncome);
        ReflectionTestUtils.setField(item, "earnCndSeCd", incomeConditionTypeCode);
        ReflectionTestUtils.setField(item, "bizPrdBgngYmd", "20260401");
        ReflectionTestUtils.setField(item, "bizPrdEndYmd", "20261231");
        ReflectionTestUtils.setField(item, "aplyYmd", applyRange);
        ReflectionTestUtils.setField(item, "plcyAplyMthdCn", "온라인 신청");
        ReflectionTestUtils.setField(item, "aplyUrlAddr", "https://example.com/policies/" + sourceId);
        ReflectionTestUtils.setField(item, "zipCd", "11000,26000");
        ReflectionTestUtils.setField(item, "inqCnt", 10L);
        ReflectionTestUtils.setField(item, "frstRegDt", "2026-04-30 12:30:45");
        ReflectionTestUtils.setField(item, "lastMdfcnDt", "2026-04-30 13:30:45");
        return item;
    }
}
