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
     * null AI는 아래 상태로 구분된다.
     * - NOT_REQUESTED: 상위 AI 호출 대상 밖
     * - PARTIAL_MISSING: 요청했지만 응답에서 service_id 누락
     * - CALL_FAILED: 호출/파싱 전체 실패
     * - RULE_ONLY: 강제 rule-only
     */
    List<ScoredCandidate> score(String clusterId, List<ScoredCandidate> candidates, RecommendationUserSnapshot user);
}
