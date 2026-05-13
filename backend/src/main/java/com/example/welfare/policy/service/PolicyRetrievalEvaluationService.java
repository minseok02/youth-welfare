package com.example.welfare.policy.service;

import com.example.welfare.chat.config.ChatRetrievalProperties;
import com.example.welfare.chat.dto.ChatPolicyCandidate;
import com.example.welfare.chat.dto.response.ChatBranchOptionResponse;
import com.example.welfare.chat.service.ChatBranchCatalog;
import com.example.welfare.chat.service.ChatPolicyService;
import com.example.welfare.chat.service.ChatRetrievalSnapshotService;
import com.example.welfare.policy.dto.PolicyRetrievalEvaluationCompareRequest;
import com.example.welfare.policy.dto.PolicyRetrievalEvaluationCompareResponse;
import com.example.welfare.policy.dto.PolicyRetrievalEvaluationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PolicyRetrievalEvaluationService {

    private static final String DATASET_KEY = "retrieval-baseline-v2";
    private static final int EVALUATION_LIMIT = 5;
    private static final List<EvaluationScenario> SCENARIOS = List.of(
            new EvaluationScenario("branch-housing", "주거 지원", null, null, "housing-stability", true),
            new EvaluationScenario("branch-job", "취업 지원", null, null, "job-employment", true),
            new EvaluationScenario("housing-cash", "월세 지원 받을 수 있나요?", "housing-cash", "주거", null, false),
            new EvaluationScenario("housing-stability", "전세임대나 공공임대 정보를 보고 싶어요", "housing-stability", "주거", null, false),
            new EvaluationScenario("housing-subscription", "청약이나 입주 모집 공고를 보고 싶어요", "housing-subscription", "주거", null, false),
            new EvaluationScenario("job-employment", "청년 인턴이나 채용 공고가 궁금해요", "job-employment", "일자리", null, false),
            new EvaluationScenario("job-training", "직업훈련이나 취업 교육을 찾고 있어요", "job-training", "교육·직업훈련", null, false),
            new EvaluationScenario("job-startup", "청년 창업 자금 지원이 있나요?", "job-startup", "일자리", null, false),
            new EvaluationScenario("finance-support", "청년 생활비나 금융 지원이 있나요?", null, "금융·생활지원", null, false),
            new EvaluationScenario("culture-support", "청년 문화 활동비 지원 있나요?", null, "문화·여가", null, false),
            new EvaluationScenario("health-support", "청년 정신건강 상담이나 의료비 지원이 있나요?", null, "건강·의료", null, false)
    );

    private final ChatPolicyService chatPolicyService;
    private final ChatBranchCatalog chatBranchCatalog;
    private final ChatRetrievalSnapshotService chatRetrievalSnapshotService;
    private final ChatRetrievalProperties chatRetrievalProperties;

    public PolicyRetrievalEvaluationResponse evaluateBaseline() {
        return evaluate(DATASET_KEY, chatRetrievalProperties);
    }

    public PolicyRetrievalEvaluationCompareResponse compareBaseline(PolicyRetrievalEvaluationCompareRequest request) {
        ChatRetrievalProperties baselineTuning = chatRetrievalProperties;
        ChatRetrievalProperties candidateTuning = (request == null)
                ? baselineTuning
                : request.mergeWith(baselineTuning);

        PolicyRetrievalEvaluationResponse baseline = evaluate(DATASET_KEY, baselineTuning);
        PolicyRetrievalEvaluationResponse candidate = evaluate(candidateDatasetKey(candidateTuning), candidateTuning);

        return new PolicyRetrievalEvaluationCompareResponse(
                toTuningDto(baselineTuning),
                toTuningDto(candidateTuning),
                baseline,
                candidate,
                new PolicyRetrievalEvaluationCompareResponse.Delta(
                        candidate.top1HitCount() - baseline.top1HitCount(),
                        candidate.top3HitCount() - baseline.top3HitCount(),
                        candidate.fallbackCount() - baseline.fallbackCount(),
                        candidate.semanticContributionCount() - baseline.semanticContributionCount(),
                        candidate.top1HitRate() - baseline.top1HitRate(),
                        candidate.top3HitRate() - baseline.top3HitRate(),
                        candidate.fallbackRate() - baseline.fallbackRate(),
                        candidate.semanticContributionRate() - baseline.semanticContributionRate(),
                        candidate.averageResultCount() - baseline.averageResultCount(),
                        candidate.averageSemanticOnlyResultCount() - baseline.averageSemanticOnlyResultCount(),
                        candidate.averageBranchReductionCount() - baseline.averageBranchReductionCount()
                )
        );
    }

    private PolicyRetrievalEvaluationResponse evaluate(String datasetKey, ChatRetrievalProperties tuning) {
        List<PolicyRetrievalEvaluationResponse.ScenarioResult> scenarioResults = SCENARIOS.stream()
                .map(scenario -> evaluateScenario(datasetKey, scenario, tuning))
                .toList();

        long retrievalScenarioCount = scenarioResults.stream()
                .filter(result -> result.expectedCategory() != null)
                .count();
        long branchScenarioCount = scenarioResults.size() - retrievalScenarioCount;
        int top1HitCount = (int) scenarioResults.stream().filter(PolicyRetrievalEvaluationResponse.ScenarioResult::top1Hit).count();
        int top3HitCount = (int) scenarioResults.stream().filter(PolicyRetrievalEvaluationResponse.ScenarioResult::top3Hit).count();
        int branchSuggestionHitCount = (int) scenarioResults.stream()
                .filter(PolicyRetrievalEvaluationResponse.ScenarioResult::branchSuggestionHit)
                .count();
        int fallbackCount = (int) scenarioResults.stream()
                .filter(result -> result.expectedCategory() != null)
                .filter(result -> result.fallbackStrategy() != null && !"MERGED_RESULTS".equals(result.fallbackStrategy()))
                .count();
        int semanticContributionCount = (int) scenarioResults.stream()
                .filter(PolicyRetrievalEvaluationResponse.ScenarioResult::semanticContribution)
                .count();
        int emptyResultCount = (int) scenarioResults.stream()
                .filter(result -> result.expectedCategory() != null && result.resultCount() == 0)
                .count();
        double averageResultCount = averageForRetrievalScenarios(
                scenarioResults,
                PolicyRetrievalEvaluationResponse.ScenarioResult::resultCount
        );
        double averageFtsResultCount = averageForRetrievalScenarios(
                scenarioResults,
                PolicyRetrievalEvaluationResponse.ScenarioResult::ftsResultCount
        );
        double averageSemanticResultCount = averageForRetrievalScenarios(
                scenarioResults,
                PolicyRetrievalEvaluationResponse.ScenarioResult::semanticResultCount
        );
        double averageSemanticOnlyResultCount = averageForRetrievalScenarios(
                scenarioResults,
                PolicyRetrievalEvaluationResponse.ScenarioResult::semanticOnlyResultCount
        );
        double averageTop3CategoryConcentration = averageForRetrievalScenarios(
                scenarioResults,
                PolicyRetrievalEvaluationResponse.ScenarioResult::top3CategoryConcentration
        );
        double averageBranchReductionCount = scenarioResults.stream()
                .filter(result -> result.branchKey() != null)
                .mapToInt(PolicyRetrievalEvaluationResponse.ScenarioResult::branchReductionCount)
                .average()
                .orElse(0);
        double top1HitRate = rate(top1HitCount, retrievalScenarioCount);
        double top3HitRate = rate(top3HitCount, retrievalScenarioCount);
        double branchSuggestionHitRate = rate(branchSuggestionHitCount, branchScenarioCount);
        double fallbackRate = rate(fallbackCount, retrievalScenarioCount);
        double semanticContributionRate = rate(semanticContributionCount, retrievalScenarioCount);

        return new PolicyRetrievalEvaluationResponse(
                datasetKey,
                scenarioResults.size(),
                (int) retrievalScenarioCount,
                (int) branchScenarioCount,
                top1HitCount,
                top3HitCount,
                branchSuggestionHitCount,
                fallbackCount,
                semanticContributionCount,
                emptyResultCount,
                top1HitRate,
                top3HitRate,
                branchSuggestionHitRate,
                fallbackRate,
                semanticContributionRate,
                averageResultCount,
                averageFtsResultCount,
                averageSemanticResultCount,
                averageSemanticOnlyResultCount,
                averageTop3CategoryConcentration,
                averageBranchReductionCount,
                scenarioResults
        );
    }

    private PolicyRetrievalEvaluationResponse.ScenarioResult evaluateScenario(String datasetKey,
                                                                             EvaluationScenario scenario,
                                                                             ChatRetrievalProperties tuning) {
        if (scenario.branchScenario()) {
            List<ChatBranchOptionResponse> suggestions = chatBranchCatalog.toResponses(
                    chatBranchCatalog.suggestBranches(scenario.question())
            );
            chatRetrievalSnapshotService.recordEvaluationTrace(
                    scenarioSnapshotKey(datasetKey, scenario.scenarioKey()),
                    scenario.question(),
                    suggestions,
                    emptyTrace()
            );
            return new PolicyRetrievalEvaluationResponse.ScenarioResult(
                    scenario.scenarioKey(),
                    scenario.question(),
                    null,
                    null,
                    scenario.expectedBranchKey(),
                    null,
                    false,
                    false,
                    suggestions.stream().map(ChatBranchOptionResponse::getBranchKey).anyMatch(scenario.expectedBranchKey()::equals),
                    false,
                    false,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    List.of(),
                    suggestions.stream().map(ChatBranchOptionResponse::getBranchKey).toList()
            );
        }

        ChatPolicyService.CandidateTrace trace =
                chatPolicyService.traceCandidates(scenario.question(), scenario.branchKey(), EVALUATION_LIMIT, tuning);
        chatRetrievalSnapshotService.recordEvaluationTrace(
                scenarioSnapshotKey(datasetKey, scenario.scenarioKey()),
                scenario.question(),
                List.of(),
                trace
        );

        List<ChatPolicyCandidate> finalCandidates = trace.finalCandidates();
        boolean top1Hit = matchesExpectedCategory(finalCandidates.stream().findFirst().orElse(null), scenario.expectedCategory());
        boolean top3Hit = finalCandidates.stream()
                .limit(3)
                .anyMatch(candidate -> matchesExpectedCategory(candidate, scenario.expectedCategory()));

        Map<Long, ChatPolicyCandidate> ftsById = toMap(trace.ftsCandidates());
        int semanticOnlyResultCount = (int) trace.semanticCandidates().stream()
                .filter(candidate -> !ftsById.containsKey(candidate.getServiceId()))
                .filter(candidate -> finalCandidates.stream().map(ChatPolicyCandidate::getServiceId).anyMatch(candidate.getServiceId()::equals))
                .count();
        boolean semanticContribution = semanticOnlyResultCount > 0;

        int unbranchedResultCount = finalCandidates.size();
        int branchReductionCount = 0;
        if (scenario.branchKey() != null) {
            ChatPolicyService.CandidateTrace unbranchedTrace =
                    chatPolicyService.traceCandidates(scenario.question(), null, EVALUATION_LIMIT, tuning);
            unbranchedResultCount = unbranchedTrace.finalCandidates().size();
            branchReductionCount = Math.max(0, unbranchedResultCount - finalCandidates.size());
        }
        boolean fallbackUsed = trace.fallbackStrategy() != null && !"MERGED_RESULTS".equals(trace.fallbackStrategy());
        double top3CategoryConcentration = top3CategoryConcentration(finalCandidates);

        return new PolicyRetrievalEvaluationResponse.ScenarioResult(
                scenario.scenarioKey(),
                scenario.question(),
                scenario.branchKey(),
                scenario.expectedCategory(),
                null,
                trace.fallbackStrategy(),
                top1Hit,
                top3Hit,
                false,
                semanticContribution,
                fallbackUsed,
                trace.ftsCandidates().size(),
                trace.semanticCandidates().size(),
                semanticOnlyResultCount,
                finalCandidates.size(),
                unbranchedResultCount,
                branchReductionCount,
                top3CategoryConcentration,
                finalCandidates.stream().map(ChatPolicyCandidate::getServiceId).toList(),
                List.of()
        );
    }

    private boolean matchesExpectedCategory(ChatPolicyCandidate candidate, String expectedCategory) {
        return candidate != null
                && expectedCategory != null
                && expectedCategory.equals(candidate.getUnifiedCategory());
    }

    private double top3CategoryConcentration(List<ChatPolicyCandidate> candidates) {
        List<ChatPolicyCandidate> top3 = candidates.stream().limit(3).toList();
        if (top3.isEmpty()) {
            return 0;
        }

        Map<String, Integer> counts = new LinkedHashMap<>();
        for (ChatPolicyCandidate candidate : top3) {
            String category = candidate.getUnifiedCategory() != null ? candidate.getUnifiedCategory() : "UNKNOWN";
            counts.merge(category, 1, Integer::sum);
        }
        int dominant = counts.values().stream().max(Comparator.naturalOrder()).orElse(0);
        return (double) dominant / top3.size();
    }

    private double averageForRetrievalScenarios(
            List<PolicyRetrievalEvaluationResponse.ScenarioResult> scenarioResults,
            java.util.function.ToDoubleFunction<PolicyRetrievalEvaluationResponse.ScenarioResult> mapper
    ) {
        return scenarioResults.stream()
                .filter(result -> result.expectedCategory() != null)
                .mapToDouble(mapper)
                .average()
                .orElse(0);
    }

    private double rate(long numerator, long denominator) {
        if (denominator <= 0) {
            return 0;
        }
        return (double) numerator / denominator;
    }

    private Map<Long, ChatPolicyCandidate> toMap(List<ChatPolicyCandidate> candidates) {
        Map<Long, ChatPolicyCandidate> byId = new LinkedHashMap<>();
        for (ChatPolicyCandidate candidate : candidates) {
            byId.put(candidate.getServiceId(), candidate);
        }
        return byId;
    }

    private PolicyRetrievalEvaluationCompareResponse.RetrievalTuning toTuningDto(ChatRetrievalProperties tuning) {
        return new PolicyRetrievalEvaluationCompareResponse.RetrievalTuning(
                tuning.minResultCount(),
                tuning.semanticBlendLimit(),
                tuning.semanticOnlyLimit(),
                tuning.maxPreferredTermsInSearchKeyword()
        );
    }

    private String candidateDatasetKey(ChatRetrievalProperties tuning) {
        return "retrieval-compare-v2"
                + "-min" + tuning.minResultCount()
                + "-blend" + tuning.semanticBlendLimit()
                + "-sem" + tuning.semanticOnlyLimit()
                + "-terms" + tuning.maxPreferredTermsInSearchKeyword();
    }

    private String scenarioSnapshotKey(String datasetKey, String scenarioKey) {
        return datasetKey + ":" + scenarioKey;
    }

    private ChatPolicyService.CandidateTrace emptyTrace() {
        return new ChatPolicyService.CandidateTrace(
                null,
                null,
                null,
                null,
                List.of(),
                null,
                List.of(),
                List.of(),
                List.of()
        );
    }

    private record EvaluationScenario(
            String scenarioKey,
            String question,
            String branchKey,
            String expectedCategory,
            String expectedBranchKey,
            boolean branchScenario
    ) {
    }
}
