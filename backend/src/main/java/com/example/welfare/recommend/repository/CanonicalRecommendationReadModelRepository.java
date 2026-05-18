package com.example.welfare.recommend.repository;

import com.example.welfare.collect.support.Gov24LabelTokenSupport;
import com.example.welfare.collect.support.YouthOfficialCodeSupport;
import com.example.welfare.recommend.dto.RecommendationCandidateProjection;
import com.example.welfare.policy.support.CompatCategorySupport;
import com.example.welfare.recommend.support.RecommendationProjectionHeuristicSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 추천 후보 service id 목록에 대해 canonical sidecar 기반 projection을 읽는다.
 */
@Repository
@RequiredArgsConstructor
public class CanonicalRecommendationReadModelRepository {

    private static final String SUMMARY_SLOT_TABLE = "service_taxonomy_summary_slots";

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    private volatile Boolean summarySlotTableReady;

    public Map<Long, RecommendationCandidateProjection> findByServiceIds(List<Long> serviceIds) {
        if (serviceIds == null || serviceIds.isEmpty()) {
            return Map.of();
        }

        MapSqlParameterSource params = new MapSqlParameterSource("serviceIds", serviceIds);
        Map<Long, MutableProjection> projections = baseRows(params).stream()
                .collect(LinkedHashMap::new,
                        (map, row) -> map.put((Long) row.get("service_id"), MutableProjection.fromBaseRow(row)),
                        LinkedHashMap::putAll);

        if (projections.isEmpty()) {
            return Map.of();
        }

        for (Map<String, Object> row : taxonomyTermRows(params)) {
            MutableProjection projection = projections.get(longValue(row.get("service_id")));
            if (projection == null) {
                continue;
            }
            projection.addTaxonomyTerm(
                    stringValue(row.get("term_group")),
                    stringValue(row.get("term_label")),
                    stringValue(row.get("source_field"))
            );
        }

        for (Map<String, Object> row : factRows(params)) {
            MutableProjection projection = projections.get(longValue(row.get("service_id")));
            if (projection == null) {
                continue;
            }
            projection.addFact(
                    stringValue(row.get("fact_merge_key")),
                    stringValue(row.get("fact_code_set_key")),
                    stringValue(row.get("fact_code")),
                    stringValue(row.get("text_value"))
            );
        }

        LinkedHashMap<Long, RecommendationCandidateProjection> result = new LinkedHashMap<>();
        for (MutableProjection projection : projections.values()) {
            result.put(projection.serviceId, projection.toProjection());
        }
        return result;
    }

    private List<Map<String, Object>> baseRows(MapSqlParameterSource params) {
        if (summarySlotTableReady()) {
            return namedParameterJdbcTemplate.queryForList("""
                    SELECT ws.id AS service_id,
                           ws.source_type,
                           ws.unified_category,
                           COALESCE(stss_youth_major.slot_label, st.youth_major_label) AS youth_major_label,
                           COALESCE(stss_youth_mid.slot_label, st.youth_mid_label) AS youth_mid_label,
                           COALESCE(stss_provision_method.slot_label, st.provision_method_label, ws.apply_method_name) AS provision_method_label,
                           COALESCE(stss_gov24_service_field.slot_label, st.gov24_service_field_label) AS gov24_service_field_label,
                           COALESCE(stss_gov24_user_type.slot_label, st.gov24_user_type_label) AS gov24_user_type_label,
                           COALESCE(stss_gov24_benefit_type.slot_label, st.gov24_benefit_type_label) AS gov24_benefit_type_label,
                           ws.title,
                           COALESCE(wsd.support_detail, ws.support_content, ws.description) AS summary,
                           ws.min_age,
                           ws.max_age,
                           ws.min_income,
                           ws.max_income,
                           ws.apply_end_date,
                           ws.search_youth_relevant
                    FROM welfare_services ws
                    LEFT JOIN welfare_service_details wsd ON wsd.service_id = ws.id
                    LEFT JOIN service_taxonomies st ON st.service_id = ws.id
                    LEFT JOIN (
                        SELECT service_id,
                               MAX(slot_label) AS slot_label
                        FROM service_taxonomy_summary_slots
                        WHERE slot_key = 'YOUTH_MAJOR'
                        GROUP BY service_id
                    ) stss_youth_major ON stss_youth_major.service_id = ws.id
                    LEFT JOIN (
                        SELECT service_id,
                               MAX(slot_label) AS slot_label
                        FROM service_taxonomy_summary_slots
                        WHERE slot_key = 'YOUTH_MID'
                        GROUP BY service_id
                    ) stss_youth_mid ON stss_youth_mid.service_id = ws.id
                    LEFT JOIN (
                        SELECT service_id,
                               MAX(slot_label) AS slot_label
                        FROM service_taxonomy_summary_slots
                        WHERE slot_key = 'PROVISION_METHOD'
                        GROUP BY service_id
                    ) stss_provision_method ON stss_provision_method.service_id = ws.id
                    LEFT JOIN (
                        SELECT service_id,
                               MAX(slot_label) AS slot_label
                        FROM service_taxonomy_summary_slots
                        WHERE slot_key = 'GOV24_SERVICE_FIELD'
                        GROUP BY service_id
                    ) stss_gov24_service_field ON stss_gov24_service_field.service_id = ws.id
                    LEFT JOIN (
                        SELECT service_id,
                               MAX(slot_label) AS slot_label
                        FROM service_taxonomy_summary_slots
                        WHERE slot_key = 'GOV24_USER_TYPE'
                        GROUP BY service_id
                    ) stss_gov24_user_type ON stss_gov24_user_type.service_id = ws.id
                    LEFT JOIN (
                        SELECT service_id,
                               MAX(slot_label) AS slot_label
                        FROM service_taxonomy_summary_slots
                        WHERE slot_key = 'GOV24_BENEFIT_TYPE'
                        GROUP BY service_id
                    ) stss_gov24_benefit_type ON stss_gov24_benefit_type.service_id = ws.id
                    WHERE ws.id IN (:serviceIds)
                    """, params);
        }
        return namedParameterJdbcTemplate.queryForList("""
                SELECT ws.id AS service_id,
                       ws.source_type,
                       ws.unified_category,
                       st.youth_major_label,
                       st.youth_mid_label,
                       COALESCE(st.provision_method_label, ws.apply_method_name) AS provision_method_label,
                       st.gov24_service_field_label,
                       st.gov24_user_type_label,
                       st.gov24_benefit_type_label,
                       ws.title,
                       COALESCE(wsd.support_detail, ws.support_content, ws.description) AS summary,
                       ws.min_age,
                       ws.max_age,
                       ws.min_income,
                       ws.max_income,
                       ws.apply_end_date,
                       ws.search_youth_relevant
                FROM welfare_services ws
                LEFT JOIN welfare_service_details wsd ON wsd.service_id = ws.id
                LEFT JOIN service_taxonomies st ON st.service_id = ws.id
                WHERE ws.id IN (:serviceIds)
                """, params);
    }

