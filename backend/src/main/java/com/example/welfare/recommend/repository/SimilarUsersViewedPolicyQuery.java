package com.example.welfare.recommend.repository;

import java.time.LocalDateTime;
import java.util.List;

public record SimilarUsersViewedPolicyQuery(
        String userKey,
        Integer age,
        String ageBand,
        String sido,
        String regionCode,
        Integer incomeLevel,
        List<String> interestFields,
        List<String> targetTypes,
        List<String> priorityCodes,
        LocalDateTime viewedSince,
        int minSimilarUsers,
        double minSimilarityScore,
        int limit
) {
}
