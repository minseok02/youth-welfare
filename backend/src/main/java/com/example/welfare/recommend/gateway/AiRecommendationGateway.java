package com.example.welfare.recommend.gateway;

import com.example.welfare.recommend.dto.RecommendationUserSnapshot;
import com.example.welfare.recommend.dto.ScoredCandidate;

import java.util.List;

/**
 * AI 추천 점수 획득 인터페이스
 * 1차: RealtimeAiGateway (실시간 단건)
 * 2차: BatchAiGateway (Batch API) — 현재 미구현
 */
public interface AiRecommendationGateway {

    /**
     * 후보 목록에 대해 AI 점수(0~100) + 추천 이유를 채워 반환
     * 실패 시 해당 후보의 aiScore = null (NULL-safe)
     */
    List<ScoredCandidate> score(String clusterId, List<ScoredCandidate> candidates, RecommendationUserSnapshot user);
}
