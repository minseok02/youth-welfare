package com.example.welfare.collect.support;

import com.example.welfare.collect.dto.YouthApiDto;
import com.example.welfare.collect.normalization.NormalizedPolicyAggregate;
import com.example.welfare.collect.validation.RawFieldValidator;
import com.example.welfare.policy.entity.WelfareService;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

public final class YouthNormalizationSupport {

    private YouthNormalizationSupport() {
    }

    public static Map<String, String> summaryLabels(WelfareService service, YouthMidPartition youthMidPartition) {
        LinkedHashMap<String, String> labels = new LinkedHashMap<>();
        putIfPresent(labels, NormalizationKeySupport.SUMMARY_KEY_YOUTH_MAJOR, service.getCategoryMain());
        putIfPresent(labels, NormalizationKeySupport.SUMMARY_KEY_YOUTH_MID, youthMidPartition.summaryLabel());
        return Map.copyOf(labels);
    }

    public static YouthMidPartition partitionYouthMidLabels(String rawYouthMid) {
        if (rawYouthMid == null || rawYouthMid.isBlank()) {
            return new YouthMidPartition(List.of(), List.of());
        }

        LinkedHashSet<String> officialLabels = new LinkedHashSet<>();
        LinkedHashSet<String> rawAliases = new LinkedHashSet<>();
        for (String rawToken : rawYouthMid.split(",")) {
            String label = RawFieldValidator.normalize(rawToken == null ? null : rawToken.strip());
            if (label == null) {
                continue;
            }
            if (NormalizationKeySupport.isOfficialYouthMidLabel(label)) {
                officialLabels.add(label);
            } else {
                rawAliases.add(label);
            }
        }
        return new YouthMidPartition(List.copyOf(officialLabels), List.copyOf(rawAliases));
    }

    public static List<NormalizedPolicyAggregate.TaxonomyTerm> taxonomyTerms(YouthApiDto.Item item,
                                                                             YouthMidPartition youthMidPartition) {
        List<NormalizedPolicyAggregate.TaxonomyTerm> terms = new ArrayList<>();
        addTaxonomyTerm(terms,
                NormalizationKeySupport.TERM_GROUP_YOUTH_MAJOR,
                NormalizationKeySupport.TERM_GROUP_YOUTH_MAJOR,
                null,
                item.getLclsfNm(),
                NormalizationKeySupport.SOURCE_FIELD_YOUTH_MAJOR,
                NormalizedPolicyAggregate.Authority.OFFICIAL, 0);

        int sortOrder = 0;
        for (String label : youthMidPartition.officialLabels()) {
            addTaxonomyTerm(terms,
                    NormalizationKeySupport.TERM_GROUP_YOUTH_MID,
                    NormalizationKeySupport.TERM_GROUP_YOUTH_MID,
                    null,
                    label,
                    NormalizationKeySupport.SOURCE_FIELD_YOUTH_CATEGORY_SUB,
                    NormalizedPolicyAggregate.Authority.OFFICIAL, sortOrder++);
        }
        for (String label : youthMidPartition.rawAliases()) {
            addTaxonomyTerm(terms,
                    NormalizationKeySupport.TERM_GROUP_YOUTH_MID_RAW_ALIAS,
                    null,
                    null,
                    label,
                    NormalizationKeySupport.SOURCE_FIELD_YOUTH_CATEGORY_SUB,
                    NormalizedPolicyAggregate.Authority.OFFICIAL, sortOrder++);
        }
        addTaxonomyTermsFromCsv(terms,
                NormalizationKeySupport.TERM_GROUP_YOUTH_KEYWORD,
                NormalizationKeySupport.TERM_GROUP_YOUTH_KEYWORD,
                item.getPlcyKywdNm(),
                NormalizationKeySupport.SOURCE_FIELD_YOUTH_KEYWORD,
                NormalizedPolicyAggregate.Authority.OFFICIAL, 0);
        return terms;
    }

