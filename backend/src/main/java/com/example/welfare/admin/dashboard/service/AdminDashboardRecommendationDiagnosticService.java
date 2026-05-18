package com.example.welfare.admin.dashboard.service;

import com.example.welfare.admin.dashboard.dto.AdminRecommendationCandidateDiagnosticResponse;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.repository.WelfareServiceRepository;
import com.example.welfare.recommend.dto.RetrievedRecommendationCandidates;
import com.example.welfare.recommend.dto.ScoredCandidate;
import com.example.welfare.recommend.entity.UserRecommendation;
import com.example.welfare.recommend.service.ClusterService;
import com.example.welfare.recommend.service.RecommendationPostScoringFilterService;
import com.example.welfare.recommend.service.RecommendationResultReadService;
import com.example.welfare.recommend.service.ReRankingService;
import com.example.welfare.recommend.service.RetrievalService;
import com.example.welfare.recommend.service.RuleScoringService;
import com.example.welfare.user.entity.User;
import com.example.welfare.user.service.UserRecommendationReadService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminDashboardRecommendationDiagnosticService {

    private final UserRecommendationReadService userRecommendationReadService;
    private final ClusterService clusterService;
    private final RetrievalService retrievalService;
    private final RuleScoringService ruleScoringService;
    private final RecommendationPostScoringFilterService recommendationPostScoringFilterService;
    private final ReRankingService reRankingService;
    private final RecommendationResultReadService recommendationResultReadService;
    private final WelfareServiceRepository welfareServiceRepository;
    private static final String RERANK_TRACE_MODE = "PRE_AI_POST_SCORING";

    @Transactional(readOnly = true)
    public AdminRecommendationCandidateDiagnosticResponse getRecommendationDiagnostics(String userKey,
                                                                                      List<Long> serviceIds) {
        UserRecommendationReadService.RecommendationReadContext context =
                userRecommendationReadService.getRecommendationContextByUserKey(userKey);
        User user = context.user();
        String clusterId = clusterService.assignCluster(context.snapshot());

        RetrievalService.RecommendationRetrievalTrace trace = retrievalService.trace(context.snapshot());
        List<ScoredCandidate> scored = ruleScoringService.score(
                new RetrievedRecommendationCandidates(trace.mergedCandidates(), trace.allProjections()),
                context.snapshot()
        );
        List<ScoredCandidate> postScoring = recommendationPostScoringFilterService.filterSpecialTargetMismatches(scored);
        ReRankingService.RerankTrace rerankTrace = reRankingService.trace(postScoring, context.snapshot());
        List<UserRecommendation> latestSaved = recommendationResultReadService.findLatestSavedRecommendations(userKey);

        Map<Long, WelfareService> serviceById = new LinkedHashMap<>();
        mergeServices(serviceById, trace.rawBaseCandidates());
        mergeServices(serviceById, trace.rawLatestCandidates());
        mergeServices(serviceById, trace.filteredBaseCandidates());
        mergeServices(serviceById, trace.filteredLatestCandidates());
        mergeServices(serviceById, trace.mergedCandidates());
        welfareServiceRepository.findAllById(new LinkedHashSet<>(serviceIds))
                .forEach(service -> serviceById.putIfAbsent(service.getId(), service));

        Set<Long> baseIds = ids(trace.rawBaseCandidates());
        Set<Long> latestIds = ids(trace.rawLatestCandidates());
        Set<Long> filteredBaseIds = ids(trace.filteredBaseCandidates());
        Set<Long> filteredLatestIds = ids(trace.filteredLatestCandidates());
        Set<Long> mergedIds = ids(trace.mergedCandidates());
        Set<Long> postScoringIds = ids(postScoring.stream().map(ScoredCandidate::getService).toList());
        Map<Long, ScoredCandidate> scoredById = scored.stream()
                .collect(Collectors.toMap(candidate -> candidate.getService().getId(), Function.identity(), (left, right) -> left));
        Map<Long, UserRecommendation> savedById = latestSaved.stream()
                .collect(Collectors.toMap(rec -> rec.getService().getId(), Function.identity(), (left, right) -> left, LinkedHashMap::new));
        Map<Long, Integer> savedRankById = new LinkedHashMap<>();
        for (int i = 0; i < latestSaved.size(); i++) {
            savedRankById.put(latestSaved.get(i).getService().getId(), i + 1);
        }

        List<AdminRecommendationCandidateDiagnosticResponse.ServiceDiagnostic> serviceDiagnostics = new LinkedHashSet<>(serviceIds).stream()
                .map(serviceId -> {
                    WelfareService service = serviceById.get(serviceId);
                    ScoredCandidate scoredCandidate = scoredById.get(serviceId);
                    UserRecommendation savedRecommendation = savedById.get(serviceId);
                    var projection = trace.allProjections().get(serviceId);
                    Boolean youthRelevant = projection != null ? projection.youthRelevant() : null;

                    boolean inBase = baseIds.contains(serviceId);
                    boolean inLatest = latestIds.contains(serviceId);
                    boolean inFilteredBase = filteredBaseIds.contains(serviceId);
                    boolean inFilteredLatest = filteredLatestIds.contains(serviceId);
                    boolean inMerged = mergedIds.contains(serviceId);
                    boolean inPostScoring = postScoringIds.contains(serviceId);
                    boolean inSaved = savedById.containsKey(serviceId);
                    RetrievalService.CandidateFilterTrace filterTrace = trace.filterTraces().get(serviceId);
                    ReRankingService.CandidateRerankTrace rerankCandidateTrace = rerankTrace.candidateTraces().get(serviceId);

                    return new AdminRecommendationCandidateDiagnosticResponse.ServiceDiagnostic(
                            serviceId,
                            service != null ? service.getTitle() : null,
                            service != null && service.getSourceType() != null ? service.getSourceType().name() : null,
                            service != null ? service.getUnifiedCategory() : null,
                            projection != null ? projection.gov24UserTypeTokens() : List.of(),
                            projection != null ? projection.gov24BenefitTypeTokens() : List.of(),
                            projection != null ? projection.youthEmploymentRequirementCodes() : List.of(),
                            projection != null ? projection.youthEmploymentRequirementLabels() : List.of(),
                            projection != null ? projection.youthEducationRequirementCodes() : List.of(),
                            projection != null ? projection.youthEducationRequirementLabels() : List.of(),
                            projection != null ? projection.youthSpecialRequirementCodes() : List.of(),
                            projection != null ? projection.youthSpecialRequirementLabels() : List.of(),
                            projection != null ? projection.youthIncomeConditionTypeCode() : null,
                            projection != null ? projection.youthIncomeConditionTypeLabel() : null,
                            inBase,
                            inLatest,
                            inFilteredBase,
                            inFilteredLatest,
                            inMerged,
                            inPostScoring,
                            inSaved,
                            resolveDropStage(
                                    inBase,
                                    inLatest,
                                    inFilteredBase,
                                    inFilteredLatest,
                                    inMerged,
                                    inPostScoring,
                                    inSaved,
                                    filterTrace
                            ),
                            youthRelevant,
                            scoredCandidate != null ? scoredCandidate.getRuleBaseScore() : null,
                            scoredCandidate != null ? scoredCandidate.getRuleWeightedScore() : null,
                            scoredCandidate != null ? scoredCandidate.getAiScore() : null,
                            savedRecommendation != null && savedRecommendation.getAiScore() != null
                                    ? savedRecommendation.getAiScore().doubleValue()
                                    : null,
                            savedRecommendation != null && savedRecommendation.getAiStatus() != null
                                    ? savedRecommendation.getAiStatus().name()
                                    : null,
                            savedRecommendation != null && savedRecommendation.getFinalScore() != null
                                    ? savedRecommendation.getFinalScore().doubleValue()
                                    : null,
                            savedRankById.get(serviceId),
                            rerankCandidateTrace != null ? rerankCandidateTrace.normRule() : null,
                            rerankCandidateTrace != null ? rerankCandidateTrace.normAi() : null,
                            rerankCandidateTrace != null ? rerankCandidateTrace.baseBlendScore() : null,
                            rerankCandidateTrace != null ? rerankCandidateTrace.priorityMultiplier() : null,
                            rerankCandidateTrace != null ? rerankCandidateTrace.scoreAfterPriorityMultiplier() : null,
                            rerankCandidateTrace != null ? rerankCandidateTrace.diversityBucket() : null,
                            rerankCandidateTrace != null ? rerankCandidateTrace.diversityPenalty() : null,
                            rerankCandidateTrace != null && rerankCandidateTrace.noPriorityTopBandEligible(),
                            rerankCandidateTrace != null ? rerankCandidateTrace.noPriorityAdjustment() : null,
                            rerankCandidateTrace != null ? rerankCandidateTrace.currentFinalScore() : null,
                            rerankCandidateTrace != null ? rerankCandidateTrace.currentRank() : null,
                            scoredCandidate != null && scoredCandidate.isHasInterestMismatch(),
                            scoredCandidate != null && scoredCandidate.isHasPriorityMismatch(),
                            scoredCandidate != null && scoredCandidate.isHasSpecialTargetMismatch()
                    );
                })
                .toList();

        return new AdminRecommendationCandidateDiagnosticResponse(
                LocalDateTime.now(),
                userKey,
                user.getAccountOrigin().name(),
                clusterId,
                trace.rawBaseCandidates().size(),
                trace.rawLatestCandidates().size(),
                trace.filteredBaseCandidates().size(),
                trace.filteredLatestCandidates().size(),
                trace.mergedCandidates().size(),
                scored.size(),
                postScoring.size(),
                latestSaved.size(),
                RERANK_TRACE_MODE,
                serviceDiagnostics
        );
    }

    private void mergeServices(Map<Long, WelfareService> serviceById, List<WelfareService> services) {
        services.forEach(service -> serviceById.putIfAbsent(service.getId(), service));
    }

    private Set<Long> ids(List<WelfareService> services) {
        return services.stream()
                .map(WelfareService::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private String resolveDropStage(boolean inBase,
                                    boolean inLatest,
                                    boolean inFilteredBase,
                                    boolean inFilteredLatest,
                                    boolean inMerged,
                                    boolean inPostScoring,
                                    boolean inSaved,
                                    RetrievalService.CandidateFilterTrace filterTrace) {
        if (inSaved) {
            return "PRESENT_IN_SAVED_BATCH";
        }
        if (inMerged && !inPostScoring) {
            return "FILTERED_BY_SPECIAL_TARGET_MISMATCH";
        }
        if (inFilteredBase || inFilteredLatest) {
            return "SCORED_BUT_NOT_IN_SAVED_BATCH";
        }
        if (inBase || inLatest) {
            if (filterTrace != null) {
                if (!filterTrace.primaryAudienceRelevant() && !filterTrace.ageConstraintMatched()) {
                    return "FILTERED_BY_PRIMARY_AUDIENCE_AND_AGE";
                }
                if (!filterTrace.primaryAudienceRelevant()) {
                    return "FILTERED_BY_PRIMARY_AUDIENCE_RELEVANCE";
                }
                if (!filterTrace.ageConstraintMatched()) {
                    return "FILTERED_BY_AGE_CONSTRAINT";
                }
            }
            return "FILTERED_BY_YOUTH_OR_AGE";
        }
        return "NOT_IN_SQL_RETRIEVAL";
    }
}
