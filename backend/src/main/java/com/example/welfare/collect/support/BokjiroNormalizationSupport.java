package com.example.welfare.collect.support;

import com.example.welfare.collect.dto.BokjiroCentralDto;
import com.example.welfare.collect.dto.BokjiroLocalDto;
import com.example.welfare.collect.gateway.BokjiroDetailClient;
import com.example.welfare.collect.normalization.NormalizedPolicyAggregate;
import com.example.welfare.collect.validation.RawFieldValidator;
import com.example.welfare.collect.validation.TextConstraintExtractor;
import com.example.welfare.policy.entity.WelfareService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 복지로 source에 공통인 텍스트 정규화 규칙을 모은다.
 */
public final class BokjiroNormalizationSupport {

    private static final List<String> BASIC_LIVELIHOOD_LABELS = List.of(
            "국민기초생활보장수급자",
            "기초생활수급자",
            "생계급여 수급자",
            "의료급여 수급자",
            "주거급여 수급자",
            "교육급여 수급자",
            "수급권자"
    );
    private static final Set<String> HOUSING_HINTS = Set.of(
            "주거", "주택", "월세", "전세", "보증금", "임대료", "임차료", "주거급여"
    );
    private static final Set<String> FINANCE_LIFE_HINTS = Set.of(
            "생활안정", "생활지원", "생활비", "생계", "자금", "융자", "대출", "보증", "월세보증금"
    );
    private static final Set<String> CARE_HINTS = Set.of(
            "돌봄", "주간활동", "안부확인", "발달장애인"
    );
    private static final Set<String> EDUCATION_HINTS = Set.of(
            "교육", "훈련", "직무", "학습", "어학", "자격증", "장학"
    );

    private BokjiroNormalizationSupport() {
    }

    public static List<NormalizedPolicyAggregate.TaxonomyTerm> centralTerms(BokjiroCentralDto.Item item) {
        return listTerms(
                item.getLifeArray(), NormalizationKeySupport.SOURCE_FIELD_BOKJIRO_LIFE_ARRAY,
                item.getIntrsThemaArray(), NormalizationKeySupport.SOURCE_FIELD_BOKJIRO_INTEREST_THEME_ARRAY,
                item.getTrgterIndvdlArray(), NormalizationKeySupport.SOURCE_FIELD_BOKJIRO_TARGET_GROUP_ARRAY
        );
    }

    public static List<NormalizedPolicyAggregate.TaxonomyTerm> localTerms(BokjiroLocalDto.Item item) {
        List<NormalizedPolicyAggregate.TaxonomyTerm> terms = listTerms(
                item.getLifeNmArray(), NormalizationKeySupport.SOURCE_FIELD_BOKJIRO_LIFE_NM_ARRAY,
                item.getIntrsThemaNmArray(), NormalizationKeySupport.SOURCE_FIELD_BOKJIRO_INTEREST_THEME_NM_ARRAY,
                item.getTrgterIndvdlNmArray(), NormalizationKeySupport.SOURCE_FIELD_BOKJIRO_TARGET_GROUP_NM_ARRAY
        );
        int sortOrder = terms.size();
        for (String label : derivedLocalInterestThemes(item)) {
            addTaxonomyTermIfAbsent(
                    terms,
                    NormalizationKeySupport.TERM_GROUP_INTEREST_THEME,
                    null,
                    null,
                    label,
                    NormalizationKeySupport.SOURCE_FIELD_BOKJIRO_TITLE_DIGEST_PROVISION,
                    NormalizedPolicyAggregate.Authority.SYSTEM_DERIVED,
                    sortOrder++
            );
        }
        return terms;
    }

    public static List<String> derivedLocalInterestThemes(BokjiroLocalDto.Item item) {
        String normalized = localHeuristicText(item);
        if (normalized == null) {
            return List.of();
        }
        LinkedHashSet<String> labels = new LinkedHashSet<>();
        if (containsAny(normalized, HOUSING_HINTS)) {
            labels.add("주거");
        }
        if (containsAny(normalized, FINANCE_LIFE_HINTS)) {
            labels.add("생활지원");
        }
        if (containsAny(normalized, CARE_HINTS)) {
            labels.add("보호·돌봄");
        }
        if (containsAny(normalized, EDUCATION_HINTS)) {
            labels.add("교육");
        }
        return List.copyOf(labels);
    }