    public static List<NormalizedPolicyAggregate.Fact> facts(WelfareService service) {
        return facts(service, null);
    }

    public static List<NormalizedPolicyAggregate.Fact> facts(WelfareService service, YouthApiDto.Item item) {
        List<NormalizedPolicyAggregate.Fact> facts = new ArrayList<>();
        addRangeFact(facts,
                NormalizationKeySupport.FACT_GROUP_AGE,
                NormalizationKeySupport.FACT_CODE_YOUTH_AGE,
                NormalizationKeySupport.FACT_MERGE_KEY_YOUTH_AGE,
                "지원 연령", service.getMinAge(), service.getMaxAge(), "세",
                NormalizationKeySupport.SOURCE_FIELD_YOUTH_AGE, NormalizedPolicyAggregate.Authority.OFFICIAL, BigDecimal.ONE, null);
        addBoundaryFact(facts,
                NormalizationKeySupport.FACT_GROUP_INCOME,
                NormalizationKeySupport.FACT_CODE_YOUTH_INCOME_MIN,
                NormalizationKeySupport.FACT_MERGE_KEY_YOUTH_INCOME_MIN,
                "소득 하한", service.getMinIncome(),
                NormalizedPolicyAggregate.Operator.GTE, "legacy-int", NormalizationKeySupport.SOURCE_FIELD_YOUTH_INCOME_MIN,
                NormalizedPolicyAggregate.Authority.OFFICIAL, BigDecimal.ONE, null);
        addBoundaryFact(facts,
                NormalizationKeySupport.FACT_GROUP_INCOME,
                NormalizationKeySupport.FACT_CODE_YOUTH_INCOME_MAX,
                NormalizationKeySupport.FACT_MERGE_KEY_YOUTH_INCOME_MAX,
                "소득 상한", service.getMaxIncome(),
                NormalizedPolicyAggregate.Operator.LTE, "legacy-int", NormalizationKeySupport.SOURCE_FIELD_YOUTH_INCOME_MAX,
                NormalizedPolicyAggregate.Authority.OFFICIAL, BigDecimal.ONE, null);
        addDateFact(facts,
                NormalizationKeySupport.FACT_GROUP_APPLY_END_DATE,
                NormalizationKeySupport.FACT_CODE_YOUTH_APPLY_END_DATE,
                NormalizationKeySupport.FACT_MERGE_KEY_YOUTH_APPLY_END_DATE,
                "신청 종료일", service.getApplyEndDate(),
                NormalizationKeySupport.SOURCE_FIELD_YOUTH_APPLY_END_DATE, NormalizedPolicyAggregate.Authority.OFFICIAL, BigDecimal.ONE, null);
        addEmploymentRequirementFact(facts, item);
        addIncomeConditionTypeFact(facts, item);
        return facts;
    }

