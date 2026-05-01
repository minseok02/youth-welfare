package com.example.welfare.recommend.repository;

import com.example.welfare.recommend.dto.RecommendationCandidateProjection;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 추천 후보 service id 목록에 대해 canonical sidecar 기반 projection을 읽는다.
 * 아직 retrieval/scoring path에는 연결하지 않고, recommendation read-model 경계만 먼저 고정한다.
 */
@Repository
@RequiredArgsConstructor
public class CanonicalRecommendationReadModelRepository {

    static final String BENEFICIARY_SUPPORT_BUCKET = "BENEFICIARY_SUPPORT";
    static final String PRIORITY_BUCKET_HOUSING = "HOUSING";
    static final String PRIORITY_BUCKET_JOB = "JOB";
    static final String PRIORITY_BUCKET_EDUCATION = "EDUCATION";
    static final String PRIORITY_BUCKET_FINANCE = "FINANCE";
    static final String PRIORITY_BUCKET_CULTURE = "CULTURE";
    static final String PRIORITY_BUCKET_PARTICIPATION = "PARTICIPATION";
    static final String PRIORITY_BUCKET_FAMILY = "FAMILY";
    static final String SPECIAL_TARGET_RURAL = "농어촌";
    static final String SPECIAL_TARGET_SELF_RELIANCE = "자립준비청년";
    static final String SPECIAL_TARGET_FAMILY_CARE = "가족돌봄";
    static final String SPECIAL_TARGET_MULTICULTURAL = "다문화";
    static final String SPECIAL_TARGET_DEFECTOR = "북한이탈";
    static final String SPECIAL_TARGET_SINGLE_PARENT = "한부모";
    static final String SPECIAL_TARGET_GRANDPARENT = "조손";
    static final String SPECIAL_TARGET_VETERAN = "보훈";
    static final String SPECIAL_TARGET_DISABILITY = "장애";
    static final String SPECIAL_TARGET_MILITARY = "병역";
    private static final Set<String> BENEFICIARY_TERMS = Set.of("기초생활수급자", "차상위계층");
    private static final int YOUTH_MIN_AGE = 18;
    private static final int YOUTH_MAX_AGE = 39;
    private static final double EXPLICIT_YOUTH_BONUS = 15.0;
    private static final double FOCUSED_LIFE_STAGE_BONUS = 8.0;
    private static final double AGE_RANGE_ONLY_BONUS = 3.0;
    private static final Set<String> YOUTH_SIGNALS = Set.of(
            "청년",
            "미취업청년",
            "취업준비생",
            "사회초년생",
            "대학생",
            "대학원생",
            "청년층"
    );
    private static final Set<String> BROAD_LIFE_STAGE_SIGNALS = Set.of(
            "영유아",
            "아동",
            "청소년",
            "중장년",
            "노년",
            "임신",
            "출산"
    );

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

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
            projection.addFactKey(stringValue(row.get("fact_merge_key")));
        }

        LinkedHashMap<Long, RecommendationCandidateProjection> result = new LinkedHashMap<>();
        for (MutableProjection projection : projections.values()) {
            result.put(projection.serviceId, projection.toProjection());
        }
        return result;
    }

    private List<Map<String, Object>> baseRows(MapSqlParameterSource params) {
        return namedParameterJdbcTemplate.queryForList("""
                SELECT ws.id AS service_id,
                       ws.source_type,
                       ws.unified_category,
                       st.youth_major_label,
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
                       fact_merge_key
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
        private final String youthMajorLabel;
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
            this.youthMajorLabel = youthMajorLabel;
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
                    if ("targetDetail/selectionCriteria".equals(sourceField) && BENEFICIARY_TERMS.contains(termLabel)) {
                        beneficiaryTerms.add(termLabel);
                        targetGroupBuckets.add(BENEFICIARY_SUPPORT_BUCKET);
                    }
                }
                default -> {
                    // no-op
                }
            }
            addSpecialTargetBucket(termLabel);
        }

        void addFactKey(String factMergeKey) {
            if (factMergeKey != null && !factMergeKey.isBlank()) {
                factKeys.add(factMergeKey);
            }
        }

        RecommendationCandidateProjection toProjection() {
            addSpecialTargetBucket(title);
            addSpecialTargetBucket(summary);
            addPriorityBucket(unifiedCategoryCompat);
            return RecommendationCandidateProjection.builder()
                    .serviceId(serviceId)
                    .sourceType(sourceType)
                    .unifiedCategoryCompat(unifiedCategoryCompat)
                    .youthMajorLabel(youthMajorLabel)
                    .title(title)
                    .summary(summary)
                    .minAge(minAge)
                    .maxAge(maxAge)
                    .incomeMinLegacy(incomeMinLegacy)
                    .incomeMaxLegacy(incomeMaxLegacy)
                    .applyEndDate(applyEndDate)
                    .youthRelevant(youthRelevant)
                    .audienceRelevanceBonus(computeAudienceRelevanceBonus())
                    .educationPriorityBoostEligible(isEducationPriorityBoostEligible())
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

        private double computeAudienceRelevanceBonus() {
            boolean explicitYouthSignal = containsExplicitYouthSignal();
            boolean focusedLifeStage = hasYouthFocusedLifeStage();
            boolean youthFocusedAgeRange = isYouthFocusedAgeRange();

            double bonus = 0.0;
            if (explicitYouthSignal) {
                bonus += EXPLICIT_YOUTH_BONUS;
            }
            if (focusedLifeStage) {
                bonus += FOCUSED_LIFE_STAGE_BONUS;
            }
            if (!explicitYouthSignal && !focusedLifeStage && youthFocusedAgeRange) {
                bonus += AGE_RANGE_ONLY_BONUS;
            }
            return bonus;
        }

        private boolean containsExplicitYouthSignal() {
            return audienceTextSignals().stream().anyMatch(MutableProjection::containsYouthSignal);
        }

        private boolean hasYouthFocusedLifeStage() {
            if (lifeStages.isEmpty()) {
                return false;
            }
            boolean hasYouth = lifeStages.stream().anyMatch(MutableProjection::containsYouthSignal);
            boolean hasBroadOtherStage = lifeStages.stream()
                    .map(MutableProjection::normalize)
                    .filter(value -> value != null)
                    .anyMatch(value -> BROAD_LIFE_STAGE_SIGNALS.stream().anyMatch(value::contains));
            return hasYouth && !hasBroadOtherStage;
        }

        private boolean isYouthFocusedAgeRange() {
            if (minAge == null && maxAge == null) {
                return false;
            }
            if (maxAge == null) {
                return false;
            }
            int effectiveMin = minAge != null ? minAge : 0;
            return effectiveMin <= YOUTH_MAX_AGE && maxAge <= YOUTH_MAX_AGE && maxAge >= YOUTH_MIN_AGE;
        }

        private List<String> audienceTextSignals() {
            List<String> signals = new ArrayList<>();
            if (title != null) signals.add(title);
            if (summary != null) signals.add(summary);
            signals.addAll(targetGroupsRaw);
            signals.addAll(keywordTags);
            signals.addAll(interestThemes);
            signals.addAll(lifeStages);
            return signals;
        }

        private void addSpecialTargetBucket(String raw) {
            String normalized = normalize(raw);
            if (normalized == null || normalized.isBlank()) {
                return;
            }
            if (normalized.contains("장애")) {
                specialTargetBuckets.add(SPECIAL_TARGET_DISABILITY);
            }
            if (normalized.contains("농어촌") || normalized.contains("농촌") || normalized.contains("어촌")) {
                specialTargetBuckets.add(SPECIAL_TARGET_RURAL);
            }
            if (normalized.contains("자립준비") || normalized.contains("보호종료")) {
                specialTargetBuckets.add(SPECIAL_TARGET_SELF_RELIANCE);
            }
            if (normalized.contains("가족돌봄")) {
                specialTargetBuckets.add(SPECIAL_TARGET_FAMILY_CARE);
            }
            if (normalized.contains("다문화")) {
                specialTargetBuckets.add(SPECIAL_TARGET_MULTICULTURAL);
            }
            if (normalized.contains("북한이탈")) {
                specialTargetBuckets.add(SPECIAL_TARGET_DEFECTOR);
            }
            if (normalized.contains("한부모")) {
                specialTargetBuckets.add(SPECIAL_TARGET_SINGLE_PARENT);
            }
            if (normalized.contains("조손")) {
                specialTargetBuckets.add(SPECIAL_TARGET_GRANDPARENT);
            }
            if (normalized.contains("보훈")) {
                specialTargetBuckets.add(SPECIAL_TARGET_VETERAN);
            }
            if (normalized.contains("현역병") || normalized.contains("병역")) {
                specialTargetBuckets.add(SPECIAL_TARGET_MILITARY);
            }
        }

        private void addPriorityBucket(String compatCategory) {
            if (compatCategory == null || compatCategory.isBlank()) {
                return;
            }
            switch (compatCategory) {
                case "주거" -> priorityBuckets.add(PRIORITY_BUCKET_HOUSING);
                case "일자리" -> priorityBuckets.add(PRIORITY_BUCKET_JOB);
                case "교육·직업훈련" -> priorityBuckets.add(PRIORITY_BUCKET_EDUCATION);
                case "금융·생활지원" -> priorityBuckets.add(PRIORITY_BUCKET_FINANCE);
                case "문화·여가" -> priorityBuckets.add(PRIORITY_BUCKET_CULTURE);
                case "참여·기회" -> priorityBuckets.add(PRIORITY_BUCKET_PARTICIPATION);
                case "가족·돌봄" -> priorityBuckets.add(PRIORITY_BUCKET_FAMILY);
                default -> {
                    // no-op
                }
            }
        }

        private boolean isEducationPriorityBoostEligible() {
            return "기타".equals(unifiedCategoryCompat) && "교육".equals(youthMajorLabel);
        }

        private static boolean containsYouthSignal(String raw) {
            String normalized = normalize(raw);
            if (normalized == null) {
                return false;
            }
            return YOUTH_SIGNALS.stream()
                    .map(MutableProjection::normalize)
                    .filter(value -> value != null)
                    .anyMatch(normalized::contains);
        }

        private static String normalize(String raw) {
            if (raw == null) {
                return null;
            }
            String trimmed = raw.trim();
            if (trimmed.isBlank()) {
                return null;
            }
            return trimmed.toLowerCase(Locale.ROOT);
        }
    }
}
