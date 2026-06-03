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
        String houseTenureCode,
        String housingTypeCode,
        String basicLivingRecipientTypeCode,
        String disabilityGradeCode,
        int displayCount,
        Double notificationMinScore,
        List<String> interestFields,
        List<String> targetTypes,
        List<PriorityPreference> priorities
) {

    public RecommendationUserSnapshot(
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
        this(
                userId,
                userKey,
                age,
                ageBand,
                sido,
                sgg,
                regionCode,
                incomeLevel,
                householdType,
                employmentStatus,
                null,
                null,
                null,
                null,
                displayCount,
                notificationMinScore,
                interestFields,
                targetTypes,
                priorities
        );
    }

    public int resolvedAge() {
        return age != null ? age : 25;
    }

    public int resolvedIncomeLevel() {
        return incomeLevel != null ? incomeLevel : 5;
    }
}
