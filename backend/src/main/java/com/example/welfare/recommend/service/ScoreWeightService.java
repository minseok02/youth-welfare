package com.example.welfare.recommend.service;

import com.example.welfare.recommend.entity.ScoreWeight;
import com.example.welfare.recommend.repository.RecommendationLogRepository;
import com.example.welfare.recommend.repository.ScoreWeightRepository;
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

    private final RecommendationLogRepository logRepository;
    private final ScoreWeightRepository scoreWeightRepository;

    @Transactional(readOnly = true)
    public ScoreWeight getActiveWeight() {
        long totalLogCount = logRepository.count();

        List<ScoreWeight> activeWeights = scoreWeightRepository.findByIsActiveTrueOrderByMinLogCountAsc();

        // totalLogCount 이하인 단계 중 minLogCount가 가장 큰 것 선택
        return activeWeights.stream()
                .filter(w -> totalLogCount >= w.getMinLogCount())
                .max(Comparator.comparing(ScoreWeight::getMinLogCount))
                .orElse(activeWeights.get(0)); // 최소 단계(COLD_START) fallback
    }
}