    public static List<String> derivedLocalProgramKeywords(BokjiroLocalDto.Item item) {
        String normalized = localHeuristicText(item);
        if (normalized == null) {
            return List.of();
        }
        LinkedHashSet<String> tags = new LinkedHashSet<>();

        if (containsAny(normalized, HOUSING_HINTS)) {
            tags.add("주거지원");
        }
        if (normalized.contains("월세") && normalized.contains("보증금")) {
            tags.add("월세보증금");
        }
        if (normalized.contains("주거급여")) {
            tags.add("주거급여지원");
        }
        if (containsAny(normalized, FINANCE_LIFE_HINTS)) {
            tags.add("금융지원");
        }
        if (normalized.contains("생활안정자금")) {
            tags.add("생활안정자금");
        }
        if (normalized.contains("융자") || normalized.contains("대출")) {
            tags.add("융자");
        }
        if (normalized.contains("바우처")) {
            tags.add("바우처");
        }
        if (containsAny(normalized, CARE_HINTS)) {
            tags.add("돌봄서비스");
        }
        if (normalized.contains("주간활동")) {
            tags.add("주간활동서비스");
        }
        if (normalized.contains("안부확인")) {
            tags.add("안부확인서비스");
        }
        if (normalized.contains("맞춤형") && normalized.contains("상담")) {
            tags.add("맞춤형상담서비스");
        } else if (normalized.contains("상담")) {
            tags.add("상담서비스");
        }
        if (containsAny(normalized, EDUCATION_HINTS)) {
            tags.add("교육지원");
        }
        if (normalized.contains("인프라") || normalized.contains("조성") || normalized.contains("구축")) {
            tags.add("인프라 구축");
        }
        return List.copyOf(tags);
    }

    public static List<NormalizedPolicyAggregate.TaxonomyTerm> detailTerms(BokjiroDetailClient.DetailPayload detailPayload) {
        List<NormalizedPolicyAggregate.TaxonomyTerm> terms = new ArrayList<>();
        int sortOrder = 0;
        for (String label : beneficiaryLabels(
                detailPayload.getTargetDetail(),
                detailPayload.getSelectionCriteria()
        )) {
            addTaxonomyTerm(
                    terms,
                    NormalizationKeySupport.TERM_GROUP_TARGET_GROUP,
                    null,
                    null,
                    label,
                    NormalizationKeySupport.SOURCE_FIELD_TARGET_DETAIL_SELECTION_CRITERIA,
                    NormalizedPolicyAggregate.Authority.SYSTEM_DERIVED,
                    sortOrder++
            );
        }
        return terms;
    }

    public static List<NormalizedPolicyAggregate.Fact> derivedFacts(WelfareService service, String evidenceText) {
        List<NormalizedPolicyAggregate.Fact> facts = new ArrayList<>();
        addRangeFact(facts,
                NormalizationKeySupport.FACT_GROUP_AGE,
                NormalizationKeySupport.FACT_CODE_BOKJIRO_AGE,
                NormalizationKeySupport.FACT_MERGE_KEY_BOKJIRO_AGE,
                "지원 연령", service.getMinAge(), service.getMaxAge(), "세",
                NormalizationKeySupport.SOURCE_FIELD_BOKJIRO_DIGEST, NormalizedPolicyAggregate.Authority.RULE_DERIVED,
                BigDecimal.valueOf(0.90), evidenceText);
        addDateFact(facts,
                NormalizationKeySupport.FACT_GROUP_APPLY_END_DATE,
                NormalizationKeySupport.FACT_CODE_BOKJIRO_APPLY_END_DATE,
                NormalizationKeySupport.FACT_MERGE_KEY_BOKJIRO_APPLY_END_DATE,
                "신청 종료일", service.getApplyEndDate(),
                NormalizationKeySupport.SOURCE_FIELD_BOKJIRO_DIGEST, NormalizedPolicyAggregate.Authority.RULE_DERIVED,
                BigDecimal.valueOf(0.80), evidenceText);
        return facts;
    }

