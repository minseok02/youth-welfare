package com.example.welfare.recommend.service;

import com.example.welfare.recommend.dto.ScoredCandidate;
import com.example.welfare.recommend.dto.RecommendationUserSnapshot;
import com.example.welfare.recommend.entity.AiScoreStatus;
import com.example.welfare.recommend.entity.ScoreWeight;
import com.example.welfare.policy.entity.WelfareService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 최종 점수 계산 + 정렬
 * final_score = norm_rule × rule_weight + norm_ai × ai_weight
 * ai_score NULL이면 norm_rule만 사용
 */
@Service
@RequiredArgsConstructor
public class ReRankingService {
    private static final int NO_PRIORITY_TOP_ELIGIBLE_LIMIT = 8;
    private static final double NO_PRIORITY_TOP_BAND = 0.15d;
    private static final double NO_PRIORITY_TOP_BAND_EXPANDED = 0.20d;
    private static final double NO_PRIORITY_PREFERRED_CANDIDATE_BONUS = 0.085d;
    private static final double NO_PRIORITY_SECONDARY_CANDIDATE_BONUS = 0.03d;
    private static final double NO_PRIORITY_NON_PREFERRED_TOP_PENALTY = 0.025d;
    private static final int NO_PRIORITY_ROTATION_LIMIT = 4;

    private final ScoreNormalizer normalizer;
    private final ScoreWeightService scoreWeightService;
    private final RecommendationDiversityService recommendationDiversityService;

    public List<ScoredCandidate> rerank(List<ScoredCandidate> candidates) {
        return rerank(candidates, null);
    }

    public List<ScoredCandidate> rerank(List<ScoredCandidate> candidates, RecommendationUserSnapshot snapshot) {
        return trace(candidates, snapshot).rankedCandidates();
    }

