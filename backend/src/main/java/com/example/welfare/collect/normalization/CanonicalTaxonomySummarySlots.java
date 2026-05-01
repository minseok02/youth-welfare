package com.example.welfare.collect.normalization;

import com.example.welfare.collect.normalization.NormalizedPolicyAggregate.TaxonomySummary;
import com.example.welfare.collect.support.NormalizationKeySupport;

import java.util.ArrayList;
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
    private static final List<String> MANAGED_SLOTS = List.of(
            SLOT_YOUTH_MAJOR,
            SLOT_YOUTH_MID,
            SLOT_GOV24_SERVICE_FIELD,
            SLOT_GOV24_USER_TYPE,
            SLOT_GOV24_BENEFIT_TYPE,
            SLOT_PROVISION_METHOD
    );

    private CanonicalTaxonomySummarySlots() {
    }

    public static SummarySlots from(TaxonomySummary taxonomy) {
        LinkedHashMap<String, SummarySlot> slots = new LinkedHashMap<>();
        for (String slot : SUMMARY_LABEL_SLOTS) {
            String label = TaxonomySummarySupport.summaryLabel(taxonomy, slot);
            if (label != null) {
                SummarySlot summarySlot = summarySlot(slot, label);
                if (summarySlot.slotLabel() != null) {
                    slots.put(slot, summarySlot);
                }
            }
        }
        if (taxonomy != null && taxonomy.provisionMethod() != null) {
            slots.put(SLOT_PROVISION_METHOD, summarySlot(SLOT_PROVISION_METHOD, taxonomy.provisionMethod()));
        }
        return new SummarySlots(Map.copyOf(slots));
    }

    public static List<String> managedSlotKeys() {
        return MANAGED_SLOTS;
    }

    private static SummarySlot summarySlot(String slotKey, String label) {
        return switch (slotKey) {
            case SLOT_YOUTH_MAJOR -> {
                TaxonomySummarySupport.YouthMajorSummary summary =
                        TaxonomySummarySupport.normalizeYouthMajorSummary(label);
                yield new SummarySlot(slotKey, slotKey, normalizeBlankCode(summary.code()), summary.label());
            }
            case SLOT_PROVISION_METHOD -> new SummarySlot(slotKey, null, "", label);
            default -> new SummarySlot(slotKey, slotKey, "", label);
        };
    }

    private static String normalizeBlankCode(String code) {
        return code == null ? "" : code;
    }

    public record SummarySlots(Map<String, SummarySlot> slots) {
        public String label(String slot) {
            SummarySlot summarySlot = slot(slot);
            return summarySlot == null ? null : summarySlot.slotLabel();
        }

        public SummarySlot slot(String slot) {
            return slots == null ? null : slots.get(slot);
        }

        public List<SummarySlot> presentSlots() {
            return slots == null ? List.of() : new ArrayList<>(slots.values());
        }
    }

    public record SummarySlot(
            String slotKey,
            String codeSetKey,
            String slotCode,
            String slotLabel
    ) {
    }
}
