package com.example.welfare.collect.service;

import com.example.welfare.policy.entity.WelfareService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 복지로 detail collect 의 source별 호출 예산 배분만 담당한다.
 */
final class BokjiroDetailBudgetAllocator {

    BudgetAllocation allocate(int maxCalls,
                              int maxCallsPerApiPerRun,
                              List<WelfareService.SourceType> sourceTypes,
                              Map<WelfareService.SourceType, List<WelfareService>> targetsBySource) {
        if (maxCalls <= 0) {
            return BudgetAllocation.empty();
        }

        Map<WelfareService.SourceType, Integer> targetCounts = new LinkedHashMap<>();
        for (WelfareService.SourceType sourceType : sourceTypes) {
            targetCounts.put(sourceType, targetsBySource.getOrDefault(sourceType, List.of()).size());
        }

        int totalTargets = targetCounts.values().stream().mapToInt(Integer::intValue).sum();
        if (totalTargets <= 0) {
            return BudgetAllocation.empty();
        }

        Map<WelfareService.SourceType, Integer> budgets = new LinkedHashMap<>();
        Map<WelfareService.SourceType, Double> remainders = new LinkedHashMap<>();
        int allocated = 0;

        for (Map.Entry<WelfareService.SourceType, Integer> entry : targetCounts.entrySet()) {
            WelfareService.SourceType sourceType = entry.getKey();
            int count = entry.getValue();
            if (count <= 0) {
                budgets.put(sourceType, 0);
                remainders.put(sourceType, 0.0);
                continue;
            }

            double share = (double) count / totalTargets;
            double exactBudget = maxCalls * share;
            int baseBudget = Math.min(maxCallsPerApiPerRun, (int) Math.floor(exactBudget));
            budgets.put(sourceType, baseBudget);
            remainders.put(sourceType, exactBudget - Math.floor(exactBudget));
            allocated += baseBudget;
        }

        int remaining = maxCalls - allocated;
        while (remaining > 0) {
            WelfareService.SourceType nextSource = selectNextBudgetSource(
                    sourceTypes,
                    maxCallsPerApiPerRun,
                    targetCounts,
                    budgets,
                    remainders
            );
            if (nextSource == null) {
                break;
            }
            budgets.computeIfPresent(nextSource, (key, value) -> value + 1);
            remaining--;
        }

        return new BudgetAllocation(budgets);
    }

    private WelfareService.SourceType selectNextBudgetSource(List<WelfareService.SourceType> sourceTypes,
                                                             int maxCallsPerApiPerRun,
                                                             Map<WelfareService.SourceType, Integer> targetCounts,
                                                             Map<WelfareService.SourceType, Integer> budgets,
                                                             Map<WelfareService.SourceType, Double> remainders) {
        WelfareService.SourceType selected = null;
        double selectedRemainder = Double.NEGATIVE_INFINITY;

        for (WelfareService.SourceType sourceType : sourceTypes) {
            int targetCount = targetCounts.getOrDefault(sourceType, 0);
            int currentBudget = budgets.getOrDefault(sourceType, 0);
            if (targetCount <= 0 || currentBudget >= maxCallsPerApiPerRun) {
                continue;
            }

            double remainder = remainders.getOrDefault(sourceType, 0.0);
            if (selected == null || remainder > selectedRemainder) {
                selected = sourceType;
                selectedRemainder = remainder;
            }
        }

        return selected;
    }

    record BudgetAllocation(Map<WelfareService.SourceType, Integer> budgets) {
        static BudgetAllocation empty() {
            return new BudgetAllocation(Map.of());
        }

        int budgetFor(WelfareService.SourceType sourceType) {
            return budgets.getOrDefault(sourceType, 0);
        }

        String toJsonObject() {
            return budgets.entrySet().stream()
                    .map(entry -> "\"%s\":%d".formatted(entry.getKey().name(), entry.getValue()))
                    .reduce((left, right) -> left + "," + right)
                    .orElse("");
        }
    }
}