    public static List<NormalizedPolicyAggregate.Fact> detailFacts(BokjiroDetailClient.DetailPayload detailPayload) {
        TextConstraintExtractor.ConstraintSummary constraints = TextConstraintExtractor.summarize(
                detailPayload.getTargetDetail(),
                detailPayload.getSelectionCriteria(),
                detailPayload.getApplyMethodDetail(),
                detailPayload.getSupportDetail()
        );

        String evidenceText = firstNonBlank(
                detailPayload.getTargetDetail(),
                detailPayload.getSelectionCriteria(),
                detailPayload.getApplyMethodDetail(),
                detailPayload.getSupportDetail()
        );

        List<NormalizedPolicyAggregate.Fact> facts = new ArrayList<>();
        addRangeFact(facts,
                NormalizationKeySupport.FACT_GROUP_AGE,
                NormalizationKeySupport.FACT_CODE_BOKJIRO_AGE,
                NormalizationKeySupport.FACT_MERGE_KEY_BOKJIRO_AGE,
                "지원 연령", constraints.minAge(), constraints.maxAge(), "세",
                NormalizationKeySupport.SOURCE_FIELD_TARGET_DETAIL_SELECTION_CRITERIA, NormalizedPolicyAggregate.Authority.RULE_DERIVED,
                BigDecimal.valueOf(0.90), evidenceText);
        addDateFact(facts,
                NormalizationKeySupport.FACT_GROUP_APPLY_END_DATE,
                NormalizationKeySupport.FACT_CODE_BOKJIRO_APPLY_END_DATE,
                NormalizationKeySupport.FACT_MERGE_KEY_BOKJIRO_APPLY_END_DATE,
                "신청 종료일", constraints.applyEndDate(),
                NormalizationKeySupport.SOURCE_FIELD_APPLY_METHOD_DETAIL_SUPPORT_DETAIL, NormalizedPolicyAggregate.Authority.RULE_DERIVED,
                BigDecimal.valueOf(0.80), evidenceText);
        return facts;
    }

    public static List<String> beneficiaryLabels(String... texts) {
        LinkedHashSet<String> labels = new LinkedHashSet<>();
        if (texts == null) {
            return List.of();
        }
        for (String text : texts) {
            collectBeneficiaryLabels(labels, text);
        }
        return List.copyOf(labels);
    }

    private static void collectBeneficiaryLabels(Set<String> labels, String text) {
        String normalizedText = RawFieldValidator.normalize(text);
        if (normalizedText == null) {
            return;
        }
        if (containsAny(normalizedText, BASIC_LIVELIHOOD_LABELS)) {
            labels.add("기초생활수급자");
        }
        if (normalizedText.contains("차상위")) {
            labels.add("차상위계층");
        }
    }

