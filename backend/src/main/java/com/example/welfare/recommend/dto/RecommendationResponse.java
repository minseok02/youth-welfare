package com.example.welfare.recommend.dto;

import com.example.welfare.recommend.entity.UserRecommendation;
import com.example.welfare.recommend.support.RecommendationAiReasonSanitizer;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Builder
public class RecommendationResponse {

    private Long id;
    private Long serviceId;
    private Long logId;             // CTR 클릭 추적용 POST 본문 값
    private String title;
    private String description;
    private String unifiedCategory;
    private String sourceType;
    private String youthMajorLabel;
    private String youthMidLabel;
    private String provisionMethodLabel;
    private String gov24ServiceFieldLabel;
    private String gov24UserTypeLabel;
    private String gov24BenefitTypeLabel;
    private String status;
    private String hostOrg;
    private String operatingOrg;
    private String sido;
    private LocalDate applyEndDate;
    private BigDecimal finalScore;
    private BigDecimal aiScore;     // null 가능
    private String aiStatus;        // null 아님
    private String aiReason;        // null 가능
    private List<ReasonFactor> reasonFactors;
    private boolean isBookmarked;
    private LocalDateTime recommendedAt;

    @Getter
    @Builder
    public static class ReasonFactor {
        private String key;
        private String label;
        private String value;
    }

    public static RecommendationResponse from(UserRecommendation rec) {
        return from(rec, null);
    }

    public static RecommendationResponse from(UserRecommendation rec, Long logId) {
        return from(rec, logId, null, null);
    }

    public static RecommendationResponse from(UserRecommendation rec,
                                              Long logId,
                                              RecommendationCandidateProjection projection) {
        return from(rec, logId, projection, null);
    }

    public static RecommendationResponse from(UserRecommendation rec,
                                              Long logId,
                                              RecommendationCandidateProjection projection,
                                              String sido) {
        return RecommendationResponse.builder()
                .id(rec.getId())
                .serviceId(rec.getService().getId())
                .logId(logId)
                .title(rec.getService().getTitle())
                .description(resolveDescription(rec, projection))
                .unifiedCategory(resolveUnifiedCategory(rec, projection))
                .sourceType(rec.getService().getSourceType().name())
                .youthMajorLabel(resolveYouthMajorLabel(projection))
                .youthMidLabel(resolveYouthMidLabel(projection))
                .provisionMethodLabel(resolveProvisionMethodLabel(projection))
                .gov24ServiceFieldLabel(resolveGov24ServiceFieldLabel(projection))
                .gov24UserTypeLabel(resolveGov24UserTypeLabel(projection))
                .gov24BenefitTypeLabel(resolveGov24BenefitTypeLabel(projection))
                .status(rec.getService().getStatus().name())
                .hostOrg(rec.getService().getHostOrg())
                .operatingOrg(rec.getService().getOperatingOrg())
                .sido(sido)
                .applyEndDate(rec.getService().getApplyEndDate())
                .finalScore(rec.getFinalScore())
                .aiScore(rec.getAiScore())
                .aiStatus(rec.getAiStatus().name())
                .aiReason(RecommendationAiReasonSanitizer.sanitize(rec.getAiReason()))
                .reasonFactors(resolveReasonFactors(rec, projection, sido))
                .isBookmarked(rec.isBookmarked())
                .recommendedAt(rec.getRecommendedAt())
                .build();
    }

    private static String resolveDescription(UserRecommendation rec,
                                             RecommendationCandidateProjection projection) {
        if (projection != null && projection.summary() != null && !projection.summary().isBlank()) {
            return projection.summary();
        }
        if (rec.getService().getSupportContent() != null && !rec.getService().getSupportContent().isBlank()) {
            return rec.getService().getSupportContent();
        }
        return rec.getService().getDescription();
    }

    private static String resolveUnifiedCategory(UserRecommendation rec,
                                                 RecommendationCandidateProjection projection) {
        if (projection != null && projection.unifiedCategoryCompat() != null) {
            return projection.unifiedCategoryCompat();
        }
        return rec.getService().getUnifiedCategory();
    }

    private static String resolveYouthMidLabel(RecommendationCandidateProjection projection) {
        return projection != null ? projection.youthMidLabel() : null;
    }

    private static String resolveYouthMajorLabel(RecommendationCandidateProjection projection) {
        return projection != null ? projection.youthMajorLabel() : null;
    }

    private static String resolveProvisionMethodLabel(RecommendationCandidateProjection projection) {
        return projection != null ? projection.provisionMethodLabel() : null;
    }

