package com.example.welfare.recommend.dto;

import java.util.List;

public record RecommendationUserSnapshot(
        Long userId,
        String userKey,
        Integer age,
        String ageBand,
        String sido,
        String sgg,
        String regionCode,
        Byte incomeLevel,
        String householdType,
        String employmentStatus,
        int displayCount,
        Double notificationMinScore,
        List<String> interestFields,
        List<String> targetTypes,
        List<PriorityPreference> priorities
) {

    public int resolvedAge() {
        return age != null ? age : 25;
    }

    public int resolvedIncomeLevel() {
        return incomeLevel != null ? incomeLevel : 5;
    }
}
