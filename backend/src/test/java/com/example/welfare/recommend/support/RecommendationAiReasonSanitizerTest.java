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
    void sanitizeLimitsReasonToTwentyCodePoints() {
        String reason = "청년 주거 지원 조건과 지역 조건이 잘 맞아요";

        String sanitized = RecommendationAiReasonSanitizer.sanitize(reason);

        assertThat(sanitized).isEqualTo("청년 주거 지원 조건과 지역 조건이");
        assertThat(sanitized.codePointCount(0, sanitized.length()))
                .isLessThanOrEqualTo(RecommendationAiReasonSanitizer.MAX_REASON_CODE_POINTS);
    }
}