    private static String resolveGov24ServiceFieldLabel(RecommendationCandidateProjection projection) {
        return projection != null ? projection.gov24ServiceFieldLabel() : null;
    }

    private static String resolveGov24UserTypeLabel(RecommendationCandidateProjection projection) {
        return projection != null ? projection.gov24UserTypeLabel() : null;
    }

    private static String resolveGov24BenefitTypeLabel(RecommendationCandidateProjection projection) {
        return projection != null ? projection.gov24BenefitTypeLabel() : null;
    }

    private static List<ReasonFactor> resolveReasonFactors(UserRecommendation rec,
                                                           RecommendationCandidateProjection projection,
                                                           String sido) {
        List<ReasonFactor> factors = new ArrayList<>();
        if (rec.getAiStatus() != null && rec.getAiStatus().name().equals("NOT_REQUESTED")) {
            addFactor(factors, "evaluation", "평가", "규칙 기반");
        }
        addFactor(factors, "fit", "적합도", resolveFitLabel(rec.getFinalScore()));
        addFactor(factors, "region", "지역", sido);
        addFactor(factors, "age", "연령", resolveAgeRange(rec.getService().getMinAge(), rec.getService().getMaxAge()));
        addFactor(factors, "income", "소득", resolveIncomeRange(rec.getService().getMinIncome(), rec.getService().getMaxIncome()));
        addFactor(factors, "category", "분류", resolveUnifiedCategory(rec, projection));
        addFactor(factors, "detail", "세부", firstText(
                resolveYouthMidLabel(projection),
                resolveGov24ServiceFieldLabel(projection)
        ));
        addFactor(factors, "support", "지원", firstText(
                resolveProvisionMethodLabel(projection),
                resolveGov24BenefitTypeLabel(projection)
        ));
        addFactor(factors, "target", "대상", resolveGov24UserTypeLabel(projection));
        addFactor(factors, "source", "출처", rec.getService().getSourceType().name());
        return factors;
    }

    private static String resolveFitLabel(BigDecimal finalScore) {
        if (finalScore == null || finalScore.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        if (finalScore.compareTo(new BigDecimal("0.70")) >= 0) {
            return "높음";
        }
        if (finalScore.compareTo(new BigDecimal("0.40")) >= 0) {
            return "보통";
        }
        return "낮음";
    }

    private static String resolveAgeRange(Integer minAge, Integer maxAge) {
        if (minAge == null && maxAge == null) {
            return null;
        }
        if (minAge != null && maxAge != null) {
            return minAge + "-" + maxAge + "세";
        }
        if (minAge != null) {
            return minAge + "세 이상";
        }
        return maxAge + "세 이하";
    }

    private static String resolveIncomeRange(Integer minIncome, Integer maxIncome) {
        Integer normalizedMinIncome = normalizeIncomeLevel(minIncome);
        Integer normalizedMaxIncome = normalizeIncomeLevel(maxIncome);
        if (normalizedMinIncome != null && normalizedMaxIncome != null
                && normalizedMinIncome > normalizedMaxIncome) {
            Integer temp = normalizedMinIncome;
            normalizedMinIncome = normalizedMaxIncome;
            normalizedMaxIncome = temp;
        }
        if (normalizedMinIncome == null && normalizedMaxIncome == null) {
            return null;
        }
        if (normalizedMinIncome != null && normalizedMaxIncome != null) {
            if (normalizedMinIncome.equals(normalizedMaxIncome)) {
                return normalizedMinIncome + "분위";
            }
            return normalizedMinIncome + "-" + normalizedMaxIncome + "분위";
        }
        if (normalizedMinIncome != null) {
            return normalizedMinIncome + "분위 이상";
        }
        return normalizedMaxIncome + "분위 이하";
    }

    private static Integer normalizeIncomeLevel(Integer incomeLevel) {
        if (incomeLevel == null || incomeLevel <= 0) {
            return null;
        }
        return incomeLevel;
    }

    private static void addFactor(List<ReasonFactor> factors, String key, String label, String value) {
        String normalizedValue = normalizeBlank(value);
        if (normalizedValue == null) {
            return;
        }
        boolean duplicate = factors.stream()
                .anyMatch(factor -> factor.getKey().equals(key)
                        || (factor.getLabel().equals(label) && factor.getValue().equals(normalizedValue)));
        if (duplicate) {
            return;
        }
        factors.add(ReasonFactor.builder()
                .key(key)
                .label(label)
                .value(normalizedValue)
                .build());
    }

    private static String firstText(String... values) {
        for (String value : values) {
            String normalized = normalizeBlank(value);
            if (normalized != null) {
                return normalized;
            }
        }
        return null;
    }

    private static String normalizeBlank(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
