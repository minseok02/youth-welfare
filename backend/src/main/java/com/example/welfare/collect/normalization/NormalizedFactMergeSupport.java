package com.example.welfare.collect.normalization;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * future service_facts saver 에서 사용할 merge/upsert 우선순위를 canonical Fact 단계에서 먼저 고정한다.
 */
public class NormalizedFactMergeSupport {

    private static final Map<NormalizedPolicyAggregate.Authority, Integer> AUTHORITY_PRIORITY = Map.of(
            NormalizedPolicyAggregate.Authority.OFFICIAL, 0,
            NormalizedPolicyAggregate.Authority.SYSTEM_DERIVED, 1,
            NormalizedPolicyAggregate.Authority.RULE_DERIVED, 2,
            NormalizedPolicyAggregate.Authority.AI_ENRICHED, 3
    );

    private static final Map<String, Integer> SOURCE_FIELD_PRIORITY = Map.of(
            "targetDetail", 0,
            "selectionCriteria", 1,
            "servDgst", 2,
            "applyMethodDetail", 3,
            "supportDetail", 4
    );

    public List<NormalizedPolicyAggregate.Fact> merge(
            List<NormalizedPolicyAggregate.Fact> existingFacts,
            List<NormalizedPolicyAggregate.Fact> incomingFacts
    ) {
        LinkedHashMap<String, NormalizedPolicyAggregate.Fact> merged = new LinkedHashMap<>();
        existingFacts.forEach(fact -> putBest(merged, fact));
        incomingFacts.forEach(fact -> putBest(merged, fact));
        return List.copyOf(merged.values());
    }

    private void putBest(Map<String, NormalizedPolicyAggregate.Fact> merged,
                         NormalizedPolicyAggregate.Fact candidate) {
        String mergeKey = mergeKeyOf(candidate);
        NormalizedPolicyAggregate.Fact current = merged.get(mergeKey);
        if (current == null || shouldReplace(current, candidate)) {
            merged.put(mergeKey, candidate);
        }
    }

    private boolean shouldReplace(NormalizedPolicyAggregate.Fact current,
                                  NormalizedPolicyAggregate.Fact candidate) {
        int authorityOrder = Integer.compare(
                authorityPriority(candidate.authority()),
                authorityPriority(current.authority())
        );
        if (authorityOrder != 0) {
            return authorityOrder < 0;
        }

        int confidenceOrder = compareConfidence(candidate.confidence(), current.confidence());
        if (confidenceOrder != 0) {
            return confidenceOrder > 0;
        }

        int sourceFieldOrder = Integer.compare(
                sourceFieldPriority(candidate.sourceField()),
                sourceFieldPriority(current.sourceField())
        );
        if (sourceFieldOrder != 0) {
            return sourceFieldOrder < 0;
        }

        return false;
    }

    private String mergeKeyOf(NormalizedPolicyAggregate.Fact fact) {
        if (fact == null || fact.factMergeKey() == null || fact.factMergeKey().isBlank()) {
            throw new IllegalArgumentException("factMergeKey 는 필수입니다.");
        }
        return fact.factMergeKey();
    }

    private int authorityPriority(NormalizedPolicyAggregate.Authority authority) {
        return AUTHORITY_PRIORITY.getOrDefault(authority, Integer.MAX_VALUE);
    }

    private int compareConfidence(BigDecimal left, BigDecimal right) {
        BigDecimal normalizedLeft = left == null ? BigDecimal.ZERO : left;
        BigDecimal normalizedRight = right == null ? BigDecimal.ZERO : right;
        return normalizedLeft.compareTo(normalizedRight);
    }

    private int sourceFieldPriority(String sourceField) {
        if (sourceField == null || sourceField.isBlank()) {
            return Integer.MAX_VALUE;
        }

        int priority = Integer.MAX_VALUE;
        for (String token : sourceField.split("/")) {
            String normalized = token == null ? "" : token.strip();
            priority = Math.min(priority, SOURCE_FIELD_PRIORITY.getOrDefault(normalized, Integer.MAX_VALUE));
        }
        return priority;
    }
}
