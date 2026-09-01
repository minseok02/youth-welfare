package com.example.welfare.recommend.support;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RecommendationAiReasonSanitizerTest {

    @Test
    void sanitizeReturnsNullForBlankReason() {
        assertThat(RecommendationAiReasonSanitizer.sanitize(null)).isNull();
        assertThat(RecommendationAiReasonSanitizer.sanitize("   \n\t  ")).isNull();
    }

    @Test
    void sanitizeTrimsAndCollapsesWhitespace() {
        assertThat(RecommendationAiReasonSanitizer.sanitize("  지역\n청년\t주거  조건  "))
                .isEqualTo("지역 청년 주거 조건");
    }

    @Test
    void sanitizeReturnsNullForLowConfidenceReason() {
        assertThat(RecommendationAiReasonSanitizer.sanitize("농업 관련성 낮음")).isNull();
        assertThat(RecommendationAiReasonSanitizer.sanitize("사용자 조건과 맞지 않음")).isNull();
    }

    @Test
    void sanitizeKeepsPositiveLowIncomeReason() {
        assertThat(RecommendationAiReasonSanitizer.sanitize("소득 낮음으로 주거 지원 필요"))
                .isEqualTo("소득 낮음으로 주거 지원 필요");
    }

    @Test
    void sanitizeLimitsReasonToTwentyCodePoints() {
        String reason = "청년 주거 지원 조건과 지역 조건이 잘 맞아요";

        String sanitized = RecommendationAiReasonSanitizer.sanitize(reason);

        assertThat(sanitized).isEqualTo("청년 주거 지원 조건과 지역 조건이");
        assertThat(sanitized.codePointCount(0, sanitized.length()))
                .isLessThanOrEqualTo(RecommendationAiReasonSanitizer.MAX_REASON_CODE_POINTS);
    }
}
