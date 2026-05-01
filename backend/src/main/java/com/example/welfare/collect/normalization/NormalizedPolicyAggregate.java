package com.example.welfare.collect.normalization;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 수집 결과를 canonical 구조로 표현하는 내부 DTO.
 * JPA entity / sidecar schema와 직접 결합하지 않고 mapper와 saver 사이의 계약으로만 사용한다.
 */
@Builder
public record NormalizedPolicyAggregate(
        Core core,
        Detail detail,
        TaxonomySummary taxonomy,
        List<TaxonomyTerm> taxonomyTerms,
        List<Fact> facts
) {

    public NormalizedPolicyAggregate {
        taxonomyTerms = taxonomyTerms == null ? List.of() : List.copyOf(taxonomyTerms);
        facts = facts == null ? List.of() : List.copyOf(facts);
    }

    @Builder
    public record Core(
            SourceType sourceType,
            String sourceId,
            String title,
            String summary,
            String description,
            String supportContent,
            String hostOrg,
            String operatingOrg,
            ServiceStatus status,
            LocalDate startDate,
            LocalDate endDate,
            LocalDate applyStartDate,
            LocalDate applyEndDate,
            String detailUrl,
            Boolean onlineApply,
            Long apiViewCount,
            LocalDateTime registeredAt,
            LocalDateTime lastModifiedAt
    ) {
    }

    @Builder
    public record Detail(
            String targetDetail,
            String supportDetail,
            String applyMethodDetail,
            String selectionCriteria,
            String requiredDocuments,
            String contactText,
            String legalBasisText,
            String onlineApplyUrl,
            String supportCycle,
            String provisionType
    ) {
    }

    @Builder
    public record TaxonomySummary(
            String compatUnifiedCategory,
            String provisionMethod,
            Map<String, String> summaryLabels,
            Authority authority,
            BigDecimal confidence
    ) {
        public TaxonomySummary {
            summaryLabels = summaryLabels == null ? Map.of() : Map.copyOf(summaryLabels);
        }

        public String summaryLabel(String key) {
            if (key == null) {
                return null;
            }
            return summaryLabels.get(key);
        }
    }

    @Builder
    public record TaxonomyTerm(
            String termGroup,
            String codeSetKey,
            String termCode,
            String termLabel,
            String sourceField,
            Authority authority,
            Integer sortOrder
    ) {
    }

    @Builder
    public record Fact(
            String factGroup,
            String factCodeSetKey,
            String factCode,
            String factMergeKey,
            String factLabel,
            Operator operator,
            ValueType valueType,
            Boolean boolValue,
            Integer intValue,
            BigDecimal decimalValue,
            String textValue,
            LocalDate dateValue,
            Integer rangeMinInt,
            Integer rangeMaxInt,
            String unit,
            String sourceField,
            Authority authority,
            BigDecimal confidence,
            String rawValue,
            String evidenceText
    ) {
    }

    public enum Authority {
        OFFICIAL,
        SYSTEM_DERIVED,
        RULE_DERIVED,
        AI_ENRICHED
    }

    public enum SourceType {
        YOUTH,
        BOKJIRO_CENTRAL,
        BOKJIRO_LOCAL
    }

    public enum ServiceStatus {
        ACTIVE,
        UPCOMING,
        CLOSED
    }

    public enum Operator {
        EQ,
        GTE,
        LTE,
        RANGE,
        FLAG,
        MEMBER
    }

    public enum ValueType {
        BOOLEAN,
        INTEGER,
        DECIMAL,
        STRING,
        DATE
    }
}
