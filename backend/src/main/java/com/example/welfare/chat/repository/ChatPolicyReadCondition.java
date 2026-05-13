package com.example.welfare.chat.repository;

import java.util.List;

public record ChatPolicyReadCondition(
        String keyword,
        int limit,
        String branchKey,
        String preferredCategory,
        List<String> preferredTerms
) {

    public ChatPolicyReadCondition {
        branchKey = normalize(branchKey);
        preferredCategory = normalize(preferredCategory);
        preferredTerms = preferredTerms == null ? List.of() : List.copyOf(preferredTerms);
    }

    public ChatPolicyReadCondition(String keyword, int limit) {
        this(keyword, limit, null, null, List.of());
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
