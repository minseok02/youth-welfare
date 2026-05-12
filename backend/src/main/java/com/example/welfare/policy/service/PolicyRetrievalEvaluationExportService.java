package com.example.welfare.policy.service;

import com.example.welfare.policy.dto.PolicyRetrievalEvaluationCompareResponse;
import com.example.welfare.policy.dto.PolicyRetrievalEvaluationResponse;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class PolicyRetrievalEvaluationExportService {

    public String toCsv(PolicyRetrievalEvaluationResponse response) {
        StringBuilder csv = new StringBuilder();
        appendSummaryHeader(csv);
        appendSummaryRow(csv, response);
        csv.append('\n');
        appendScenarioHeader(csv);
        for (PolicyRetrievalEvaluationResponse.ScenarioResult scenario : response.scenarios()) {
            appendScenarioRow(csv, scenario);
        }
        return csv.toString();
    }

    public String toCsv(PolicyRetrievalEvaluationCompareResponse response) {
        StringBuilder csv = new StringBuilder();
        appendCompareTuningHeader(csv);
        appendCompareTuningRow(csv, "baseline", response.baselineTuning());
        appendCompareTuningRow(csv, "candidate", response.candidateTuning());
        csv.append('\n');

        appendCompareSummaryHeader(csv);
        appendCompareSummaryRow(csv, "baseline", response.baseline());
        appendCompareSummaryRow(csv, "candidate", response.candidate());
        appendCompareDeltaRow(csv, response.delta());
        csv.append('\n');

        appendCompareScenarioHeader(csv);
        Map<String, PolicyRetrievalEvaluationResponse.ScenarioResult> baselineByKey = byScenarioKey(response.baseline());
        Map<String, PolicyRetrievalEvaluationResponse.ScenarioResult> candidateByKey = byScenarioKey(response.candidate());
        for (Map.Entry<String, PolicyRetrievalEvaluationResponse.ScenarioResult> entry : baselineByKey.entrySet()) {
            PolicyRetrievalEvaluationResponse.ScenarioResult baseline = entry.getValue();
            PolicyRetrievalEvaluationResponse.ScenarioResult candidate = candidateByKey.get(entry.getKey());
            appendCompareScenarioRow(csv, baseline, candidate);
        }
        return csv.toString();
    }

    private void appendSummaryHeader(StringBuilder csv) {
        csv.append("datasetKey,scenarioCount,retrievalScenarioCount,branchScenarioCount,top1HitCount,top3HitCount,branchSuggestionHitCount,")
                .append("fallbackCount,semanticContributionCount,emptyResultCount,top1HitRate,top3HitRate,branchSuggestionHitRate,")
                .append("fallbackRate,semanticContributionRate,averageResultCount,averageFtsResultCount,averageSemanticResultCount,")
                .append("averageSemanticOnlyResultCount,averageTop3CategoryConcentration,averageBranchReductionCount\n");
    }

    private void appendSummaryRow(StringBuilder csv, PolicyRetrievalEvaluationResponse response) {
        csv.append(escape(response.datasetKey())).append(',')
                .append(response.scenarioCount()).append(',')
                .append(response.retrievalScenarioCount()).append(',')
                .append(response.branchScenarioCount()).append(',')
                .append(response.top1HitCount()).append(',')
                .append(response.top3HitCount()).append(',')
                .append(response.branchSuggestionHitCount()).append(',')
                .append(response.fallbackCount()).append(',')
                .append(response.semanticContributionCount()).append(',')
                .append(response.emptyResultCount()).append(',')
                .append(response.top1HitRate()).append(',')
                .append(response.top3HitRate()).append(',')
                .append(response.branchSuggestionHitRate()).append(',')
                .append(response.fallbackRate()).append(',')
                .append(response.semanticContributionRate()).append(',')
                .append(response.averageResultCount()).append(',')
                .append(response.averageFtsResultCount()).append(',')
                .append(response.averageSemanticResultCount()).append(',')
                .append(response.averageSemanticOnlyResultCount()).append(',')
                .append(response.averageTop3CategoryConcentration()).append(',')
                .append(response.averageBranchReductionCount()).append('\n');
    }

    private void appendScenarioHeader(StringBuilder csv) {
        csv.append("scenarioKey,question,branchKey,expectedCategory,expectedBranchKey,fallbackStrategy,top1Hit,top3Hit,")
                .append("branchSuggestionHit,semanticContribution,fallbackUsed,ftsResultCount,semanticResultCount,")
                .append("semanticOnlyResultCount,resultCount,unbranchedResultCount,branchReductionCount,")
                .append("top3CategoryConcentration,finalServiceIds,branchSuggestionKeys\n");
    }

    private void appendScenarioRow(StringBuilder csv, PolicyRetrievalEvaluationResponse.ScenarioResult scenario) {
        csv.append(escape(scenario.scenarioKey())).append(',')
                .append(escape(scenario.question())).append(',')
                .append(escape(scenario.branchKey())).append(',')
                .append(escape(scenario.expectedCategory())).append(',')
                .append(escape(scenario.expectedBranchKey())).append(',')
                .append(escape(scenario.fallbackStrategy())).append(',')
                .append(scenario.top1Hit()).append(',')
                .append(scenario.top3Hit()).append(',')
                .append(scenario.branchSuggestionHit()).append(',')
                .append(scenario.semanticContribution()).append(',')
                .append(scenario.fallbackUsed()).append(',')
                .append(scenario.ftsResultCount()).append(',')
                .append(scenario.semanticResultCount()).append(',')
                .append(scenario.semanticOnlyResultCount()).append(',')
                .append(scenario.resultCount()).append(',')
                .append(scenario.unbranchedResultCount()).append(',')
                .append(scenario.branchReductionCount()).append(',')
                .append(scenario.top3CategoryConcentration()).append(',')
                .append(escape(joinLongs(scenario.finalServiceIds()))).append(',')
                .append(escape(String.join("|", scenario.branchSuggestionKeys()))).append('\n');
    }

    private void appendCompareTuningHeader(StringBuilder csv) {
        csv.append("profile,minResultCount,semanticBlendLimit,semanticOnlyLimit,maxPreferredTermsInSearchKeyword\n");
    }

    private void appendCompareTuningRow(StringBuilder csv,
                                        String profile,
                                        PolicyRetrievalEvaluationCompareResponse.RetrievalTuning tuning) {
        csv.append(escape(profile)).append(',')
                .append(tuning.minResultCount()).append(',')
                .append(tuning.semanticBlendLimit()).append(',')
                .append(tuning.semanticOnlyLimit()).append(',')
                .append(tuning.maxPreferredTermsInSearchKeyword()).append('\n');
    }

    private void appendCompareSummaryHeader(StringBuilder csv) {
        csv.append("profile,datasetKey,top1HitCount,top3HitCount,fallbackCount,semanticContributionCount,top1HitRate,top3HitRate,")
                .append("fallbackRate,semanticContributionRate,averageResultCount,averageSemanticOnlyResultCount,averageBranchReductionCount\n");
    }

    private void appendCompareSummaryRow(StringBuilder csv,
                                         String profile,
                                         PolicyRetrievalEvaluationResponse response) {
        csv.append(escape(profile)).append(',')
                .append(escape(response.datasetKey())).append(',')
                .append(response.top1HitCount()).append(',')
                .append(response.top3HitCount()).append(',')
                .append(response.fallbackCount()).append(',')
                .append(response.semanticContributionCount()).append(',')
                .append(response.top1HitRate()).append(',')
                .append(response.top3HitRate()).append(',')
                .append(response.fallbackRate()).append(',')
                .append(response.semanticContributionRate()).append(',')
                .append(response.averageResultCount()).append(',')
                .append(response.averageSemanticOnlyResultCount()).append(',')
                .append(response.averageBranchReductionCount()).append('\n');
    }

    private void appendCompareDeltaRow(StringBuilder csv, PolicyRetrievalEvaluationCompareResponse.Delta delta) {
        csv.append(escape("delta")).append(',')
                .append(',')
                .append(delta.top1HitCountDelta()).append(',')
                .append(delta.top3HitCountDelta()).append(',')
                .append(delta.fallbackCountDelta()).append(',')
                .append(delta.semanticContributionCountDelta()).append(',')
                .append(delta.top1HitRateDelta()).append(',')
                .append(delta.top3HitRateDelta()).append(',')
                .append(delta.fallbackRateDelta()).append(',')
                .append(delta.semanticContributionRateDelta()).append(',')
                .append(delta.averageResultCountDelta()).append(',')
                .append(delta.averageSemanticOnlyResultCountDelta()).append(',')
                .append(delta.averageBranchReductionCountDelta()).append('\n');
    }

    private void appendCompareScenarioHeader(StringBuilder csv) {
        csv.append("scenarioKey,question,branchKey,expectedCategory,baselineFallbackStrategy,candidateFallbackStrategy,")
                .append("baselineTop1Hit,candidateTop1Hit,baselineTop3Hit,candidateTop3Hit,baselineFallbackUsed,candidateFallbackUsed,")
                .append("baselineSemanticContribution,candidateSemanticContribution,baselineResultCount,candidateResultCount,resultCountDelta,")
                .append("baselineSemanticOnlyResultCount,candidateSemanticOnlyResultCount,semanticOnlyResultCountDelta,")
                .append("baselineFinalServiceIds,candidateFinalServiceIds\n");
    }

    private void appendCompareScenarioRow(StringBuilder csv,
                                          PolicyRetrievalEvaluationResponse.ScenarioResult baseline,
                                          PolicyRetrievalEvaluationResponse.ScenarioResult candidate) {
        if (candidate == null) {
            candidate = baseline;
        }
        csv.append(escape(baseline.scenarioKey())).append(',')
                .append(escape(baseline.question())).append(',')
                .append(escape(baseline.branchKey())).append(',')
                .append(escape(baseline.expectedCategory())).append(',')
                .append(escape(baseline.fallbackStrategy())).append(',')
                .append(escape(candidate.fallbackStrategy())).append(',')
                .append(baseline.top1Hit()).append(',')
                .append(candidate.top1Hit()).append(',')
                .append(baseline.top3Hit()).append(',')
                .append(candidate.top3Hit()).append(',')
                .append(baseline.fallbackUsed()).append(',')
                .append(candidate.fallbackUsed()).append(',')
                .append(baseline.semanticContribution()).append(',')
                .append(candidate.semanticContribution()).append(',')
                .append(baseline.resultCount()).append(',')
                .append(candidate.resultCount()).append(',')
                .append(candidate.resultCount() - baseline.resultCount()).append(',')
                .append(baseline.semanticOnlyResultCount()).append(',')
                .append(candidate.semanticOnlyResultCount()).append(',')
                .append(candidate.semanticOnlyResultCount() - baseline.semanticOnlyResultCount()).append(',')
                .append(escape(joinLongs(baseline.finalServiceIds()))).append(',')
                .append(escape(joinLongs(candidate.finalServiceIds()))).append('\n');
    }

    private Map<String, PolicyRetrievalEvaluationResponse.ScenarioResult> byScenarioKey(PolicyRetrievalEvaluationResponse response) {
        Map<String, PolicyRetrievalEvaluationResponse.ScenarioResult> byKey = new LinkedHashMap<>();
        for (PolicyRetrievalEvaluationResponse.ScenarioResult scenario : response.scenarios()) {
            byKey.put(scenario.scenarioKey(), scenario);
        }
        return byKey;
    }

    private String joinLongs(java.util.List<Long> values) {
        return values.stream()
                .map(String::valueOf)
                .collect(java.util.stream.Collectors.joining("|"));
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        String escaped = value.replace("\"", "\"\"");
        return "\"" + escaped + "\"";
    }
}
