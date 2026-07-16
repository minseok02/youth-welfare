package com.example.welfare.recommend.repository;

import com.example.welfare.recommend.dto.RecommendationCandidateProjection;
import com.example.welfare.recommend.support.RecommendationProjectionHeuristicSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.jdbc.core.JdbcTemplate;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.sql.Date;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class CanonicalRecommendationReadModelRepositoryTest {

    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    private CanonicalRecommendationReadModelRepository repository;

    @BeforeEach
    void setUp() {
        repository = new CanonicalRecommendationReadModelRepository(jdbcTemplate, namedParameterJdbcTemplate);
    }

    @Test
    @DisplayName("복지로 beneficiary raw term 두 개는 projection 에서 raw 유지 + BENEFICIARY_SUPPORT bucket 1개로 dedupe 된다")
    void findByServiceIds_buildsProjectionAndDedupeBucket() {
        Map<String, Object> baseRow = new LinkedHashMap<>();
        baseRow.put("service_id", 2309L);
        baseRow.put("source_type", "BOKJIRO_CENTRAL");
        baseRow.put("unified_category", "금융·생활지원");
        baseRow.put("youth_major_label", "교육");
        baseRow.put("youth_mid_label", null);
        baseRow.put("provision_method_label", "온라인");
        baseRow.put("gov24_service_field_label", "보육");
        baseRow.put("gov24_user_type_label", "영유아");
        baseRow.put("gov24_benefit_type_label", "현금");
        baseRow.put("title", "여성청소년 생리용품 지원");
        baseRow.put("summary", "바우처 지원");
        baseRow.put("min_age", 9);
        baseRow.put("max_age", 24);
        baseRow.put("min_income", null);
        baseRow.put("max_income", null);
        baseRow.put("apply_end_date", Date.valueOf(LocalDate.of(2026, 12, 31)));
        baseRow.put("search_youth_relevant", true);

        given(jdbcTemplate.queryForObject(org.mockito.ArgumentMatchers.contains("table_name = ?"), org.mockito.ArgumentMatchers.eq(Integer.class), org.mockito.ArgumentMatchers.eq("service_taxonomy_summary_slots")))
                .willReturn(0);
        given(namedParameterJdbcTemplate.queryForList(org.mockito.ArgumentMatchers.contains("FROM welfare_services"), any(MapSqlParameterSource.class)))
                .willReturn(List.of(baseRow));
        given(namedParameterJdbcTemplate.queryForList(org.mockito.ArgumentMatchers.contains("FROM service_taxonomy_terms"), any(MapSqlParameterSource.class)))
                .willReturn(List.of(
                        Map.of(
                                "service_id", 2309L,
                                "term_group", "TARGET_GROUP",
                                "term_label", "기초생활수급자",
                                "source_field", "targetDetail/selectionCriteria"
                        ),
                        Map.of(
                                "service_id", 2309L,
                                "term_group", "TARGET_GROUP",
                                "term_label", "차상위계층",
                                "source_field", "targetDetail/selectionCriteria"
                        ),
                        Map.of(
                                "service_id", 2309L,
                                "term_group", "INTEREST_THEME",
                                "term_label", "생활지원",
                                "source_field", "intrsThemaArray"
                        )
                ));
        given(namedParameterJdbcTemplate.queryForList(org.mockito.ArgumentMatchers.contains("FROM service_facts"), any(MapSqlParameterSource.class)))
                .willReturn(List.of(
                        Map.of("service_id", 2309L, "fact_merge_key", "BK_AGE_ELIGIBILITY")
                ));

        Map<Long, RecommendationCandidateProjection> projections = repository.findByServiceIds(List.of(2309L));

        assertThat(projections).containsOnlyKeys(2309L);
        RecommendationCandidateProjection projection = projections.get(2309L);
        assertThat(projection.serviceId()).isEqualTo(2309L);
        assertThat(projection.sourceType()).isEqualTo("BOKJIRO_CENTRAL");
        assertThat(projection.unifiedCategoryCompat()).isEqualTo("금융·생활지원");
        assertThat(projection.compatCategoryCode()).isEqualTo("FINANCE_LIFE_SUPPORT");
        assertThat(projection.compatPriorityBucket()).isEqualTo("FINANCE");
        assertThat(projection.youthMajorLabel()).isEqualTo("교육");
        assertThat(projection.youthMidLabel()).isNull();
        assertThat(projection.provisionMethodLabel()).isEqualTo("온라인");
        assertThat(projection.gov24ServiceFieldLabel()).isEqualTo("보육");
        assertThat(projection.gov24UserTypeLabel()).isEqualTo("영유아");
        assertThat(projection.gov24BenefitTypeLabel()).isEqualTo("현금");
        assertThat(projection.educationPriorityBoostEligible()).isFalse();
        assertThat(projection.title()).isEqualTo("여성청소년 생리용품 지원");
        assertThat(projection.applyEndDate()).isEqualTo(LocalDate.of(2026, 12, 31));
        assertThat(projection.audienceRelevanceBonus()).isEqualTo(3.0);
        assertThat(projection.interestThemes()).containsExactly("생활지원");
        assertThat(projection.targetGroupsRaw()).containsExactlyInAnyOrder("기초생활수급자", "차상위계층");
        assertThat(projection.beneficiaryTerms()).containsExactlyInAnyOrder("기초생활수급자", "차상위계층");
        assertThat(projection.targetGroupBuckets()).containsExactly(RecommendationProjectionHeuristicSupport.BENEFICIARY_SUPPORT_BUCKET);
        assertThat(projection.factKeys()).containsExactly("BK_AGE_ELIGIBILITY");
    }

    @Test
    @DisplayName("service id 가 비면 빈 projection 을 반환하고 DB 조회를 생략한다")
    void findByServiceIds_returnsEmptyMapForEmptyInput() {
        assertThat(repository.findByServiceIds(List.of())).isEmpty();
    }

    @Test
    @DisplayName("projection 은 youth bonus 와 special target bucket 을 read-model 단계에서 미리 조립한다")
    void findByServiceIds_buildsAudienceBonusAndSpecialTargetBuckets() {
        Map<String, Object> baseRow = new LinkedHashMap<>();
        baseRow.put("service_id", 4401L);
        baseRow.put("source_type", "BOKJIRO_LOCAL");
        baseRow.put("unified_category", "주거");
        baseRow.put("youth_major_label", "주거");
        baseRow.put("youth_mid_label", null);
        baseRow.put("provision_method_label", "방문");
        baseRow.put("gov24_service_field_label", null);
        baseRow.put("gov24_user_type_label", null);
        baseRow.put("gov24_benefit_type_label", null);
        baseRow.put("title", "청년 농어촌 정착 지원");
        baseRow.put("summary", "한부모 청년의 농촌 정착을 지원");
        baseRow.put("min_age", 19);
        baseRow.put("max_age", 34);
        baseRow.put("min_income", 0);
        baseRow.put("max_income", 0);
        baseRow.put("apply_end_date", Date.valueOf(LocalDate.of(2026, 10, 1)));
        baseRow.put("search_youth_relevant", true);

        given(jdbcTemplate.queryForObject(org.mockito.ArgumentMatchers.contains("table_name = ?"), org.mockito.ArgumentMatchers.eq(Integer.class), org.mockito.ArgumentMatchers.eq("service_taxonomy_summary_slots")))
                .willReturn(0);
        given(namedParameterJdbcTemplate.queryForList(org.mockito.ArgumentMatchers.contains("FROM welfare_services"), any(MapSqlParameterSource.class)))
                .willReturn(List.of(baseRow));
        given(namedParameterJdbcTemplate.queryForList(org.mockito.ArgumentMatchers.contains("FROM service_taxonomy_terms"), any(MapSqlParameterSource.class)))
                .willReturn(List.of(
                        Map.of(
                                "service_id", 4401L,
                                "term_group", "LIFE_STAGE",
                                "term_label", "청년",
                                "source_field", "lifeStage"
                        )
                ));
        given(namedParameterJdbcTemplate.queryForList(org.mockito.ArgumentMatchers.contains("FROM service_facts"), any(MapSqlParameterSource.class)))
                .willReturn(List.of());

        RecommendationCandidateProjection projection = repository.findByServiceIds(List.of(4401L)).get(4401L);

        assertThat(projection.audienceRelevanceBonus()).isEqualTo(23.0);
        assertThat(projection.educationPriorityBoostEligible()).isFalse();
        assertThat(projection.specialTargetBuckets()).containsExactlyInAnyOrder(
                RecommendationProjectionHeuristicSupport.SPECIAL_TARGET_RURAL,
                RecommendationProjectionHeuristicSupport.SPECIAL_TARGET_SINGLE_PARENT
        );
    }

    @Test
    @DisplayName("education priority boost eligibility는 projection 단계에서 미리 계산한다")
    void findByServiceIds_buildsEducationPriorityBoostEligibility() {
        Map<String, Object> baseRow = new LinkedHashMap<>();
        baseRow.put("service_id", 5501L);
        baseRow.put("source_type", "YOUTH");
        baseRow.put("unified_category", "기타");
        baseRow.put("youth_major_label", "교육");
        baseRow.put("youth_mid_label", "재직자");
        baseRow.put("provision_method_label", "온라인");
        baseRow.put("gov24_service_field_label", null);
        baseRow.put("gov24_user_type_label", null);
        baseRow.put("gov24_benefit_type_label", null);
        baseRow.put("title", "교육 역량 강화");
        baseRow.put("summary", "청년 교육 지원");
        baseRow.put("min_age", 19);
        baseRow.put("max_age", 34);
        baseRow.put("min_income", 0);
        baseRow.put("max_income", 0);
        baseRow.put("apply_end_date", null);
        baseRow.put("search_youth_relevant", true);

        given(jdbcTemplate.queryForObject(org.mockito.ArgumentMatchers.contains("table_name = ?"), org.mockito.ArgumentMatchers.eq(Integer.class), org.mockito.ArgumentMatchers.eq("service_taxonomy_summary_slots")))
                .willReturn(0);
        given(namedParameterJdbcTemplate.queryForList(org.mockito.ArgumentMatchers.contains("FROM welfare_services"), any(MapSqlParameterSource.class)))
                .willReturn(List.of(baseRow));
        given(namedParameterJdbcTemplate.queryForList(org.mockito.ArgumentMatchers.contains("FROM service_taxonomy_terms"), any(MapSqlParameterSource.class)))
                .willReturn(List.of());
        given(namedParameterJdbcTemplate.queryForList(org.mockito.ArgumentMatchers.contains("FROM service_facts"), any(MapSqlParameterSource.class)))
                .willReturn(List.of());

        RecommendationCandidateProjection projection = repository.findByServiceIds(List.of(5501L)).get(5501L);

        assertThat(projection.educationPriorityBoostEligible()).isTrue();
        assertThat(projection.compatCategoryCode()).isEqualTo("OTHER");
        assertThat(projection.youthMidLabel()).isEqualTo("재직자");
        assertThat(projection.provisionMethodLabel()).isEqualTo("온라인");
    }

    @Test
    @DisplayName("summary slot table이 있으면 youth major는 slot-first로 읽고 legacy summary를 fallback으로만 사용한다")
    void findByServiceIds_prefersSummarySlotForYouthMajor() {
        Map<String, Object> baseRow = new LinkedHashMap<>();
        baseRow.put("service_id", 6601L);
        baseRow.put("source_type", "YOUTH");
        baseRow.put("unified_category", "기타");
        baseRow.put("youth_major_label", "교육");
        baseRow.put("youth_mid_label", "전월세 및 주거급여 지원");
        baseRow.put("provision_method_label", "온라인");
        baseRow.put("gov24_service_field_label", "상담");
        baseRow.put("gov24_user_type_label", "청년");
        baseRow.put("gov24_benefit_type_label", "서비스");
        baseRow.put("title", "교육 역량 강화");
        baseRow.put("summary", "청년 교육 지원");
        baseRow.put("min_age", 19);
        baseRow.put("max_age", 34);
        baseRow.put("min_income", 0);
        baseRow.put("max_income", 0);
        baseRow.put("apply_end_date", null);
        baseRow.put("search_youth_relevant", true);

        given(jdbcTemplate.queryForObject(org.mockito.ArgumentMatchers.contains("table_name = ?"), org.mockito.ArgumentMatchers.eq(Integer.class), org.mockito.ArgumentMatchers.eq("service_taxonomy_summary_slots")))
                .willReturn(1);
        given(namedParameterJdbcTemplate.queryForList(org.mockito.ArgumentMatchers.contains("FROM service_taxonomy_summary_slots"), any(MapSqlParameterSource.class)))
                .willReturn(List.of(baseRow));
        given(namedParameterJdbcTemplate.queryForList(org.mockito.ArgumentMatchers.contains("FROM service_taxonomy_terms"), any(MapSqlParameterSource.class)))
                .willReturn(List.of());
        given(namedParameterJdbcTemplate.queryForList(org.mockito.ArgumentMatchers.contains("FROM service_facts"), any(MapSqlParameterSource.class)))
                .willReturn(List.of());

        RecommendationCandidateProjection projection = repository.findByServiceIds(List.of(6601L)).get(6601L);

        assertThat(projection.youthMajorLabel()).isEqualTo("교육");
        assertThat(projection.compatCategoryCode()).isEqualTo("OTHER");
        assertThat(projection.youthMidLabel()).isEqualTo("전월세 및 주거급여 지원");
        assertThat(projection.provisionMethodLabel()).isEqualTo("온라인");
        assertThat(projection.gov24ServiceFieldLabel()).isEqualTo("상담");
        assertThat(projection.gov24UserTypeLabel()).isEqualTo("청년");
        assertThat(projection.gov24BenefitTypeLabel()).isEqualTo("서비스");
        assertThat(projection.educationPriorityBoostEligible()).isTrue();
    }

    @Test
    @DisplayName("Gov24 canonical term이 있으면 서비스분야 label과 사용자/지원유형 token은 term-first로 읽고 raw summary는 fallback으로만 사용한다")
    void findByServiceIds_prefersGov24CanonicalTerms() {
        Map<String, Object> baseRow = new LinkedHashMap<>();
        baseRow.put("service_id", 7701L);
        baseRow.put("source_type", "GOV24");
        baseRow.put("unified_category", "주거");
        baseRow.put("youth_major_label", null);
        baseRow.put("youth_mid_label", null);
        baseRow.put("provision_method_label", null);
        baseRow.put("gov24_service_field_label", "legacy-field");
        baseRow.put("gov24_user_type_label", "legacy-user");
        baseRow.put("gov24_benefit_type_label", "legacy-benefit");
        baseRow.put("title", "청년 이사비 지원");
        baseRow.put("summary", "청년 주거비 부담 완화");
        baseRow.put("min_age", 19);
        baseRow.put("max_age", 34);
        baseRow.put("min_income", 0);
        baseRow.put("max_income", 0);
        baseRow.put("apply_end_date", null);
        baseRow.put("search_youth_relevant", true);

        given(jdbcTemplate.queryForObject(org.mockito.ArgumentMatchers.contains("table_name = ?"), org.mockito.ArgumentMatchers.eq(Integer.class), org.mockito.ArgumentMatchers.eq("service_taxonomy_summary_slots")))
                .willReturn(0);
        given(namedParameterJdbcTemplate.queryForList(org.mockito.ArgumentMatchers.contains("FROM welfare_services"), any(MapSqlParameterSource.class)))
                .willReturn(List.of(baseRow));
        given(namedParameterJdbcTemplate.queryForList(org.mockito.ArgumentMatchers.contains("FROM service_taxonomy_terms"), any(MapSqlParameterSource.class)))
                .willReturn(List.of(
                        Map.of(
                                "service_id", 7701L,
                                "term_group", "GOV24_SERVICE_FIELD",
                                "term_label", "주거·자립",
                                "source_field", "serviceField"
                        ),
                        Map.of(
                                "service_id", 7701L,
                                "term_group", "GOV24_USER_TYPE_TOKEN",
                                "term_label", "개인",
                                "source_field", "userType"
                        ),
                        Map.of(
                                "service_id", 7701L,
                                "term_group", "GOV24_BENEFIT_TYPE_TOKEN",
                                "term_label", "현금(융자)",
                                "source_field", "supportType"
                        )
                ));
        given(namedParameterJdbcTemplate.queryForList(org.mockito.ArgumentMatchers.contains("FROM service_facts"), any(MapSqlParameterSource.class)))
                .willReturn(List.of());

        RecommendationCandidateProjection projection = repository.findByServiceIds(List.of(7701L)).get(7701L);

        assertThat(projection.gov24ServiceFieldLabel()).isEqualTo("주거·자립");
        assertThat(projection.gov24UserTypeTokens()).containsExactly("개인");
        assertThat(projection.gov24BenefitTypeTokens()).containsExactly("현금(융자)");
        assertThat(projection.gov24UserTypeLabel()).isEqualTo("개인");
        assertThat(projection.gov24BenefitTypeLabel()).isEqualTo("현금(융자)");
        assertThat(projection.priorityBuckets()).contains("HOUSING", "FINANCE");
    }

    @Test
    @DisplayName("Gov24 canonical term은 recommendation projection에서 youth major/mid bridge도 연다")
    void findByServiceIds_autoBridgesGov24TermsToYouthMid() {
        Map<String, Object> baseRow = new LinkedHashMap<>();
        baseRow.put("service_id", 7801L);
        baseRow.put("source_type", "GOV24");
        baseRow.put("unified_category", "기타");
        baseRow.put("youth_major_label", null);
        baseRow.put("youth_mid_label", null);
        baseRow.put("provision_method_label", null);
        baseRow.put("gov24_service_field_label", "주거·자립");
        baseRow.put("gov24_user_type_label", "개인||가구");
        baseRow.put("gov24_benefit_type_label", "현금(융자)");
        baseRow.put("title", "청년 주거비 지원");
        baseRow.put("summary", "청년 주거 안정 지원");
        baseRow.put("min_age", 19);
        baseRow.put("max_age", 34);
        baseRow.put("min_income", 0);
        baseRow.put("max_income", 0);
        baseRow.put("apply_end_date", null);
        baseRow.put("search_youth_relevant", true);

        given(jdbcTemplate.queryForObject(org.mockito.ArgumentMatchers.contains("table_name = ?"), org.mockito.ArgumentMatchers.eq(Integer.class), org.mockito.ArgumentMatchers.eq("service_taxonomy_summary_slots")))
                .willReturn(0);
        given(namedParameterJdbcTemplate.queryForList(org.mockito.ArgumentMatchers.contains("FROM welfare_services"), any(MapSqlParameterSource.class)))
                .willReturn(List.of(baseRow));
        given(namedParameterJdbcTemplate.queryForList(org.mockito.ArgumentMatchers.contains("FROM service_taxonomy_terms"), any(MapSqlParameterSource.class)))
                .willReturn(List.of(
                        Map.of(
                                "service_id", 7801L,
                                "term_group", "GOV24_SERVICE_FIELD",
                                "term_label", "주거·자립",
                                "source_field", "serviceField"
                        ),
                        Map.of(
                                "service_id", 7801L,
                                "term_group", "GOV24_USER_TYPE_TOKEN",
                                "term_label", "개인",
                                "source_field", "userType"
                        ),
                        Map.of(
                                "service_id", 7801L,
                                "term_group", "GOV24_BENEFIT_TYPE_TOKEN",
                                "term_label", "현금(융자)",
                                "source_field", "supportType"
                        )
                ));
        given(namedParameterJdbcTemplate.queryForList(org.mockito.ArgumentMatchers.contains("FROM service_facts"), any(MapSqlParameterSource.class)))
                .willReturn(List.of());

        RecommendationCandidateProjection projection = repository.findByServiceIds(List.of(7801L)).get(7801L);

        assertThat(projection.gov24ServiceFieldLabel()).isEqualTo("주거·자립");
        assertThat(projection.gov24UserTypeTokens()).containsExactly("개인");
        assertThat(projection.gov24BenefitTypeTokens()).containsExactly("현금(융자)");
        assertThat(projection.gov24UserTypeLabel()).isEqualTo("개인");
        assertThat(projection.gov24BenefitTypeLabel()).isEqualTo("현금(융자)");
        assertThat(projection.priorityBuckets()).contains("HOUSING", "FINANCE");
        assertThat(projection.youthMajorLabel()).isEqualTo("주거");
        assertThat(projection.youthMidLabel()).isEqualTo("주택 및 거주지");
    }

    @Test
    @DisplayName("Gov24 보육·교육 bridge는 교육 priority boost 판정에도 연결된다")
    void findByServiceIds_usesGov24YouthBridgeForEducationBoostEligibility() {
        Map<String, Object> baseRow = new LinkedHashMap<>();
        baseRow.put("service_id", 7901L);
        baseRow.put("source_type", "GOV24");
        baseRow.put("unified_category", "기타");
        baseRow.put("youth_major_label", null);
        baseRow.put("youth_mid_label", null);
        baseRow.put("provision_method_label", null);
        baseRow.put("gov24_service_field_label", "보육·교육");
        baseRow.put("gov24_user_type_label", "개인");
        baseRow.put("gov24_benefit_type_label", "현금(장학금)");
        baseRow.put("title", "청년 장학금 지원");
        baseRow.put("summary", "청년 등록금 부담 완화");
        baseRow.put("min_age", 19);
        baseRow.put("max_age", 34);
        baseRow.put("min_income", 0);
        baseRow.put("max_income", 0);
        baseRow.put("apply_end_date", null);
        baseRow.put("search_youth_relevant", true);

        given(jdbcTemplate.queryForObject(org.mockito.ArgumentMatchers.contains("table_name = ?"), org.mockito.ArgumentMatchers.eq(Integer.class), org.mockito.ArgumentMatchers.eq("service_taxonomy_summary_slots")))
                .willReturn(0);
        given(namedParameterJdbcTemplate.queryForList(org.mockito.ArgumentMatchers.contains("FROM welfare_services"), any(MapSqlParameterSource.class)))
                .willReturn(List.of(baseRow));
        given(namedParameterJdbcTemplate.queryForList(org.mockito.ArgumentMatchers.contains("FROM service_taxonomy_terms"), any(MapSqlParameterSource.class)))
                .willReturn(List.of(
                        Map.of(
                                "service_id", 7901L,
                                "term_group", "GOV24_SERVICE_FIELD",
                                "term_label", "보육·교육",
                                "source_field", "serviceField"
                        ),
                        Map.of(
                                "service_id", 7901L,
                                "term_group", "GOV24_BENEFIT_TYPE_TOKEN",
                                "term_label", "현금(장학금)",
                                "source_field", "supportType"
                        )
                ));
        given(namedParameterJdbcTemplate.queryForList(org.mockito.ArgumentMatchers.contains("FROM service_facts"), any(MapSqlParameterSource.class)))
                .willReturn(List.of());

        RecommendationCandidateProjection projection = repository.findByServiceIds(List.of(7901L)).get(7901L);

        assertThat(projection.gov24ServiceFieldLabel()).isEqualTo("보육·교육");
        assertThat(projection.youthMajorLabel()).isEqualTo("교육");
        assertThat(projection.youthMidLabel()).isEqualTo("교육비지원");
        assertThat(projection.priorityBuckets()).contains("EDUCATION");
        assertThat(projection.educationPriorityBoostEligible()).isTrue();
    }

    @Test
    @DisplayName("summary projection은 카드 응답 라벨만 보존하고 추천 scoring field 조립은 생략한다")
    void findSummaryByServiceIds_buildsResponseFieldsOnly() {
        Map<String, Object> baseRow = new LinkedHashMap<>();
        baseRow.put("service_id", 8801L);
        baseRow.put("source_type", "GOV24");
        baseRow.put("unified_category", "기타");
        baseRow.put("youth_major_label", null);
        baseRow.put("youth_mid_label", null);
        baseRow.put("provision_method_label", "온라인");
        baseRow.put("gov24_service_field_label", "legacy-field");
        baseRow.put("gov24_user_type_label", "legacy-user");
        baseRow.put("gov24_benefit_type_label", "legacy-benefit");
        baseRow.put("title", "청년 월세 지원");
        baseRow.put("summary", "청년 주거비 부담 완화");
        baseRow.put("min_age", 19);
        baseRow.put("max_age", 34);
        baseRow.put("min_income", 0);
        baseRow.put("max_income", 0);
        baseRow.put("apply_end_date", null);
        baseRow.put("search_youth_relevant", true);

        given(jdbcTemplate.queryForObject(org.mockito.ArgumentMatchers.contains("table_name = ?"), org.mockito.ArgumentMatchers.eq(Integer.class), org.mockito.ArgumentMatchers.eq("service_taxonomy_summary_slots")))
                .willReturn(0);
        given(namedParameterJdbcTemplate.queryForList(org.mockito.ArgumentMatchers.contains("FROM welfare_services"), any(MapSqlParameterSource.class)))
                .willReturn(List.of(baseRow));
        given(namedParameterJdbcTemplate.queryForList(
                argThat(sql -> sql != null && sql.contains("FROM service_taxonomy_terms") && sql.contains("term_group IN")),
                any(MapSqlParameterSource.class)
        )).willReturn(List.of(
                Map.of(
                        "service_id", 8801L,
                        "term_group", "GOV24_SERVICE_FIELD",
                        "term_label", "주거·자립",
                        "source_field", "serviceField"
                ),
                Map.of(
                        "service_id", 8801L,
                        "term_group", "GOV24_USER_TYPE_TOKEN",
                        "term_label", "개인",
                        "source_field", "userType"
                ),
                Map.of(
                        "service_id", 8801L,
                        "term_group", "GOV24_BENEFIT_TYPE_TOKEN",
                        "term_label", "현금",
                        "source_field", "supportType"
                )
        ));
        given(namedParameterJdbcTemplate.queryForList(
                argThat(sql -> sql != null && sql.contains("FROM service_facts") && sql.contains("fact_code_set_key IN")),
                any(MapSqlParameterSource.class)
        )).willReturn(List.of(
                Map.of(
                        "service_id", 8801L,
                        "fact_merge_key", "YOUTH_EMPLOYMENT_REQUIREMENT:001",
                        "fact_code_set_key", "YOUTH_EMPLOYMENT_REQUIREMENT",
                        "fact_code", "001",
                        "raw_value", "001",
                        "text_value", "미취업자"
                )
        ));

        RecommendationCandidateProjection projection = repository.findSummaryByServiceIds(List.of(8801L)).get(8801L);

        assertThat(projection.gov24ServiceFieldLabel()).isEqualTo("주거·자립");
        assertThat(projection.gov24UserTypeLabel()).isEqualTo("개인");
        assertThat(projection.gov24BenefitTypeLabel()).isEqualTo("현금");
        assertThat(projection.youthMajorLabel()).isEqualTo("주거");
        assertThat(projection.youthMidLabel()).isEqualTo("주택 및 거주지");
        assertThat(projection.youthEmploymentRequirementLabels()).containsExactly("미취업자");
        assertThat(projection.summary()).isEqualTo("청년 주거비 부담 완화");
        assertThat(projection.priorityBuckets()).isEmpty();
        assertThat(projection.targetGroupsRaw()).isEmpty();
        assertThat(projection.specialTargetBuckets()).isEmpty();
        assertThat(projection.factKeys()).isEmpty();
        assertThat(projection.audienceRelevanceBonus()).isZero();
    }
}
