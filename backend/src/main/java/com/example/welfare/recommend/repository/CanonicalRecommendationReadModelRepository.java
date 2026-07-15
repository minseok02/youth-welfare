package com.example.welfare.recommend.repository;

import com.example.welfare.collect.support.Gov24LabelTokenSupport;
import com.example.welfare.collect.support.Gov24TaxonomyCodeSupport;
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
                    stringValue(row.get("raw_value")),
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
                    WITH summary_slots AS (
                        SELECT service_id,
                               MAX(slot_label) FILTER (WHERE slot_key = 'YOUTH_MAJOR') AS youth_major_label,
                               MAX(slot_label) FILTER (WHERE slot_key = 'YOUTH_MID') AS youth_mid_label,
                               MAX(slot_label) FILTER (WHERE slot_key = 'PROVISION_METHOD') AS provision_method_label,
                               MAX(slot_label) FILTER (WHERE slot_key = 'GOV24_SERVICE_FIELD') AS gov24_service_field_label,
                               MAX(slot_label) FILTER (WHERE slot_key = 'GOV24_USER_TYPE') AS gov24_user_type_label,
                               MAX(slot_label) FILTER (WHERE slot_key = 'GOV24_BENEFIT_TYPE') AS gov24_benefit_type_label
                        FROM service_taxonomy_summary_slots
                        WHERE service_id IN (:serviceIds)
                          AND slot_key IN (
                              'YOUTH_MAJOR',
                              'YOUTH_MID',
                              'PROVISION_METHOD',
                              'GOV24_SERVICE_FIELD',
                              'GOV24_USER_TYPE',
                              'GOV24_BENEFIT_TYPE'
                          )
                        GROUP BY service_id
                    )
                    SELECT ws.id AS service_id,
                           ws.source_type,
                           ws.unified_category,
                           COALESCE(ss.youth_major_label, st.youth_major_label) AS youth_major_label,
                           COALESCE(ss.youth_mid_label, st.youth_mid_label) AS youth_mid_label,
                           COALESCE(ss.provision_method_label, st.provision_method_label, ws.apply_method_name) AS provision_method_label,
                           COALESCE(ss.gov24_service_field_label, st.gov24_service_field_label) AS gov24_service_field_label,
                           COALESCE(ss.gov24_user_type_label, st.gov24_user_type_label) AS gov24_user_type_label,
                           COALESCE(ss.gov24_benefit_type_label, st.gov24_benefit_type_label) AS gov24_benefit_type_label,
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
                    LEFT JOIN summary_slots ss ON ss.service_id = ws.id
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
                       raw_value,
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
        private String gov24ServiceFieldTermLabel;
        private final Set<String> gov24UserTypeTokens = new LinkedHashSet<>();
        private final Set<String> gov24BenefitTypeTokens = new LinkedHashSet<>();
        private final Set<String> youthEmploymentRequirementCodes = new LinkedHashSet<>();
        private final Set<String> youthEmploymentRequirementLabels = new LinkedHashSet<>();
        private final Set<String> youthEducationRequirementCodes = new LinkedHashSet<>();
        private final Set<String> youthEducationRequirementLabels = new LinkedHashSet<>();
        private final Set<String> youthSpecialRequirementCodes = new LinkedHashSet<>();
        private final Set<String> youthSpecialRequirementLabels = new LinkedHashSet<>();
        private String youthMaritalStatusCode;
        private String youthMaritalStatusLabel;
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
                case "GOV24_SERVICE_FIELD" -> {
                    if (gov24ServiceFieldTermLabel == null || gov24ServiceFieldTermLabel.isBlank()) {
                        gov24ServiceFieldTermLabel = termLabel;
                    }
                    addPriorityBucket(RecommendationProjectionHeuristicSupport.gov24ServiceFieldPriorityBucket(termLabel));
                }
                case "GOV24_USER_TYPE_TOKEN" -> gov24UserTypeTokens.add(termLabel);
                case "GOV24_BENEFIT_TYPE_TOKEN" -> {
                    gov24BenefitTypeTokens.add(termLabel);
                    addPriorityBucket(RecommendationProjectionHeuristicSupport.gov24BenefitTypePriorityBucket(termLabel));
                }
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

        void addFact(String factMergeKey, String factCodeSetKey, String factCode, String rawValue, String textValue) {
            if (factMergeKey != null && !factMergeKey.isBlank()) {
                factKeys.add(factMergeKey);
            }
            String codeCsv = preferredCodeCsv(rawValue, factCode);
            if ("YOUTH_EMPLOYMENT_REQUIREMENT".equals(factCodeSetKey)) {
                youthEmploymentRequirementCodes.addAll(YouthOfficialCodeSupport.splitCsvValues(codeCsv));
                youthEmploymentRequirementLabels.addAll(YouthOfficialCodeSupport.splitCsvValues(textValue));
            }
            if ("YOUTH_EDUCATION_REQUIREMENT".equals(factCodeSetKey)) {
                youthEducationRequirementCodes.addAll(YouthOfficialCodeSupport.splitCsvValues(codeCsv));
                youthEducationRequirementLabels.addAll(YouthOfficialCodeSupport.splitCsvValues(textValue));
            }
            if ("YOUTH_SPECIAL_REQUIREMENT".equals(factCodeSetKey)) {
                youthSpecialRequirementCodes.addAll(YouthOfficialCodeSupport.splitCsvValues(codeCsv));
                youthSpecialRequirementLabels.addAll(YouthOfficialCodeSupport.splitCsvValues(textValue));
            }
            if ("YOUTH_MARITAL_STATUS".equals(factCodeSetKey)) {
                youthMaritalStatusCode = factCode;
                youthMaritalStatusLabel = textValue;
            }
            if ("YOUTH_INCOME_CONDITION_TYPE".equals(factCodeSetKey)) {
                youthIncomeConditionTypeCode = factCode;
                youthIncomeConditionTypeLabel = textValue;
            }
            if ("GOV24_SUPPORT_CONDITION".equals(factCodeSetKey)
                    && factMergeKey != null
                    && factMergeKey.startsWith("GOV24_BUSINESS_STAGE:")) {
                addPriorityBucket("JOB");
            }
        }

        private String preferredCodeCsv(String rawValue, String factCode) {
            return rawValue != null && !rawValue.isBlank() ? rawValue : factCode;
        }

        RecommendationCandidateProjection toProjection() {
            RecommendationProjectionHeuristicSupport.collectSpecialTargetBuckets(specialTargetBuckets, title);
            RecommendationProjectionHeuristicSupport.collectSpecialTargetBuckets(specialTargetBuckets, summary);
            addPriorityBucket(compatPriorityBucket);
            List<String> resolvedGov24UserTypeTokens = resolveGov24UserTypeTokens();
            List<String> resolvedGov24BenefitTypeTokens = resolveGov24BenefitTypeTokens();
            String resolvedGov24ServiceFieldLabel = resolveGov24ServiceFieldLabel();
            String resolvedGov24BenefitTypeLabel = resolveGov24TokenLabel(resolvedGov24BenefitTypeTokens, gov24BenefitTypeLabel);
            Gov24TaxonomyCodeSupport.YouthBridge youthBridge = Gov24TaxonomyCodeSupport.youthBridge(
                    resolvedGov24ServiceFieldLabel,
                    resolvedGov24BenefitTypeLabel,
                    title,
                    summary
            );
            String resolvedYouthMajorLabel = firstNonBlank(youthMajorLabel, youthBridge.youthMajorLabel());
            String resolvedYouthMidLabel = firstNonBlank(youthMidLabel, youthBridge.youthMidLabel());
            addPriorityBucket(RecommendationProjectionHeuristicSupport.gov24ServiceFieldPriorityBucket(resolvedGov24ServiceFieldLabel));
            resolvedGov24BenefitTypeTokens.stream()
                    .map(RecommendationProjectionHeuristicSupport::gov24BenefitTypePriorityBucket)
                    .forEach(this::addPriorityBucket);
            return RecommendationCandidateProjection.builder()
                    .serviceId(serviceId)
                    .sourceType(sourceType)
                    .unifiedCategoryCompat(unifiedCategoryCompat)
                    .compatCategoryCode(compatCategoryCode)
                    .compatPriorityBucket(compatPriorityBucket)
                    .youthMajorLabel(resolvedYouthMajorLabel)
                    .youthMidLabel(resolvedYouthMidLabel)
                    .provisionMethodLabel(provisionMethodLabel)
                    .gov24ServiceFieldLabel(resolvedGov24ServiceFieldLabel)
                    .gov24UserTypeLabel(resolveGov24TokenLabel(resolvedGov24UserTypeTokens, gov24UserTypeLabel))
                    .gov24BenefitTypeLabel(resolvedGov24BenefitTypeLabel)
                    .gov24UserTypeTokens(resolvedGov24UserTypeTokens)
                    .gov24BenefitTypeTokens(resolvedGov24BenefitTypeTokens)
                    .youthEmploymentRequirementCodes(List.copyOf(youthEmploymentRequirementCodes))
                    .youthEmploymentRequirementLabels(List.copyOf(youthEmploymentRequirementLabels))
                    .youthEducationRequirementCodes(List.copyOf(youthEducationRequirementCodes))
                    .youthEducationRequirementLabels(List.copyOf(youthEducationRequirementLabels))
                    .youthSpecialRequirementCodes(List.copyOf(youthSpecialRequirementCodes))
                    .youthSpecialRequirementLabels(List.copyOf(youthSpecialRequirementLabels))
                    .youthMaritalStatusCode(youthMaritalStatusCode)
                    .youthMaritalStatusLabel(youthMaritalStatusLabel)
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
                                    resolvedYouthMajorLabel
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

        private String resolveGov24ServiceFieldLabel() {
            if (gov24ServiceFieldTermLabel != null && !gov24ServiceFieldTermLabel.isBlank()) {
                return gov24ServiceFieldTermLabel;
            }
            return gov24ServiceFieldLabel;
        }

        private List<String> resolveGov24UserTypeTokens() {
            if (!gov24UserTypeTokens.isEmpty()) {
                return List.copyOf(gov24UserTypeTokens);
            }
            return Gov24LabelTokenSupport.userTypeTokens(gov24UserTypeLabel);
        }

        private List<String> resolveGov24BenefitTypeTokens() {
            if (!gov24BenefitTypeTokens.isEmpty()) {
                return List.copyOf(gov24BenefitTypeTokens);
            }
            return Gov24LabelTokenSupport.benefitTypeTokens(gov24BenefitTypeLabel);
        }

        private String resolveGov24TokenLabel(List<String> tokens, String fallbackLabel) {
            if (tokens != null && !tokens.isEmpty()) {
                return String.join("||", tokens);
            }
            return fallbackLabel;
        }

        private void addPriorityBucket(String priorityBucket) {
            if (priorityBucket != null) {
                priorityBuckets.add(priorityBucket);
            }
        }

        private String firstNonBlank(String first, String second) {
            if (first != null && !first.isBlank()) {
                return first;
            }
            return second;
        }

    }
}
