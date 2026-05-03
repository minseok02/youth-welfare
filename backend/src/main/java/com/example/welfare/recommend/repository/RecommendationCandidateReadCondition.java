package com.example.welfare.recommend.repository;

public record RecommendationCandidateReadCondition(
        int age,
        int incomeLevel,
        String sido,
        String regionCode,
        int baseFetchSize,
        int latestFetchSize
) {
}
