package com.example.welfare.policy.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "policy.retrieval.gate")
public record PolicyRetrievalQualityGateProperties(
        double minTop1HitRate,
        double minTop3HitRate,
        double minBranchSuggestionHitRate,
        int maxEmptyResultCount
) {
    public PolicyRetrievalQualityGateProperties {
        minTop1HitRate = normalizeRate(minTop1HitRate, 0.9d);
        minTop3HitRate = normalizeRate(minTop3HitRate, 0.9d);
        minBranchSuggestionHitRate = normalizeRate(minBranchSuggestionHitRate, 1.0d);
        maxEmptyResultCount = Math.max(maxEmptyResultCount, 0);
    }

    private static double normalizeRate(double value, double defaultValue) {
        if (value < 0d || value > 1d) {
            return defaultValue;
        }
        return value;
    }
}
