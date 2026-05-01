package com.example.welfare.collect.normalization;

import com.example.welfare.collect.support.NormalizationKeySupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ServiceTaxonomyLegacySummaryBridgeTest {

    @Test
    @DisplayName("legacy summary bridge는 canonical summary slot을 service_taxonomies parameter로 매핑한다")
    void mapsCanonicalSummarySlotsToLegacyParameters() {
        NormalizedPolicyAggregate.TaxonomySummary taxonomy = NormalizedPolicyAggregate.TaxonomySummary.builder()
                .summaryLabels(Map.of(
                        NormalizationKeySupport.SUMMARY_KEY_YOUTH_MAJOR, "금융·복지·문화",
                        NormalizationKeySupport.SUMMARY_KEY_YOUTH_MID, "재직자",
                        NormalizationKeySupport.SUMMARY_KEY_GOV24_SERVICE_FIELD, "생활안정",
                        NormalizationKeySupport.SUMMARY_KEY_GOV24_USER_TYPE, "청년",
                        NormalizationKeySupport.SUMMARY_KEY_GOV24_BENEFIT_TYPE, "현금"
                ))
                .provisionMethod("온라인")
                .authority(NormalizedPolicyAggregate.Authority.OFFICIAL)
                .confidence(BigDecimal.ONE)
                .build();

        MapSqlParameterSource params = ServiceTaxonomyLegacySummaryBridge.apply(
                new MapSqlParameterSource(),
                CanonicalTaxonomySummarySlots.from(taxonomy)
        );

        assertThat(params.getValue("youthMajorCode")).isEqualTo("WELFARE_CULTURE");
        assertThat(params.getValue("youthMajorLabel")).isEqualTo("복지문화");
        assertThat(params.getValue("youthMidCode")).isNull();
        assertThat(params.getValue("youthMidLabel")).isEqualTo("재직자");
        assertThat(params.getValue("gov24ServiceFieldCode")).isNull();
        assertThat(params.getValue("gov24ServiceFieldLabel")).isEqualTo("생활안정");
        assertThat(params.getValue("gov24UserTypeCode")).isNull();
        assertThat(params.getValue("gov24UserTypeLabel")).isEqualTo("청년");
        assertThat(params.getValue("gov24BenefitTypeCode")).isNull();
        assertThat(params.getValue("gov24BenefitTypeLabel")).isEqualTo("현금");
        assertThat(params.getValue("provisionMethodCode")).isNull();
        assertThat(params.getValue("provisionMethodLabel")).isEqualTo("온라인");
    }
}
