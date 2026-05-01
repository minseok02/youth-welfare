package com.example.welfare.recommend.service;

import com.example.welfare.policy.entity.WelfareService;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * cluster_ai_results persistence 경계를 추상화해 1차 추천 파이프라인이 JPA entity/repository에 직접 결합되지 않게 한다.
 */
public interface ClusterAiScoreCache {

    Map<Long, CachedClusterAiScore> findByClusterId(String clusterId);

    void saveAll(String clusterId, List<ClusterAiScoreWrite> writes);

    record CachedClusterAiScore(
            Long serviceId,
            BigDecimal aiScore,
            String aiReason
    ) {
    }

    record ClusterAiScoreWrite(
            WelfareService service,
            BigDecimal aiScore,
            String aiReason
    ) {
    }
}
