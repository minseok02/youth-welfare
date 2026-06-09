package com.example.welfare.recommend.service;

import com.example.welfare.global.util.RedisKeyHash;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDateTime;
import java.util.Optional;
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
    @DisplayName("userKey 마커가 있으면 recommendedAt token 을 복원한다")
    void findReusableRecommendedAtReturnsTokenWhenMarkerExists() {
        when(valueOperations.get(cacheKey("user-key-1", false)))
                .thenReturn("2026-05-04T12:00:00");

        assertThat(cacheService.findReusableRecommendedAt("user-key-1"))
                .contains(LocalDateTime.of(2026, 5, 4, 12, 0));
    }

    @Test
    @DisplayName("refresh 재사용 마커는 분 단위 TTL로 저장한다")
    void markReusableStoresTtlMarker() {
        cacheService.markReusable("user-key-1", LocalDateTime.of(2026, 5, 4, 12, 0));

        verify(valueOperations).set(cacheKey("user-key-1", false), "2026-05-04T12:00", 15, TimeUnit.MINUTES);
    }

    @Test
    @DisplayName("refresh 재사용 마커는 userKey 기준으로 제거할 수 있다")
    void evictDeletesMarker() {
        cacheService.evict("user-key-1");

        verify(redisTemplate).delete(cacheKey("user-key-1", false));
        verify(redisTemplate).delete("recommend:refresh:user:user-key-1:education-bonus:false");
    }

    @Test
    @DisplayName("refresh 마커 key 는 추천 규칙 플래그 버전을 포함한다")
    void cacheKeyIncludesRecommendationRuleVersion() {
        RecommendationRefreshCacheService enabledCacheService =
                new RecommendationRefreshCacheService(redisTemplate, 15, true);

        assertThat(enabledCacheService.cacheKey("user-key-1"))
                .isEqualTo(cacheKey("user-key-1", true));
    }

    @Test
    @DisplayName("refresh token 형식이 깨져 있으면 마커를 제거하고 empty 를 반환한다")
    void findReusableRecommendedAtEvictsBrokenToken() {
        when(valueOperations.get(cacheKey("user-key-1", false)))
                .thenReturn("broken-token");

        Optional<LocalDateTime> result = cacheService.findReusableRecommendedAt("user-key-1");

        assertThat(result).isEmpty();
        verify(redisTemplate).delete(cacheKey("user-key-1", false));
        verify(redisTemplate).delete("recommend:refresh:user:user-key-1:education-bonus:false");
    }

    private String cacheKey(String userKey, boolean educationBonusEnabled) {
        return "recommend:refresh:user:v2:" + RedisKeyHash.sha256Hex(userKey)
                + ":education-bonus:" + educationBonusEnabled;
    }
}
