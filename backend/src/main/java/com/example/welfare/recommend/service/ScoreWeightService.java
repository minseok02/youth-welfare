package com.example.welfare.recommend.service;

import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.example.welfare.recommend.entity.ScoreWeight;
import com.example.welfare.recommend.repository.RecommendationLogReadRepository;
import com.example.welfare.recommend.repository.ScoreWeightReadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

/**
 * recommendation_logs 전체 건수 기준으로 Cold Start 단계 결정
 * COLD_START(0~99) / GROWTH(100~499) / STABLE(500~)
 * 가중치는 반드시 score_weights 테이블 조회 — 하드코딩 금지 (CLAUDE.md 원칙)
 */
@Service
@RequiredArgsConstructor
public class ScoreWeightService {

    private final RecommendationLogReadRepository recommendationLogReadRepository;
    private final ScoreWeightReadRepository scoreWeightReadRepository;

    @Transactional(readOnly = true)
    public ScoreWeight getActiveWeight() {
        long totalLogCount = recommendationLogReadRepository.countAll();
        return getProgress(totalLogCount).activeWeight();
    }

    @Transactional(readOnly = true)
    public ScoreWeightProgress getProgress(long totalLogCount) {
        List<ScoreWeight> activeWeights = getConfiguredActiveWeights();
        ScoreWeight activeWeight = resolveActiveWeight(activeWeights, totalLogCount);
        ScoreWeight nextWeight = activeWeights.stream()
                .filter(weight -> weight.getMinLogCount() > totalLogCount)
                .min(Comparator.comparing(ScoreWeight::getMinLogCount))
                .orElse(null);

        return new ScoreWeightProgress(
                activeWeight,
                totalLogCount,
                nextWeight != null ? nextWeight.getWeightKey() : null,
                nextWeight != null ? nextWeight.getMinLogCount() : null,
                nextWeight != null ? (long) nextWeight.getMinLogCount() - totalLogCount : null,
                nextWeight == null
        );
    }

    private List<ScoreWeight> getConfiguredActiveWeights() {
        List<ScoreWeight> activeWeights = scoreWeightReadRepository.findConfiguredActiveWeights();
        if (activeWeights.isEmpty()) {
            throw new CustomException(ErrorCode.SCORE_WEIGHT_NOT_CONFIGURED);
        }
        return activeWeights;
    }

    private ScoreWeight resolveActiveWeight(List<ScoreWeight> activeWeights, long totalLogCount) {
        // totalLogCount 이하인 단계 중 minLogCount가 가장 큰 것 선택
        return activeWeights.stream()
                .filter(w -> totalLogCount >= w.getMinLogCount())
                .max(Comparator.comparing(ScoreWeight::getMinLogCount))
                .orElse(activeWeights.get(0)); // 최소 단계(COLD_START) fallback
    }

    public record ScoreWeightProgress(
            ScoreWeight activeWeight,
            long totalLogCount,
            String nextWeightKey,
            Integer nextWeightMinLogCount,
            Long remainingLogsUntilNextWeight,
            boolean topWeightStage
    ) {
    }
}
