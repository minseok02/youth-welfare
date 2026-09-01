package com.example.welfare.recommend.support;

public final class RecommendationAiReasonSanitizer {

    public static final int MAX_REASON_CODE_POINTS = 20;
    private static final String[] LOW_CONFIDENCE_REASON_TOKENS = {
            "관련성 낮", "적합도 낮", "낮은 적합", "미흡", "맞지", "불일치", "제한적", "대상 아님", "조건 아님"
    };

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
        if (looksLikeLowConfidenceReason(normalized)) {
            return null;
        }
        if (normalized.codePointCount(0, normalized.length()) <= MAX_REASON_CODE_POINTS) {
            return normalized;
        }
        int endIndex = normalized.offsetByCodePoints(0, MAX_REASON_CODE_POINTS);
        return normalized.substring(0, endIndex).trim();
    }

    private static boolean looksLikeLowConfidenceReason(String reason) {
        for (String token : LOW_CONFIDENCE_REASON_TOKENS) {
            if (reason.contains(token)) {
                return true;
            }
        }
        return false;
    }
}
