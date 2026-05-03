package com.example.welfare.recommend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecommendationRefreshCacheServiceTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private RecommendationRefreshCacheService cacheService;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        cacheService = new RecommendationRefreshCacheService(redisTemplate, 15, false);
    }

    @Test
    @DisplayName("userKey 마커가 있으면 refresh 결과를 재사용할 수 있다")
    void canReuseReturnsTrueWhenMarkerExists() {
        when(redisTemplate.hasKey("recommend:refresh:user:user-key-1:education-bonus:false")).thenReturn(true);

        assertThat(cacheService.canReuse("user-key-1")).isTrue();
    }

    @Test
    @DisplayName("refresh 재사용 마커는 분 단위 TTL로 저장한다")
    void markReusableStoresTtlMarker() {
        cacheService.markReusable("user-key-1");

        verify(valueOperations).set("recommend:refresh:user:user-key-1:education-bonus:false", "1", 15, TimeUnit.MINUTES);
    }

    @Test
    @DisplayName("refresh 재사용 마커는 userKey 기준으로 제거할 수 있다")
    void evictDeletesMarker() {
        cacheService.evict("user-key-1");

        verify(redisTemplate).delete("recommend:refresh:user:user-key-1:education-bonus:false");
    }

    @Test
    @DisplayName("refresh 마커 key 는 추천 규칙 플래그 버전을 포함한다")
    void cacheKeyIncludesRecommendationRuleVersion() {
        RecommendationRefreshCacheService enabledCacheService =
                new RecommendationRefreshCacheService(redisTemplate, 15, true);

        assertThat(enabledCacheService.cacheKey("user-key-1"))
                .isEqualTo("recommend:refresh:user:user-key-1:education-bonus:true");
    }
}
