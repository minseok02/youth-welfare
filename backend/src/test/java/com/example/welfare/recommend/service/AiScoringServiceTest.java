package com.example.welfare.recommend.service;

import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.recommend.dto.RecommendationUserSnapshot;
import com.example.welfare.recommend.dto.ScoredCandidate;
import com.example.welfare.recommend.entity.AiScoreStatus;
import com.example.welfare.recommend.gateway.AiRecommendationGateway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiScoringServiceTest {

    @Mock
    private AiRecommendationGateway aiRecommendationGateway;
    @Mock
    private ClusterAiScoreCache clusterAiScoreCache;

    @Test
    @DisplayName("cluster cache hit rate 가 높으면 gateway 대신 cache abstraction 을 사용한다")
    void scoreUsesCacheAbstractionWhenHitRateHigh() {
        AiScoringService service = new AiScoringService(aiRecommendationGateway, clusterAiScoreCache);
        RecommendationUserSnapshot snapshot = snapshot();
        ScoredCandidate first = candidate(1L, "정책1");
        ScoredCandidate second = candidate(2L, "정책2");
        List<ScoredCandidate> candidates = List.of(first, second);

        Map<Long, ClusterAiScoreCache.CachedClusterAiScore> cache = new LinkedHashMap<>();
        cache.put(1L, new ClusterAiScoreCache.CachedClusterAiScore(1L, BigDecimal.valueOf(81.5), "cached-1"));
        cache.put(2L, new ClusterAiScoreCache.CachedClusterAiScore(2L, BigDecimal.valueOf(76.0), "cached-2"));
        when(clusterAiScoreCache.findByClusterId("cluster-a")).thenReturn(cache);

        List<ScoredCandidate> scored = service.score("cluster-a", candidates, snapshot);

        assertThat(scored).hasSize(2);
        assertThat(scored.get(0).getAiScore()).isEqualTo(81.5);
        assertThat(scored.get(0).getAiReason()).isEqualTo("cached-1");
        assertThat(scored.get(1).getAiScore()).isEqualTo(76.0);
        assertThat(scored.get(1).getAiReason()).isEqualTo("cached-2");
        assertThat(first.getAiScore()).isNull();
        assertThat(second.getAiScore()).isNull();
        verify(aiRecommendationGateway, never()).score(eq("cluster-a"), anyList(), eq(snapshot));
        verify(clusterAiScoreCache, never()).saveAll(eq("cluster-a"), anyList());
    }

    @Test
    @DisplayName("cache miss 시 gateway 결과를 cache abstraction 으로 저장한다")
    void scoreStoresGatewayResultsThroughCacheAbstraction() {
        AiScoringService service = new AiScoringService(aiRecommendationGateway, clusterAiScoreCache);
        RecommendationUserSnapshot snapshot = snapshot();
        ScoredCandidate candidate = candidate(3L, "정책3");
        when(clusterAiScoreCache.findByClusterId("cluster-b")).thenReturn(Map.of());
        when(aiRecommendationGateway.score("cluster-b", List.of(candidate), snapshot)).thenAnswer(invocation -> {
            return List.of(candidate.withAiResult(88.0, "gateway", AiScoreStatus.SCORED));
        });

        List<ScoredCandidate> scored = service.score("cluster-b", List.of(candidate), snapshot);

        assertThat(scored).singleElement().satisfies(value -> {
            assertThat(value.getAiScore()).isEqualTo(88.0);
            assertThat(value.getAiReason()).isEqualTo("gateway");
        });
        verify(clusterAiScoreCache).saveAll(eq("cluster-b"), anyList());
    }

    private RecommendationUserSnapshot snapshot() {
        return new RecommendationUserSnapshot(
                1L, "user-key", 25, "20대", "서울특별시", "관악구", "11620",
                (byte) 3, "1인가구", "미취업", 10, 0.7, List.of(), List.of(), List.of()
        );
    }

    private ScoredCandidate candidate(Long serviceId, String title) {
        WelfareService service = WelfareService.builder()
                .id(serviceId)
                .sourceType(WelfareService.SourceType.YOUTH)
                .sourceId("SRC-" + serviceId)
                .title(title)
                .status(WelfareService.ServiceStatus.ACTIVE)
                .build();
        return ScoredCandidate.builder()
                .service(service)
                .ruleBaseScore(10.0)
                .ruleWeightedScore(12.0)
                .build();
    }
}
