package com.example.welfare.recommend.support;

public final class RecommendationAiReasonSanitizer {

    public static final int MAX_REASON_CODE_POINTS = 20;

    private RecommendationAiReasonSanitizer() {
    }

    public static String sanitize(String reason) {
        if (reason == null) {
            return null;
        }
        String normalized = reason.replaceAll("\\s+", " ").trim();
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.codePointCount(0, normalized.length()) <= MAX_REASON_CODE_POINTS) {
            return normalized;
        }
        int endIndex = normalized.offsetByCodePoints(0, MAX_REASON_CODE_POINTS);
        return normalized.substring(0, endIndex).trim();
    }
}