    private static void putIfPresent(Map<String, String> labels, String key, String value) {
        String normalizedKey = RawFieldValidator.normalize(key);
        String normalizedValue = RawFieldValidator.normalize(value);
        if (normalizedKey != null && normalizedValue != null) {
            labels.put(normalizedKey, normalizedValue);
        }
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

    private static void addBoundaryFact(List<NormalizedPolicyAggregate.Fact> facts,
                                        String factGroup,
                                        String factCode,
                                        String factMergeKey,
                                        String factLabel,
                                        Integer intValue,
                                        NormalizedPolicyAggregate.Operator operator,
                                        String unit,
                                        String sourceField,
                                        NormalizedPolicyAggregate.Authority authority,
                                        BigDecimal confidence,
                                        String evidenceText) {
        if (intValue == null) {
            return;
        }
        facts.add(NormalizedPolicyAggregate.Fact.builder()
                .factGroup(factGroup)
                .factCodeSetKey(null)
                .factCode(factCode)
                .factMergeKey(factMergeKey)
                .factLabel(factLabel)
                .operator(operator)
                .valueType(NormalizedPolicyAggregate.ValueType.INTEGER)
                .intValue(intValue)
                .unit(unit)
                .sourceField(sourceField)
                .authority(authority)
                .confidence(confidence)
                .rawValue(String.valueOf(intValue))
                .evidenceText(RawFieldValidator.normalize(evidenceText))
                .build());
    }

    private static void addDateFact(List<NormalizedPolicyAggregate.Fact> facts,
                                    String factGroup,
                                    String factCode,
                                    String factMergeKey,
                                    String factLabel,
                                    java.time.LocalDate dateValue,
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

    private static void addIncomeConditionTypeFact(List<NormalizedPolicyAggregate.Fact> facts, YouthApiDto.Item item) {
        if (item == null) {
            return;
        }

        List<String> codes = YouthOfficialCodeSupport.splitOfficialCodes(item.getEarnCndSeCd());
        List<String> labels = YouthOfficialCodeSupport.resolveIncomeConditionTypeLabels(item.getEarnCndSeCd());
        if (codes.size() != 1 || labels.size() != 1) {
            return;
        }

        String code = codes.get(0);
        String label = labels.get(0);
        facts.add(NormalizedPolicyAggregate.Fact.builder()
                .factGroup(NormalizationKeySupport.FACT_GROUP_INCOME)
                .factCodeSetKey(NormalizationKeySupport.FACT_CODE_YOUTH_INCOME_CONDITION_TYPE)
                .factCode(code)
                .factMergeKey(NormalizationKeySupport.FACT_MERGE_KEY_YOUTH_INCOME_CONDITION_TYPE)
                .factLabel("소득조건 구분")
                .operator(NormalizedPolicyAggregate.Operator.EQ)
                .valueType(NormalizedPolicyAggregate.ValueType.STRING)
                .textValue(label)
                .sourceField(NormalizationKeySupport.SOURCE_FIELD_YOUTH_INCOME_CONDITION_TYPE)
                .authority(NormalizedPolicyAggregate.Authority.OFFICIAL)
                .confidence(BigDecimal.ONE)
                .rawValue(code)
                .evidenceText(label)
                .build());
    }

    private static void addEmploymentRequirementFact(List<NormalizedPolicyAggregate.Fact> facts, YouthApiDto.Item item) {
        if (item == null) {
            return;
        }

        List<String> codes = YouthOfficialCodeSupport.resolveEmploymentRequirementCodes(item.getJobCd());
        List<String> labels = YouthOfficialCodeSupport.resolveEmploymentRequirementLabels(item.getJobCd());
        if (codes.isEmpty() || labels.isEmpty()) {
            return;
        }

        String canonicalCodes = String.join(",", codes);
        String canonicalLabels = String.join(", ", labels);
        facts.add(NormalizedPolicyAggregate.Fact.builder()
                .factGroup(NormalizationKeySupport.FACT_GROUP_EMPLOYMENT)
                .factCodeSetKey(NormalizationKeySupport.FACT_CODE_YOUTH_EMPLOYMENT_REQUIREMENT)
                .factCode(canonicalCodes)
                .factMergeKey(NormalizationKeySupport.FACT_MERGE_KEY_YOUTH_EMPLOYMENT_REQUIREMENT)
                .factLabel("취업 요건")
                .operator(NormalizedPolicyAggregate.Operator.MEMBER)
                .valueType(NormalizedPolicyAggregate.ValueType.STRING)
                .textValue(canonicalLabels)
                .sourceField(NormalizationKeySupport.SOURCE_FIELD_YOUTH_EMPLOYMENT_REQUIREMENT)
                .authority(NormalizedPolicyAggregate.Authority.OFFICIAL)
                .confidence(BigDecimal.ONE)
                .rawValue(canonicalCodes)
                .evidenceText(canonicalLabels)
                .build());
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

    public record YouthMidPartition(
            List<String> officialLabels,
            List<String> rawAliases
    ) {
        public String summaryLabel() {
            return rawAliases.isEmpty() && officialLabels.size() == 1
                    ? officialLabels.get(0)
                    : null;
        }
    }
}
