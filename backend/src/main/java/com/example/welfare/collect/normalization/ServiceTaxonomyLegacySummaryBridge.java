package com.example.welfare.collect.normalization;

import com.example.welfare.collect.support.NormalizationKeySupport;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.util.List;

public final class ServiceTaxonomyLegacySummaryBridge {

    private static final List<LegacySummaryBinding> LEGACY_SUMMARY_BINDINGS = List.of(
            LegacySummaryBinding.youthMajor(),
            LegacySummaryBinding.labelOnly(
                    CanonicalTaxonomySummarySlots.SLOT_YOUTH_MID,
                    "youthMidCode",
                    "youthMidLabel"
            ),
            LegacySummaryBinding.labelOnly(
                    CanonicalTaxonomySummarySlots.SLOT_GOV24_SERVICE_FIELD,
                    "gov24ServiceFieldCode",
                    "gov24ServiceFieldLabel"
            ),
            LegacySummaryBinding.labelOnly(
                    CanonicalTaxonomySummarySlots.SLOT_GOV24_USER_TYPE,
                    "gov24UserTypeCode",
                    "gov24UserTypeLabel"
            ),
            LegacySummaryBinding.labelOnly(
                    CanonicalTaxonomySummarySlots.SLOT_GOV24_BENEFIT_TYPE,
                    "gov24BenefitTypeCode",
                    "gov24BenefitTypeLabel"
            )
    );

    private ServiceTaxonomyLegacySummaryBridge() {
    }

    public static MapSqlParameterSource apply(MapSqlParameterSource params,
                                              CanonicalTaxonomySummarySlots.SummarySlots slots) {
        for (LegacySummaryBinding binding : LEGACY_SUMMARY_BINDINGS) {
            binding.bind(params, slots);
        }
        params.addValue("provisionMethodCode", null);
        params.addValue("provisionMethodLabel", slots == null ? null : slots.label(CanonicalTaxonomySummarySlots.SLOT_PROVISION_METHOD));
        return params;
    }

    private record LegacySummaryBinding(
            String summaryKey,
            String codeParamName,
            String labelParamName,
            SummaryValueResolver resolver
    ) {
        private static LegacySummaryBinding youthMajor() {
            return new LegacySummaryBinding(
                    CanonicalTaxonomySummarySlots.SLOT_YOUTH_MAJOR,
                    "youthMajorCode",
                    "youthMajorLabel",
                    raw -> {
                        TaxonomySummarySupport.YouthMajorSummary summary =
                                TaxonomySummarySupport.normalizeYouthMajorSummary(raw);
                        return new SummaryValue(summary.code(), summary.label());
                    }
            );
        }

        private static LegacySummaryBinding labelOnly(String summaryKey,
                                                      String codeParamName,
                                                      String labelParamName) {
            return new LegacySummaryBinding(
                    summaryKey,
                    codeParamName,
                    labelParamName,
                    raw -> new SummaryValue(null, raw)
            );
        }

        private void bind(MapSqlParameterSource params, CanonicalTaxonomySummarySlots.SummarySlots slots) {
            SummaryValue summaryValue = resolver.resolve(slots == null ? null : slots.label(summaryKey));
            params.addValue(codeParamName, summaryValue.code());
            params.addValue(labelParamName, summaryValue.label());
        }
    }

    private record SummaryValue(String code, String label) {
    }

    @FunctionalInterface
    private interface SummaryValueResolver {
        SummaryValue resolve(String rawLabel);
    }
}
