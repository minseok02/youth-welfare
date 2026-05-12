package com.example.welfare.chat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "chat.retrieval")
public record ChatRetrievalProperties(
        int minResultCount,
        int semanticBlendLimit,
        int semanticOnlyLimit,
        int maxPreferredTermsInSearchKeyword
) {
    public ChatRetrievalProperties {
        minResultCount = positiveOrDefault(minResultCount, 3);
        semanticBlendLimit = positiveOrDefault(semanticBlendLimit, 2);
        semanticOnlyLimit = positiveOrDefault(semanticOnlyLimit, 3);
        maxPreferredTermsInSearchKeyword = positiveOrDefault(maxPreferredTermsInSearchKeyword, 3);
    }

    private static int positiveOrDefault(int value, int defaultValue) {
        return value > 0 ? value : defaultValue;
    }
}
