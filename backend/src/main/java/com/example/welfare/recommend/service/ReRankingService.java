package com.example.welfare.recommend.service;

import com.example.welfare.recommend.dto.ScoredCandidate;
import com.example.welfare.recommend.entity.ScoreWeight;
import com.example.welfare.policy.entity.WelfareService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 최종 점수 계산 + 정렬
 * final_score = norm_rule × rule_weight + norm_ai × ai_weight
 * ai_score NULL이면 norm_rule만 사용
 */
@Service
@RequiredArgsConstructor
public class ReRankingService {

    private final ScoreNormalizer normalizer;
    private final ScoreWeightService scoreWeightService;
    private final RecommendationDiversityService recommendationDiversityService;

    public List<ScoredCandidate> rerank(List<ScoredCandidate> candidates) {
        ScoreWeight weight = scoreWeightService.getActiveWeight();

        // 하한을 0으로 고정: 최솟값 정책도 final_score=0이 되지 않도록
        double ruleMax = normalizer.findMax(
                candidates.stream().map(ScoredCandidate::getRuleWeightedScore).collect(Collectors.toList()));

        List<ScoredCandidate> scored = candidates.stream()
                .map(candidate -> {
                    double normRule = normalizer.normalize(candidate.getRuleWeightedScore(), 0.0, ruleMax);

                    double finalScore;
                    boolean fallback;

                    if (candidate.getAiScore() != null) {
                        double normAi = normalizer.normalize(candidate.getAiScore(), 0.0, 100.0);
                        double adjustedAiWeight = adjustedAiWeight(weight, candidate);
                        double adjustedRuleWeight = weight.getRuleWeight().doubleValue()
                                + (weight.getAiWeight().doubleValue() - adjustedAiWeight);
                        finalScore = normRule * adjustedRuleWeight
                                + normAi * adjustedAiWeight;
                        fallback = false;
                    } else {
                        // ai_score NULL → rule만 사용
                        finalScore = normRule;
                        fallback = true;
                    }

                    finalScore = finalScore * priorityRankAdjustmentMultiplier(candidate);

                    return candidate.withFinalScore(finalScore, fallback);
                })
                .toList();

        List<ScoredCandidate> diversified = recommendationDiversityService.apply(
                scored.stream().sorted(recommendationComparator()).toList()
        );
        return diversified.stream()
                .sorted(recommendationComparator())
                .toList();
    }

    public ScoreWeight getCurrentWeight() {
        return scoreWeightService.getActiveWeight();
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
}
