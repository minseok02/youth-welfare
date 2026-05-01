package com.example.welfare.recommend.service;

import com.example.welfare.recommend.dto.RecommendationUserSnapshot;
import com.example.welfare.recommend.dto.ScoredCandidate;
import com.example.welfare.recommend.gateway.AiRecommendationGateway;
import com.example.welfare.recommend.entity.ClusterAiResult;
import com.example.welfare.recommend.repository.ClusterAiResultRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * AI 점수 계층 — 군집 캐시 우선 조회, 없으면 실시간 호출 후 캐시 저장
 * youth_all 군집은 캐시 미사용 (개인화 추천 유지)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiScoringService {

    private final AiRecommendationGateway aiRecommendationGateway;
    private final ClusterAiResultRepository clusterAiResultRepository;

    @Transactional
    public List<ScoredCandidate> score(String clusterId, List<ScoredCandidate> candidates, RecommendationUserSnapshot user) {
        // youth_all은 개인 프로필 기반 실시간 호출 (캐시 미사용)
        if ("youth_all".equals(clusterId)) {
            return aiRecommendationGateway.score(clusterId, candidates, user);
        }

        // 군집 캐시 조회
        Map<Long, ClusterAiResult> cacheMap = clusterAiResultRepository
                .findByClusterId(clusterId)
                .stream()
                .collect(Collectors.toMap(c -> c.getService().getId(), c -> c));

        // 캐시 히트 여부 판별
        long cacheHitCount = candidates.stream()
                .filter(c -> cacheMap.containsKey(c.getService().getId()))
                .count();
        double hitRate = (double) cacheHitCount / candidates.size();

        log.info("[AiScoringService] clusterId={} cacheHit={}/{} hitRate={:.0f}%",
                clusterId, cacheHitCount, candidates.size(), hitRate * 100);

        // 캐시 히트율 50% 이상이면 캐시 사용
        if (hitRate >= 0.5) {
            candidates.forEach(c -> {
                ClusterAiResult cached = cacheMap.get(c.getService().getId());
                if (cached != null) {
                    c.setAiScore(cached.getAiScore().doubleValue());
                    c.setAiReason(cached.getAiReason());
                }
            });
            return candidates;
        }

        // 캐시 미스 — 실시간 AI 호출
        List<ScoredCandidate> scored = aiRecommendationGateway.score(clusterId, candidates, user);

        // 결과를 캐시에 저장 (UPSERT)
        scored.forEach(c -> {
            if (c.getAiScore() == null) return;
            ClusterAiResult existing = cacheMap.get(c.getService().getId());
            if (existing != null) {
                existing.update(BigDecimal.valueOf(c.getAiScore()), c.getAiReason());
            } else {
                clusterAiResultRepository.save(ClusterAiResult.builder()
                        .clusterId(clusterId)
                        .service(c.getService())
                        .aiScore(BigDecimal.valueOf(c.getAiScore()))
                        .aiReason(c.getAiReason())
                        .build());
            }
        });

        return scored;
    }
}
