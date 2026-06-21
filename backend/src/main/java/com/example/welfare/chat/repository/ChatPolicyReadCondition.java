package com.example.welfare.chat.repository;

import java.util.List;

public record ChatPolicyReadCondition(
        String keyword,
        int limit,
        String branchKey,
        String preferredCategory,
        List<String> preferredTerms,
        String regionCode,
        String sido,
        String sgg
) {

    public ChatPolicyReadCondition {
        branchKey = normalize(branchKey);
        preferredCategory = normalize(preferredCategory);
        preferredTerms = preferredTerms == null ? List.of() : List.copyOf(preferredTerms);
        regionCode = normalize(regionCode);
        sido = normalize(sido);
        sgg = normalize(sgg);
    }

    public ChatPolicyReadCondition(String keyword, int limit) {
        this(keyword, limit, null, null, List.of());
    }

    public ChatPolicyReadCondition(String keyword,
                                   int limit,
                                   String branchKey,
                                   String preferredCategory,
                                   List<String> preferredTerms) {
        this(keyword, limit, branchKey, preferredCategory, preferredTerms, null, null, null);
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
