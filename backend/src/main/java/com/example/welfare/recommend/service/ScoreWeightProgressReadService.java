package com.example.welfare.recommend.service;

import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.example.welfare.recommend.entity.ScoreWeight;
import com.example.welfare.recommend.repository.RecommendationLogReadRepository;
import com.example.welfare.recommend.repository.ScoreWeightReadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ScoreWeightProgressReadService {

    private final RecommendationLogReadRepository recommendationLogReadRepository;
    private final ScoreWeightReadRepository scoreWeightReadRepository;

    public ScoreWeightProgressSnapshot loadSnapshot() {
        long totalLogCount = recommendationLogReadRepository.countAll();
        List<ScoreWeight> activeWeights = scoreWeightReadRepository.findConfiguredActiveWeights();
        if (activeWeights.isEmpty()) {
            throw new CustomException(ErrorCode.SCORE_WEIGHT_NOT_CONFIGURED);
        }
        return new ScoreWeightProgressSnapshot(totalLogCount, activeWeights);
    }

    public record ScoreWeightProgressSnapshot(
            long totalLogCount,
            List<ScoreWeight> activeWeights
    ) {
    }
}
