package com.example.welfare.admin.dashboard.dto;

import java.time.LocalDateTime;

public record AdminStandardCodeEffectObservationResponse(
        boolean available,
        LocalDateTime generatedAt,
        String sourceSummaryPath,
        String precheckStatus,
        String decisionClass,
        String housingEffectStatus,
        int housingPositiveRuleDeltaRows,
        int housingPositiveFinalDeltaRows,
        double housingMaxRuleDelta,
        double housingMaxFinalDelta,
        String housingTopPositiveRuleDeltaRows,
        String welfareMatrixStatus,
        int welfareScenarioCount,
        int welfarePositiveRuleScenarios,
        int welfarePositiveFinalScenarios,
        String welfareMaxRuleDeltaScenario,
        double welfareMaxRuleDelta,
        String welfareMaxFinalDeltaScenario,
        double welfareMaxFinalDelta,
        String welfareScenarioRuleDeltaSnapshot
) {
}
