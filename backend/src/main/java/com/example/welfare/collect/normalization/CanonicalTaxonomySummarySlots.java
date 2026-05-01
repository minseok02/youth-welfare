package com.example.welfare.collect.normalization;

import com.example.welfare.collect.normalization.NormalizedPolicyAggregate.TaxonomySummary;
import com.example.welfare.collect.support.NormalizationKeySupport;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * canonical taxonomy summary를 source-neutral slot 집합으로 읽어낸다.
 * legacy DB bridge와 generic read 경계가 같은 slot catalog를 보도록 유지한다.
 */
public final class CanonicalTaxonomySummarySlots {

    public static final String SLOT_YOUTH_MAJOR = NormalizationKeySupport.SUMMARY_KEY_YOUTH_MAJOR;
    public static final String SLOT_YOUTH_MID = NormalizationKeySupport.SUMMARY_KEY_YOUTH_MID;
    public static final String SLOT_GOV24_SERVICE_FIELD = NormalizationKeySupport.SUMMARY_KEY_GOV24_SERVICE_FIELD;
    public static final String SLOT_GOV24_USER_TYPE = NormalizationKeySupport.SUMMARY_KEY_GOV24_USER_TYPE;
    public static final String SLOT_GOV24_BENEFIT_TYPE = NormalizationKeySupport.SUMMARY_KEY_GOV24_BENEFIT_TYPE;
    public static final String SLOT_PROVISION_METHOD = "PROVISION_METHOD";

    private static final List<String> SUMMARY_LABEL_SLOTS = List.of(
            SLOT_YOUTH_MAJOR,
            SLOT_YOUTH_MID,
            SLOT_GOV24_SERVICE_FIELD,
            SLOT_GOV24_USER_TYPE,
            SLOT_GOV24_BENEFIT_TYPE
    );

    private CanonicalTaxonomySummarySlots() {
    }

    public static SummarySlots from(TaxonomySummary taxonomy) {
        LinkedHashMap<String, String> labels = new LinkedHashMap<>();
        for (String slot : SUMMARY_LABEL_SLOTS) {
            String label = TaxonomySummarySupport.summaryLabel(taxonomy, slot);
            if (label != null) {
                labels.put(slot, label);
            }
        }
        if (taxonomy != null && taxonomy.provisionMethod() != null) {
            labels.put(SLOT_PROVISION_METHOD, taxonomy.provisionMethod());
        }
        return new SummarySlots(Map.copyOf(labels));
    }

    public record SummarySlots(Map<String, String> labels) {
        public String label(String slot) {
            return labels == null ? null : labels.get(slot);
        }
    }
}
