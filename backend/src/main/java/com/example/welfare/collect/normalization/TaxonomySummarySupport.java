package com.example.welfare.collect.normalization;

import com.example.welfare.collect.normalization.NormalizedPolicyAggregate.TaxonomySummary;
import com.example.welfare.collect.support.NormalizationKeySupport;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class TaxonomySummarySupport {

    private static final List<SummaryLabelBinding> SUMMARY_LABEL_BINDINGS = List.of(
            new SummaryLabelBinding("youthMidLabel", NormalizationKeySupport.SUMMARY_KEY_YOUTH_MID),
            new SummaryLabelBinding("gov24ServiceFieldLabel", "GOV24_SERVICE_FIELD"),
            new SummaryLabelBinding("gov24UserTypeLabel", "GOV24_USER_TYPE"),
            new SummaryLabelBinding("gov24BenefitTypeLabel", "GOV24_BENEFIT_TYPE")
    );

    private TaxonomySummarySupport() {
    }

    public static String summaryLabel(TaxonomySummary taxonomy, String key) {
        if (taxonomy == null) {
            return null;
        }
        return taxonomy.summaryLabel(key);
    }

    public static MapSqlParameterSource applyBoundSummaryLabels(MapSqlParameterSource params,
                                                                TaxonomySummary taxonomy) {
        for (SummaryLabelBinding binding : SUMMARY_LABEL_BINDINGS) {
            params.addValue(binding.parameterName(), summaryLabel(taxonomy, binding.summaryKey()));
        }
        return params;
    }

    public static YouthMajorSummary normalizeYouthMajorSummary(String rawLabel) {
        if (rawLabel == null || rawLabel.isBlank()) {
            return YouthMajorSummary.empty();
        }

        Set<YouthMajorSummary> canonicalMajors = new LinkedHashSet<>();
        for (String token : rawLabel.split(",")) {
            String trimmed = token.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            YouthMajorSummary canonical = toYouthMajorSummaryToken(trimmed);
            if (canonical == null) {
                return YouthMajorSummary.empty();
            }
            canonicalMajors.add(canonical);
        }

        if (canonicalMajors.size() != 1) {
            return YouthMajorSummary.empty();
        }
        return canonicalMajors.iterator().next();
    }

    public static String toYouthMajorCode(String label) {
        YouthMajorSummary summary = toYouthMajorSummaryToken(label);
        return summary == null ? null : summary.code();
    }

    static YouthMajorSummary toYouthMajorSummaryToken(String label) {
        if (label == null) {
            return null;
        }
        String normalized = label.trim().replace('･', '·');
        return switch (normalized) {
            case "일자리" -> new YouthMajorSummary("JOB", "일자리");
            case "주거" -> new YouthMajorSummary("HOUSING", "주거");
            case "교육", "교육지원", "교육·직업훈련" -> new YouthMajorSummary("EDUCATION", "교육");
            case "복지문화", "금융·복지·문화" -> new YouthMajorSummary("WELFARE_CULTURE", "복지문화");
            case "참여권리", "참여·기반" -> new YouthMajorSummary("PARTICIPATION_RIGHTS", "참여권리");
            default -> null;
        };
    }

    private record SummaryLabelBinding(String parameterName, String summaryKey) {
    }

    public record YouthMajorSummary(String code, String label) {
        public static YouthMajorSummary empty() {
            return new YouthMajorSummary(null, null);
        }
    }
}
