package com.example.welfare.recommend.service;

import com.example.welfare.recommend.dto.ScoredCandidate;
import com.example.welfare.recommend.dto.RecommendationUserSnapshot;
import com.example.welfare.recommend.gateway.AiRecommendationGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * AI 점수 계층 — Gateway를 통해 OpenAI 호출
 * 실패 시 aiScore = null 유지 (NULL-safe)
 */
@Service
@RequiredArgsConstructor
public class AiScoringService {

    private final AiRecommendationGateway aiRecommendationGateway;

    public List<ScoredCandidate> score(String clusterId, List<ScoredCandidate> candidates, RecommendationUserSnapshot user) {
        return aiRecommendationGateway.score(clusterId, candidates, user);
    }
}
