package com.example.welfare.recommend.repository;

public record RecommendationCandidateReadCondition(
        int age,
        int incomeLevel,
        String sido,
        String sgg,
        String regionCode,
        int baseFetchSize,
        int latestFetchSize
) {
}