    private static boolean containsAny(String text, List<String> candidates) {
        for (String candidate : candidates) {
            if (text.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsAny(String text, Set<String> candidates) {
        for (String candidate : candidates) {
            if (text.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    private static List<NormalizedPolicyAggregate.TaxonomyTerm> listTerms(String lifeStageCsv,
                                                                          String lifeStageField,
                                                                          String interestThemeCsv,
                                                                          String interestThemeField,
                                                                          String targetGroupCsv,
                                                                          String targetGroupField) {
        List<NormalizedPolicyAggregate.TaxonomyTerm> terms = new ArrayList<>();
        addTaxonomyTermsFromCsv(terms, NormalizationKeySupport.TERM_GROUP_LIFE_STAGE, null, lifeStageCsv, lifeStageField,
                NormalizedPolicyAggregate.Authority.OFFICIAL, 0);
        addTaxonomyTermsFromCsv(terms, NormalizationKeySupport.TERM_GROUP_INTEREST_THEME, null, interestThemeCsv, interestThemeField,
                NormalizedPolicyAggregate.Authority.OFFICIAL, 0);
        addTaxonomyTermsFromCsv(terms, NormalizationKeySupport.TERM_GROUP_TARGET_GROUP, null, targetGroupCsv, targetGroupField,
                NormalizedPolicyAggregate.Authority.OFFICIAL, 0);
        return terms;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            String normalized = RawFieldValidator.normalize(value);
            if (normalized != null) {
                return normalized;
            }
        }
        return null;
    }

    private static void addTaxonomyTermsFromCsv(List<NormalizedPolicyAggregate.TaxonomyTerm> terms,
                                                String termGroup,
                                                String codeSetKey,
                                                String csv,
                                                String sourceField,
                                                NormalizedPolicyAggregate.Authority authority,
                                                int startSortOrder) {
        if (csv == null || csv.isBlank()) {
            return;
        }
        int sortOrder = startSortOrder;
        for (String raw : csv.split(",")) {
            String label = RawFieldValidator.normalize(raw == null ? null : raw.strip());
            if (label == null) {
                continue;
            }
            terms.add(NormalizedPolicyAggregate.TaxonomyTerm.builder()
                    .termGroup(termGroup)
                    .codeSetKey(codeSetKey)
                    .termCode(null)
                    .termLabel(label)
                    .sourceField(sourceField)
                    .authority(authority)
                    .sortOrder(sortOrder++)
                    .build());
        }
    }

    private static void addTaxonomyTerm(List<NormalizedPolicyAggregate.TaxonomyTerm> terms,
                                        String termGroup,
                                        String codeSetKey,
                                        String termCode,
                                        String termLabel,
                                        String sourceField,
                                        NormalizedPolicyAggregate.Authority authority,
                                        int sortOrder) {
        String normalizedLabel = RawFieldValidator.normalize(termLabel);
        if (normalizedLabel == null) {
            return;
        }
        terms.add(NormalizedPolicyAggregate.TaxonomyTerm.builder()
                .termGroup(termGroup)
                .codeSetKey(codeSetKey)
                .termCode(termCode)
                .termLabel(normalizedLabel)
                .sourceField(sourceField)
                .authority(authority)
                .sortOrder(sortOrder)
                .build());
    }

    private static void addTaxonomyTermIfAbsent(List<NormalizedPolicyAggregate.TaxonomyTerm> terms,
                                                String termGroup,
                                                String codeSetKey,
                                                String termCode,
                                                String termLabel,
                                                String sourceField,
                                                NormalizedPolicyAggregate.Authority authority,
                                                int sortOrder) {
        String normalizedLabel = RawFieldValidator.normalize(termLabel);
        if (normalizedLabel == null) {
            return;
        }
        boolean exists = terms.stream().anyMatch(term ->
                normalizedLabel.equals(term.termLabel()) && termGroup.equals(term.termGroup()));
        if (exists) {
            return;
        }
        addTaxonomyTerm(terms, termGroup, codeSetKey, termCode, normalizedLabel, sourceField, authority, sortOrder);
    }

    private static String localHeuristicText(BokjiroLocalDto.Item item) {
        return normalizeJoined(
                item.getServNm(),
                item.getServDgst(),
                item.getSrvPvsnNm(),
                item.getAplyMtdNm()
        );
    }

    private static String normalizeJoined(String... values) {
        LinkedHashSet<String> segments = new LinkedHashSet<>();
        if (values == null) {
            return null;
        }
        for (String value : values) {
            String normalized = RawFieldValidator.normalize(value);
            if (normalized != null) {
                segments.add(normalized.toLowerCase(Locale.ROOT));
            }
        }
        if (segments.isEmpty()) {
            return null;
        }
        return String.join(" ", segments);
    }

    private static void addRangeFact(List<NormalizedPolicyAggregate.Fact> facts,
                                     String factGroup,
                                     String factCode,
                                     String factMergeKey,
                                     String factLabel,
                                     Integer rangeMin,
                                     Integer rangeMax,
                                     String unit,
                                     String sourceField,
                                     NormalizedPolicyAggregate.Authority authority,
                                     BigDecimal confidence,
                                     String evidenceText) {
        if (rangeMin == null && rangeMax == null) {
            return;
        }
        facts.add(NormalizedPolicyAggregate.Fact.builder()
                .factGroup(factGroup)
                .factCodeSetKey(null)
                .factCode(factCode)
                .factMergeKey(factMergeKey)
                .factLabel(factLabel)
                .operator(rangeMin != null && rangeMax != null
                        ? NormalizedPolicyAggregate.Operator.RANGE
                        : rangeMin != null
                        ? NormalizedPolicyAggregate.Operator.GTE
                        : NormalizedPolicyAggregate.Operator.LTE)
                .valueType(NormalizedPolicyAggregate.ValueType.INTEGER)
                .rangeMinInt(rangeMin)
                .rangeMaxInt(rangeMax)
                .unit(unit)
                .sourceField(sourceField)
                .authority(authority)
                .confidence(confidence)
                .rawValue(firstNonBlank(
                        rangeMin == null ? null : String.valueOf(rangeMin),
                        rangeMax == null ? null : String.valueOf(rangeMax)
                ))
                .evidenceText(RawFieldValidator.normalize(evidenceText))
                .build());
    }

    private static void addDateFact(List<NormalizedPolicyAggregate.Fact> facts,
                                    String factGroup,
                                    String factCode,
                                    String factMergeKey,
                                    String factLabel,
                                    LocalDate dateValue,
                                    String sourceField,
                                    NormalizedPolicyAggregate.Authority authority,
                                    BigDecimal confidence,
                                    String evidenceText) {
        if (dateValue == null) {
            return;
        }
        facts.add(NormalizedPolicyAggregate.Fact.builder()
                .factGroup(factGroup)
                .factCodeSetKey(null)
                .factCode(factCode)
                .factMergeKey(factMergeKey)
                .factLabel(factLabel)
                .operator(NormalizedPolicyAggregate.Operator.EQ)
                .valueType(NormalizedPolicyAggregate.ValueType.DATE)
                .dateValue(dateValue)
                .sourceField(sourceField)
                .authority(authority)
                .confidence(confidence)
                .rawValue(dateValue.toString())
                .evidenceText(RawFieldValidator.normalize(evidenceText))
                .build());
    }
}