    public RerankTrace trace(List<ScoredCandidate> candidates, RecommendationUserSnapshot snapshot) {
        ScoreWeight weight = scoreWeightService.getActiveWeight();

        // 하한을 0으로 고정: 최솟값 정책도 final_score=0이 되지 않도록
        double ruleMax = normalizer.findMax(
                candidates.stream().map(ScoredCandidate::getRuleWeightedScore).collect(Collectors.toList()));

        Map<Long, CandidateRerankTraceAccumulator> traceByServiceId = new LinkedHashMap<>();
        List<ScoredCandidate> scored = candidates.stream()
                .map(candidate -> {
                    double normRule = normalizer.normalize(candidate.getRuleWeightedScore(), 0.0, ruleMax);

                    double finalScore;
                    boolean fallback;
                    Double normAi = null;
                    double adjustedAiWeight = 0d;
                    double adjustedRuleWeight;
                    double baseBlendScore;

                    if (candidate.getAiStatus() == AiScoreStatus.SCORED && candidate.getAiScore() != null) {
                        normAi = normalizer.normalize(candidate.getAiScore(), 0.0, 100.0);
                        adjustedAiWeight = adjustedAiWeight(weight, candidate);
                        adjustedRuleWeight = weight.getRuleWeight().doubleValue()
                                + (weight.getAiWeight().doubleValue() - adjustedAiWeight);
                        baseBlendScore = normRule * adjustedRuleWeight
                                + normAi * adjustedAiWeight;
                        finalScore = baseBlendScore;
                        fallback = false;
                    } else {
                        // ai_score NULL → rule만 사용
                        adjustedRuleWeight = 1.0d;
                        baseBlendScore = normRule;
                        finalScore = baseBlendScore;
                        fallback = true;
                    }

                    double priorityMultiplier = priorityRankAdjustmentMultiplier(candidate);
                    finalScore = finalScore * priorityMultiplier;

                    Long serviceId = candidate.getService().getId();
                    if (serviceId != null) {
                        traceByServiceId.put(serviceId, new CandidateRerankTraceAccumulator(
                                normRule,
                                normAi,
                                adjustedRuleWeight,
                                adjustedAiWeight,
                                baseBlendScore,
                                priorityMultiplier,
                                finalScore
                        ));
                    }

                    return candidate.withFinalScore(finalScore, fallback);
                })
                .toList();

        RecommendationDiversityService.DiversityTrace diversityTrace = recommendationDiversityService.trace(
                scored.stream().sorted(recommendationComparator()).toList()
        );
        for (Map.Entry<Long, RecommendationDiversityService.CandidateDiversityTrace> entry : diversityTrace.candidateTraces().entrySet()) {
            CandidateRerankTraceAccumulator accumulator = traceByServiceId.get(entry.getKey());
            if (accumulator == null) {
                continue;
            }
            accumulator.diversityBucket = entry.getValue().bucket();
            accumulator.diversityPenalty = entry.getValue().penalty();
        }

        List<ScoredCandidate> diversified = applyNoPriorityTopBandDiversification(
                diversityTrace.adjustedCandidates(),
                snapshot,
                traceByServiceId
        );
        List<ScoredCandidate> ranked = diversified.stream()
                .sorted(recommendationComparator())
                .toList();
        List<ScoredCandidate> reordered = reorderNoPriorityTopBand(ranked, snapshot);
        for (int index = 0; index < reordered.size(); index++) {
            ScoredCandidate candidate = reordered.get(index);
            Long serviceId = candidate.getService().getId();
            if (serviceId == null) {
                continue;
            }
            CandidateRerankTraceAccumulator accumulator = traceByServiceId.get(serviceId);
            if (accumulator == null) {
                continue;
            }
            accumulator.currentFinalScore = candidate.getFinalScore();
            accumulator.currentRank = index + 1;
            if (accumulator.diversityBucket == null) {
                accumulator.diversityBucket = normalizeNoPriorityBucket(candidate.getService().getUnifiedCategory());
            }
        }
        Map<Long, CandidateRerankTrace> candidateTraces = traceByServiceId.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().toTrace(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        return new RerankTrace(reordered, candidateTraces);
    }

    public ScoreWeight getCurrentWeight() {
        return scoreWeightService.getActiveWeight();
    }

    private List<ScoredCandidate> applyNoPriorityTopBandDiversification(List<ScoredCandidate> scored,
                                                                        RecommendationUserSnapshot snapshot,
                                                                        Map<Long, CandidateRerankTraceAccumulator> traceByServiceId) {
        if (snapshot == null
                || !snapshot.priorities().isEmpty()
                || scored == null
                || scored.size() < 3
                || snapshot.userKey() == null
                || snapshot.userKey().isBlank()) {
            return scored;
        }

        List<ScoredCandidate> ranked = scored.stream()
                .sorted(recommendationComparator())
                .toList();
        List<ScoredCandidate> eligible = eligibleNoPriorityTopBandCandidates(ranked, NO_PRIORITY_TOP_BAND);
        if (eligible.size() < 2) {
            eligible = eligibleNoPriorityTopBandCandidates(ranked, NO_PRIORITY_TOP_BAND_EXPANDED);
        }
        if (eligible.size() < 2) {
            return scored;
        }

        for (ScoredCandidate candidate : eligible) {
            Long serviceId = candidate.getService().getId();
            if (serviceId == null) {
                continue;
            }
            CandidateRerankTraceAccumulator accumulator = traceByServiceId.get(serviceId);
            if (accumulator != null) {
                accumulator.noPriorityTopBandEligible = true;
            }
        }

        int preferredIndex = Math.floorMod(snapshot.userKey().chars().sum(), eligible.size());
        int secondaryIndex = eligible.size() > 2
                ? (preferredIndex + 1) % eligible.size()
                : -1;

        Map<Long, ScoredCandidate> adjustedByServiceId = new LinkedHashMap<>();
        ScoredCandidate originalTopCandidate = eligible.get(0);
        ScoredCandidate preferredCandidate = eligible.get(preferredIndex);
        if (preferredCandidate.getService().getId() != null) {
            CandidateRerankTraceAccumulator accumulator = traceByServiceId.get(preferredCandidate.getService().getId());
            if (accumulator != null) {
                accumulator.noPriorityAdjustment += NO_PRIORITY_PREFERRED_CANDIDATE_BONUS;
            }
            adjustedByServiceId.put(
                    preferredCandidate.getService().getId(),
                    preferredCandidate.withFinalScore(
                            preferredCandidate.getFinalScore() + NO_PRIORITY_PREFERRED_CANDIDATE_BONUS,
                            preferredCandidate.isAiFallback()
                    )
            );
        }
        if (secondaryIndex >= 0) {
            ScoredCandidate secondaryCandidate = eligible.get(secondaryIndex);
            if (secondaryCandidate.getService().getId() != null
                    && !adjustedByServiceId.containsKey(secondaryCandidate.getService().getId())) {
                CandidateRerankTraceAccumulator accumulator = traceByServiceId.get(secondaryCandidate.getService().getId());
                if (accumulator != null) {
                    accumulator.noPriorityAdjustment += NO_PRIORITY_SECONDARY_CANDIDATE_BONUS;
                }
                adjustedByServiceId.put(
                        secondaryCandidate.getService().getId(),
                        secondaryCandidate.withFinalScore(
                                secondaryCandidate.getFinalScore() + NO_PRIORITY_SECONDARY_CANDIDATE_BONUS,
                                secondaryCandidate.isAiFallback()
                        )
                );
            }
        }
        if (originalTopCandidate.getService().getId() != null
                && !originalTopCandidate.getService().getId().equals(preferredCandidate.getService().getId())) {
            CandidateRerankTraceAccumulator accumulator = traceByServiceId.get(originalTopCandidate.getService().getId());
            if (accumulator != null) {
                accumulator.noPriorityAdjustment -= NO_PRIORITY_NON_PREFERRED_TOP_PENALTY;
            }
            adjustedByServiceId.put(
                    originalTopCandidate.getService().getId(),
                    originalTopCandidate.withFinalScore(
                            Math.max(originalTopCandidate.getFinalScore() - NO_PRIORITY_NON_PREFERRED_TOP_PENALTY, 0d),
                            originalTopCandidate.isAiFallback()
                    )
            );
        }
        if (adjustedByServiceId.isEmpty()) {
            return scored;
        }

        return scored.stream()
                .map(candidate -> {
                    Long serviceId = candidate.getService().getId();
                    if (serviceId == null) {
                        return candidate;
                    }
                    return adjustedByServiceId.getOrDefault(serviceId, candidate);
                })
                .toList();
    }

    private List<ScoredCandidate> reorderNoPriorityTopBand(List<ScoredCandidate> scored,
                                                           RecommendationUserSnapshot snapshot) {
        if (snapshot == null
                || !snapshot.priorities().isEmpty()
                || scored == null
                || scored.size() < 3
                || snapshot.userKey() == null
                || snapshot.userKey().isBlank()) {
            return scored;
        }

        List<ScoredCandidate> ranked = scored.stream()
                .sorted(recommendationComparator())
                .toList();
        List<ScoredCandidate> eligible = eligibleNoPriorityRotationCandidates(ranked, NO_PRIORITY_TOP_BAND);
        if (eligible.size() < 2) {
            eligible = eligibleNoPriorityRotationCandidates(ranked, NO_PRIORITY_TOP_BAND_EXPANDED);
        }
        if (eligible.size() < 2) {
            return ranked;
        }

        Map<String, List<ScoredCandidate>> buckets = bucketizeNoPriorityTopBand(eligible);
        if (buckets.size() < 2) {
            eligible = eligibleNoPriorityRotationCandidates(ranked, NO_PRIORITY_TOP_BAND_EXPANDED);
            buckets = bucketizeNoPriorityTopBand(eligible);
        }
        if (buckets.size() < 2) {
            return ranked;
        }

        List<String> orderedBuckets = new ArrayList<>(buckets.keySet());
        int preferredBucketIndex = Math.floorMod(snapshot.userKey().chars().sum(), orderedBuckets.size());
        String preferredBucket = orderedBuckets.get(preferredBucketIndex);
        List<ScoredCandidate> preferredBucketCandidates = buckets.get(preferredBucket);
        ScoredCandidate preferredCandidate = selectPreferredBucketCandidate(preferredBucketCandidates, snapshot.userKey());
        Long topCandidateId = eligible.get(0).getService().getId();
        Long preferredId = preferredCandidate.getService().getId();
        if (preferredId != null && preferredId.equals(topCandidateId)) {
            return ranked;
        }

        ArrayList<ScoredCandidate> reordered = new ArrayList<>(ranked.size());
        reordered.add(preferredCandidate);
        for (ScoredCandidate candidate : ranked) {
            Long candidateId = candidate.getService().getId();
            if (candidateId != null && candidateId.equals(preferredId)) {
                continue;
            }
            reordered.add(candidate);
        }
        return List.copyOf(reordered);
    }

    private List<ScoredCandidate> eligibleNoPriorityTopBandCandidates(List<ScoredCandidate> ranked, double band) {
        if (ranked == null || ranked.isEmpty()) {
            return List.of();
        }
        double topScore = ranked.get(0).getFinalScore();
        return ranked.stream()
                .limit(NO_PRIORITY_TOP_ELIGIBLE_LIMIT)
                .filter(candidate -> topScore - candidate.getFinalScore() <= band)
                .toList();
    }

    private List<ScoredCandidate> eligibleNoPriorityRotationCandidates(List<ScoredCandidate> ranked, double band) {
        if (ranked == null || ranked.isEmpty()) {
            return List.of();
        }
        double topScore = ranked.get(0).getFinalScore();
        return ranked.stream()
                .limit(NO_PRIORITY_ROTATION_LIMIT)
                .filter(candidate -> topScore - candidate.getFinalScore() <= band)
                .toList();
    }

    private Map<String, List<ScoredCandidate>> bucketizeNoPriorityTopBand(List<ScoredCandidate> eligible) {
        LinkedHashMap<String, List<ScoredCandidate>> byCategory = new LinkedHashMap<>();
        for (ScoredCandidate candidate : eligible) {
            String category = normalizeNoPriorityBucket(candidate.getService().getUnifiedCategory());
            byCategory.computeIfAbsent(category, ignored -> new ArrayList<>()).add(candidate);
        }
        if (byCategory.size() >= 2) {
            return byCategory;
        }

        LinkedHashMap<String, List<ScoredCandidate>> bySource = new LinkedHashMap<>();
        for (ScoredCandidate candidate : eligible) {
            String source = candidate.getService().getSourceType() == null
                    ? "UNKNOWN"
                    : candidate.getService().getSourceType().name();
            bySource.computeIfAbsent(source, ignored -> new ArrayList<>()).add(candidate);
        }
        if (bySource.size() >= 2) {
            return bySource;
        }

        LinkedHashMap<String, List<ScoredCandidate>> byService = new LinkedHashMap<>();
        for (ScoredCandidate candidate : eligible) {
            Long serviceId = candidate.getService().getId();
            String serviceBucket = serviceId == null ? candidate.getService().getTitle() : "SERVICE:" + serviceId;
            byService.computeIfAbsent(serviceBucket, ignored -> new ArrayList<>()).add(candidate);
        }
        return byService;
    }

    private String normalizeNoPriorityBucket(String raw) {
        if (raw == null || raw.isBlank()) {
            return "기타";
        }
        return raw.trim();
    }

    private ScoredCandidate selectPreferredBucketCandidate(List<ScoredCandidate> bucketCandidates, String userKey) {
        if (bucketCandidates == null || bucketCandidates.isEmpty()) {
            throw new IllegalArgumentException("bucketCandidates must not be empty");
        }
        if (bucketCandidates.size() == 1) {
            return bucketCandidates.get(0);
        }
        int preferredIndex = Math.floorMod(userKey.chars().sum(), bucketCandidates.size());
        return bucketCandidates.get(preferredIndex);
    }

    private double adjustedAiWeight(ScoreWeight weight, ScoredCandidate candidate) {
        double multiplier = aiWeightTrustMultiplier(candidate);
        return weight.getAiWeight().doubleValue() * multiplier;
    }

    private double aiWeightTrustMultiplier(ScoredCandidate candidate) {
        if (candidate.isHasPriorityMismatch() && candidate.isHasInterestMismatch()) {
            return 0.2;
        }
        if (candidate.isHasPriorityMismatch()) {
            return 0.35;
        }
        if (candidate.isHasInterestMismatch()) {
            return 0.6;
        }
        return 1.0;
    }

    private double priorityRankAdjustmentMultiplier(ScoredCandidate candidate) {
        Integer rank = candidate.getMatchedPriorityRank();
        if (rank == null) {
            return 1.0;
        }
        return switch (rank) {
            case 1 -> 1.12;
            case 2 -> 0.72;
            case 3 -> 0.58;
            case 4 -> 0.50;
            default -> 0.45;
        };
    }

    private Comparator<ScoredCandidate> recommendationComparator() {
        return Comparator.comparingDouble(ScoredCandidate::getFinalScore).reversed()
                .thenComparing(ScoredCandidate::getAiScore, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(this::deadlinePriority)
                .thenComparing(this::applyEndDateOrMax)
                .thenComparing(this::viewCountOrZero, Comparator.reverseOrder())
                .thenComparing(this::apiViewCountOrZero, Comparator.reverseOrder())
                .thenComparing(this::registeredAtOrMin, Comparator.reverseOrder())
                .thenComparing(this::serviceIdOrZero, Comparator.reverseOrder());
    }

    private Integer deadlinePriority(ScoredCandidate candidate) {
        WelfareService service = candidate.getService();
        LocalDate applyEndDate = service.getApplyEndDate();
        if (applyEndDate == null) {
            return 1;
        }
        LocalDate today = LocalDate.now();
        return (!applyEndDate.isBefore(today) && applyEndDate.isBefore(today.plusDays(7))) ? 0 : 1;
    }

    private LocalDate applyEndDateOrMax(ScoredCandidate candidate) {
        return candidate.getService().getApplyEndDate() != null
                ? candidate.getService().getApplyEndDate()
                : LocalDate.MAX;
    }

    private Integer viewCountOrZero(ScoredCandidate candidate) {
        return candidate.getService().getViewCount() != null ? candidate.getService().getViewCount() : 0;
    }

    private Long apiViewCountOrZero(ScoredCandidate candidate) {
        return candidate.getService().getApiViewCount() != null ? candidate.getService().getApiViewCount() : 0L;
    }

    private java.time.LocalDateTime registeredAtOrMin(ScoredCandidate candidate) {
        return candidate.getService().getRegisteredAt() != null
                ? candidate.getService().getRegisteredAt()
                : java.time.LocalDateTime.MIN;
    }

    private Long serviceIdOrZero(ScoredCandidate candidate) {
        return candidate.getService().getId() != null ? candidate.getService().getId() : 0L;
    }

    public record RerankTrace(
            List<ScoredCandidate> rankedCandidates,
            Map<Long, CandidateRerankTrace> candidateTraces
    ) {
    }

    public record CandidateRerankTrace(
            double normRule,
            Double normAi,
            double adjustedRuleWeight,
            double adjustedAiWeight,
            double baseBlendScore,
            double priorityMultiplier,
            double scoreAfterPriorityMultiplier,
            String diversityBucket,
            double diversityPenalty,
            boolean noPriorityTopBandEligible,
            double noPriorityAdjustment,
            Double currentFinalScore,
            Integer currentRank
    ) {
    }

    private static final class CandidateRerankTraceAccumulator {
        private final double normRule;
        private final Double normAi;
        private final double adjustedRuleWeight;
        private final double adjustedAiWeight;
        private final double baseBlendScore;
        private final double priorityMultiplier;
        private final double scoreAfterPriorityMultiplier;
        private String diversityBucket;
        private double diversityPenalty;
        private boolean noPriorityTopBandEligible;
        private double noPriorityAdjustment;
        private Double currentFinalScore;
        private Integer currentRank;

        private CandidateRerankTraceAccumulator(double normRule,
                                                Double normAi,
                                                double adjustedRuleWeight,
                                                double adjustedAiWeight,
                                                double baseBlendScore,
                                                double priorityMultiplier,
                                                double scoreAfterPriorityMultiplier) {
            this.normRule = normRule;
            this.normAi = normAi;
            this.adjustedRuleWeight = adjustedRuleWeight;
            this.adjustedAiWeight = adjustedAiWeight;
            this.baseBlendScore = baseBlendScore;
            this.priorityMultiplier = priorityMultiplier;
            this.scoreAfterPriorityMultiplier = scoreAfterPriorityMultiplier;
        }

        private CandidateRerankTrace toTrace() {
            return new CandidateRerankTrace(
                    normRule,
                    normAi,
                    adjustedRuleWeight,
                    adjustedAiWeight,
                    baseBlendScore,
                    priorityMultiplier,
                    scoreAfterPriorityMultiplier,
                    diversityBucket,
                    diversityPenalty,
                    noPriorityTopBandEligible,
                    noPriorityAdjustment,
                    currentFinalScore,
                    currentRank
            );
        }
    }
}
