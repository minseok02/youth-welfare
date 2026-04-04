package com.example.welfare.recommend.service;

import com.example.welfare.recommend.dto.ScoredCandidate;
import com.example.welfare.recommend.entity.ScoreWeight;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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

    public List<ScoredCandidate> rerank(List<ScoredCandidate> candidates) {
        ScoreWeight weight = scoreWeightService.getActiveWeight();

        double ruleMin = normalizer.findMin(
                candidates.stream().map(ScoredCandidate::getRuleWeightedScore).collect(Collectors.toList()));
        double ruleMax = normalizer.findMax(
                candidates.stream().map(ScoredCandidate::getRuleWeightedScore).collect(Collectors.toList()));

        candidates.forEach(c -> {
            double normRule = normalizer.normalize(c.getRuleWeightedScore(), ruleMin, ruleMax);

            double finalScore;
            boolean fallback;

            if (c.getAiScore() != null) {
                double normAi = normalizer.normalize(c.getAiScore(), 0.0, 100.0);
                finalScore = normRule * weight.getRuleWeight().doubleValue()
                        + normAi * weight.getAiWeight().doubleValue();
                fallback = false;
            } else {
                // ai_score NULL → rule만 사용
                finalScore = normRule;
                fallback = true;
            }

            c.setFinalScore(finalScore);
            c.setAiFallback(fallback);
        });

        return candidates.stream()
                .sorted(Comparator.comparingDouble(ScoredCandidate::getFinalScore).reversed())
                .collect(Collectors.toList());
    }

    public ScoreWeight getCurrentWeight() {
        return scoreWeightService.getActiveWeight();
    }
}
