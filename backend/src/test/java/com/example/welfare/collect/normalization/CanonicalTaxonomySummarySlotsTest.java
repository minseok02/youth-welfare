package com.example.welfare.collect.normalization;

import com.example.welfare.collect.support.NormalizationKeySupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CanonicalTaxonomySummarySlotsTest {

    @Test
    @DisplayName("canonical summary slot 추출은 known summary labels와 provision method를 함께 노출한다")
    void extractsKnownSummarySlots() {
        NormalizedPolicyAggregate.TaxonomySummary taxonomy = NormalizedPolicyAggregate.TaxonomySummary.builder()
                .summaryLabels(Map.of(
                        NormalizationKeySupport.SUMMARY_KEY_YOUTH_MAJOR, "주거",
                        NormalizationKeySupport.SUMMARY_KEY_YOUTH_MID, "전월세 및 주거급여 지원",
                        NormalizationKeySupport.SUMMARY_KEY_GOV24_SERVICE_FIELD, "생활안정"
                ))
                .provisionMethod("온라인")
                .authority(NormalizedPolicyAggregate.Authority.OFFICIAL)
                .confidence(BigDecimal.ONE)
                .build();

        CanonicalTaxonomySummarySlots.SummarySlots slots = CanonicalTaxonomySummarySlots.from(taxonomy);

        assertThat(slots.label(CanonicalTaxonomySummarySlots.SLOT_YOUTH_MAJOR)).isEqualTo("주거");
        assertThat(slots.label(CanonicalTaxonomySummarySlots.SLOT_YOUTH_MID)).isEqualTo("전월세 및 주거급여 지원");
        assertThat(slots.label(CanonicalTaxonomySummarySlots.SLOT_GOV24_SERVICE_FIELD)).isEqualTo("생활안정");
        assertThat(slots.label(CanonicalTaxonomySummarySlots.SLOT_PROVISION_METHOD)).isEqualTo("온라인");
        assertThat(slots.label(CanonicalTaxonomySummarySlots.SLOT_GOV24_USER_TYPE)).isNull();
    }
}
