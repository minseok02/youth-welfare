package com.example.welfare.collect.normalization;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TaxonomySummarySupportTest {

    @Test
    @DisplayName("summary label binding은 known summary key를 SQL parameter로 옮긴다")
    void applyBoundSummaryLabelsBindsKnownKeys() {
        NormalizedPolicyAggregate.TaxonomySummary taxonomy = NormalizedPolicyAggregate.TaxonomySummary.builder()
                .summaryLabels(Map.of(
                        "YOUTH_MID", "재직자",
                        "GOV24_SERVICE_FIELD", "생활안정",
                        "GOV24_USER_TYPE", "청년",
                        "GOV24_BENEFIT_TYPE", "현금"
                ))
                .authority(NormalizedPolicyAggregate.Authority.OFFICIAL)
                .confidence(BigDecimal.ONE)
                .build();

        MapSqlParameterSource params = TaxonomySummarySupport.applyBoundSummaryLabels(new MapSqlParameterSource(), taxonomy);

        assertThat(params.getValue("youthMidLabel")).isEqualTo("재직자");
        assertThat(params.getValue("gov24ServiceFieldLabel")).isEqualTo("생활안정");
        assertThat(params.getValue("gov24UserTypeLabel")).isEqualTo("청년");
        assertThat(params.getValue("gov24BenefitTypeLabel")).isEqualTo("현금");
    }

    @Test
    @DisplayName("youth major summary는 variant label과 duplicate token을 canonical token으로 정규화한다")
    void normalizeYouthMajorSummaryCanonicalizesVariants() {
        TaxonomySummarySupport.YouthMajorSummary summary =
                TaxonomySummarySupport.normalizeYouthMajorSummary("금융･복지･문화, 금융·복지·문화");

        assertThat(summary.code()).isEqualTo("WELFARE_CULTURE");
        assertThat(summary.label()).isEqualTo("복지문화");
    }

    @Test
    @DisplayName("복수 canonical major가 남으면 youth major summary는 비운다")
    void normalizeYouthMajorSummaryRejectsMultipleCanonicalMajors() {
        TaxonomySummarySupport.YouthMajorSummary summary =
                TaxonomySummarySupport.normalizeYouthMajorSummary("주거, 교육");

        assertThat(summary.code()).isNull();
        assertThat(summary.label()).isNull();
    }
}