    private boolean summarySlotTableReady() {
        if (summarySlotTableReady != null) {
            return summarySlotTableReady;
        }
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = current_schema()
                  AND table_name = ?
                """, Integer.class, SUMMARY_SLOT_TABLE);
        summarySlotTableReady = count != null && count > 0;
        return summarySlotTableReady;
    }

    private List<Map<String, Object>> taxonomyTermRows(MapSqlParameterSource params) {
        return namedParameterJdbcTemplate.queryForList("""
                SELECT service_id,
                       term_group,
                       term_label,
                       source_field
                FROM service_taxonomy_terms
                WHERE service_id IN (:serviceIds)
                ORDER BY service_id, term_group, sort_order, term_label
                """, params);
    }

    private List<Map<String, Object>> factRows(MapSqlParameterSource params) {
        return namedParameterJdbcTemplate.queryForList("""
                SELECT service_id,
                       fact_merge_key,
                       fact_code_set_key,
                       fact_code,
                       text_value
                FROM service_facts
                WHERE service_id IN (:serviceIds)
                ORDER BY service_id, fact_merge_key
                """, params);
    }

    private static Long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return value == null ? null : Long.valueOf(String.valueOf(value));
    }

    private static Integer intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return value == null ? null : Integer.valueOf(String.valueOf(value));
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static LocalDate localDateValue(Object value) {
        if (value instanceof Date date) {
            return date.toLocalDate();
        }
        if (value instanceof LocalDate localDate) {
            return localDate;
        }
        return null;
    }

    private static boolean booleanValue(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        return value != null && Boolean.parseBoolean(String.valueOf(value));
    }

    private static final class MutableProjection {
        private final Long serviceId;
        private final String sourceType;
        private final String unifiedCategoryCompat;
        private final String compatCategoryCode;
        private final String compatPriorityBucket;
        private final String youthMajorLabel;
        private final String youthMidLabel;
        private final String provisionMethodLabel;
        private final String gov24ServiceFieldLabel;
        private final String gov24UserTypeLabel;
        private final String gov24BenefitTypeLabel;
        private final Set<String> youthEmploymentRequirementCodes = new LinkedHashSet<>();
        private final Set<String> youthEmploymentRequirementLabels = new LinkedHashSet<>();
        private final Set<String> youthEducationRequirementCodes = new LinkedHashSet<>();
        private final Set<String> youthEducationRequirementLabels = new LinkedHashSet<>();
        private final Set<String> youthSpecialRequirementCodes = new LinkedHashSet<>();
        private final Set<String> youthSpecialRequirementLabels = new LinkedHashSet<>();
        private String youthIncomeConditionTypeCode;
        private String youthIncomeConditionTypeLabel;
        private final String title;
        private final String summary;
        private final Integer minAge;
        private final Integer maxAge;
        private final Integer incomeMinLegacy;
        private final Integer incomeMaxLegacy;
        private final LocalDate applyEndDate;
        private final boolean youthRelevant;

        private final Set<String> interestThemes = new LinkedHashSet<>();
        private final Set<String> priorityBuckets = new LinkedHashSet<>();
        private final Set<String> targetGroupsRaw = new LinkedHashSet<>();
        private final Set<String> targetGroupBuckets = new LinkedHashSet<>();
        private final Set<String> lifeStages = new LinkedHashSet<>();
        private final Set<String> keywordTags = new LinkedHashSet<>();
        private final Set<String> beneficiaryTerms = new LinkedHashSet<>();
        private final Set<String> specialTargetBuckets = new LinkedHashSet<>();
        private final Set<String> factKeys = new LinkedHashSet<>();

        private MutableProjection(Long serviceId,
                                  String sourceType,
                                  String unifiedCategoryCompat,
                                  String youthMajorLabel,
                                  String youthMidLabel,
                                  String provisionMethodLabel,
                                  String gov24ServiceFieldLabel,
                                  String gov24UserTypeLabel,
                                  String gov24BenefitTypeLabel,
                                  String title,
                                  String summary,
                                  Integer minAge,
                                  Integer maxAge,
                                  Integer incomeMinLegacy,
                                  Integer incomeMaxLegacy,
                                  LocalDate applyEndDate,
                                  boolean youthRelevant) {
            this.serviceId = serviceId;
            this.sourceType = sourceType;
            this.unifiedCategoryCompat = unifiedCategoryCompat;
            this.compatCategoryCode = CompatCategorySupport.compatCode(unifiedCategoryCompat);
            this.compatPriorityBucket = CompatCategorySupport.priorityBucket(unifiedCategoryCompat);
            this.youthMajorLabel = youthMajorLabel;
            this.youthMidLabel = youthMidLabel;
            this.provisionMethodLabel = provisionMethodLabel;
            this.gov24ServiceFieldLabel = gov24ServiceFieldLabel;
            this.gov24UserTypeLabel = gov24UserTypeLabel;
            this.gov24BenefitTypeLabel = gov24BenefitTypeLabel;
            this.title = title;
            this.summary = summary;
            this.minAge = minAge;
            this.maxAge = maxAge;
            this.incomeMinLegacy = incomeMinLegacy;
            this.incomeMaxLegacy = incomeMaxLegacy;
            this.applyEndDate = applyEndDate;
            this.youthRelevant = youthRelevant;
        }

        static MutableProjection fromBaseRow(Map<String, Object> row) {
            return new MutableProjection(
                    longValue(row.get("service_id")),
                    stringValue(row.get("source_type")),
                    stringValue(row.get("unified_category")),
                    stringValue(row.get("youth_major_label")),
                    stringValue(row.get("youth_mid_label")),
                    stringValue(row.get("provision_method_label")),
                    stringValue(row.get("gov24_service_field_label")),
                    stringValue(row.get("gov24_user_type_label")),
                    stringValue(row.get("gov24_benefit_type_label")),
                    stringValue(row.get("title")),
                    stringValue(row.get("summary")),
                    intValue(row.get("min_age")),
                    intValue(row.get("max_age")),
                    intValue(row.get("min_income")),
                    intValue(row.get("max_income")),
                    localDateValue(row.get("apply_end_date")),
                    booleanValue(row.get("search_youth_relevant"))
            );
        }

        void addTaxonomyTerm(String termGroup, String termLabel, String sourceField) {
            if (termGroup == null || termLabel == null) {
                return;
            }
            switch (termGroup) {
                case "INTEREST_THEME" -> interestThemes.add(termLabel);
                case "LIFE_STAGE" -> lifeStages.add(termLabel);
                case "YOUTH_KEYWORD" -> keywordTags.add(termLabel);
                case "TARGET_GROUP" -> {
                    targetGroupsRaw.add(termLabel);
                    if (RecommendationProjectionHeuristicSupport.isBeneficiaryDetailTerm(termLabel, sourceField)) {
                        beneficiaryTerms.add(termLabel);
                        targetGroupBuckets.add(RecommendationProjectionHeuristicSupport.BENEFICIARY_SUPPORT_BUCKET);
                    }
                }
                default -> {
                    // no-op
                }
            }
            RecommendationProjectionHeuristicSupport.collectSpecialTargetBuckets(specialTargetBuckets, termLabel);
        }

        void addFact(String factMergeKey, String factCodeSetKey, String factCode, String textValue) {
            if (factMergeKey != null && !factMergeKey.isBlank()) {
                factKeys.add(factMergeKey);
            }
            if ("YOUTH_EMPLOYMENT_REQUIREMENT".equals(factCodeSetKey)) {
                youthEmploymentRequirementCodes.addAll(YouthOfficialCodeSupport.splitCsvValues(factCode));
                youthEmploymentRequirementLabels.addAll(YouthOfficialCodeSupport.splitCsvValues(textValue));
            }
            if ("YOUTH_EDUCATION_REQUIREMENT".equals(factCodeSetKey)) {
                youthEducationRequirementCodes.addAll(YouthOfficialCodeSupport.splitCsvValues(factCode));
                youthEducationRequirementLabels.addAll(YouthOfficialCodeSupport.splitCsvValues(textValue));
            }
            if ("YOUTH_SPECIAL_REQUIREMENT".equals(factCodeSetKey)) {
                youthSpecialRequirementCodes.addAll(YouthOfficialCodeSupport.splitCsvValues(factCode));
                youthSpecialRequirementLabels.addAll(YouthOfficialCodeSupport.splitCsvValues(textValue));
            }
            if ("YOUTH_INCOME_CONDITION_TYPE".equals(factCodeSetKey)) {
                youthIncomeConditionTypeCode = factCode;
                youthIncomeConditionTypeLabel = textValue;
            }
        }

        RecommendationCandidateProjection toProjection() {
            RecommendationProjectionHeuristicSupport.collectSpecialTargetBuckets(specialTargetBuckets, title);
            RecommendationProjectionHeuristicSupport.collectSpecialTargetBuckets(specialTargetBuckets, summary);
            addPriorityBucket(compatPriorityBucket);
            return RecommendationCandidateProjection.builder()
                    .serviceId(serviceId)
                    .sourceType(sourceType)
                    .unifiedCategoryCompat(unifiedCategoryCompat)
                    .compatCategoryCode(compatCategoryCode)
                    .compatPriorityBucket(compatPriorityBucket)
                    .youthMajorLabel(youthMajorLabel)
                    .youthMidLabel(youthMidLabel)
                    .provisionMethodLabel(provisionMethodLabel)
                    .gov24ServiceFieldLabel(gov24ServiceFieldLabel)
                    .gov24UserTypeLabel(gov24UserTypeLabel)
                    .gov24BenefitTypeLabel(gov24BenefitTypeLabel)
                    .gov24UserTypeTokens(Gov24LabelTokenSupport.userTypeTokens(gov24UserTypeLabel))
                    .gov24BenefitTypeTokens(Gov24LabelTokenSupport.benefitTypeTokens(gov24BenefitTypeLabel))
                    .youthEmploymentRequirementCodes(List.copyOf(youthEmploymentRequirementCodes))
                    .youthEmploymentRequirementLabels(List.copyOf(youthEmploymentRequirementLabels))
                    .youthEducationRequirementCodes(List.copyOf(youthEducationRequirementCodes))
                    .youthEducationRequirementLabels(List.copyOf(youthEducationRequirementLabels))
                    .youthSpecialRequirementCodes(List.copyOf(youthSpecialRequirementCodes))
                    .youthSpecialRequirementLabels(List.copyOf(youthSpecialRequirementLabels))
                    .youthIncomeConditionTypeCode(youthIncomeConditionTypeCode)
                    .youthIncomeConditionTypeLabel(youthIncomeConditionTypeLabel)
                    .title(title)
                    .summary(summary)
                    .minAge(minAge)
                    .maxAge(maxAge)
                    .incomeMinLegacy(incomeMinLegacy)
                    .incomeMaxLegacy(incomeMaxLegacy)
                    .applyEndDate(applyEndDate)
                    .youthRelevant(youthRelevant)
                    .audienceRelevanceBonus(RecommendationProjectionHeuristicSupport.audienceRelevanceBonus(
                            title,
                            summary,
                            targetGroupsRaw,
                            keywordTags,
                            interestThemes,
                            lifeStages,
                            minAge,
                            maxAge
                    ))
                    .educationPriorityBoostEligible(
                            RecommendationProjectionHeuristicSupport.educationPriorityBoostEligible(
                                    compatCategoryCode,
                                    youthMajorLabel
                            )
                    )
                    .priorityBuckets(priorityBuckets)
                    .interestThemes(interestThemes)
                    .targetGroupsRaw(targetGroupsRaw)
                    .targetGroupBuckets(targetGroupBuckets)
                    .lifeStages(lifeStages)
                    .keywordTags(keywordTags)
                    .beneficiaryTerms(beneficiaryTerms)
                    .specialTargetBuckets(specialTargetBuckets)
                    .factKeys(factKeys)
                    .build();
        }

        private void addPriorityBucket(String priorityBucket) {
            if (priorityBucket != null) {
                priorityBuckets.add(priorityBucket);
            }
        }

    }
}
