package com.example.welfare.policy.service;

import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.example.welfare.global.util.RedisKeyHash;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PolicyTrafficRateLimitServiceTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private PolicyTrafficRateLimitService policyTrafficRateLimitService;

    @BeforeEach
    void setUp() {
        policyTrafficRateLimitService = new PolicyTrafficRateLimitService(
                redisTemplate,
                2, 60,
                4, 60,
                3, 60,
                2, 60,
                5, 60,
                6, 60,
                2, 300
        );
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("첫 검색 요청이면 rate limit 키에 만료시간을 설정한다")
    void checkSearchLimitSetsExpiryForFirstRequest() {
        when(valueOperations.increment(key("policy:rate-limit:search:", "fp:test"))).thenReturn(1L);

        policyTrafficRateLimitService.checkSearchLimit("fp:test");

        verify(redisTemplate).expire(key("policy:rate-limit:search:", "fp:test"), Duration.ofSeconds(60));
    }

    @Test
    @DisplayName("상세 조회 키에 TTL이 남아 있으면 만료시간을 다시 설정하지 않는다")
    void checkDetailLimitSkipsExpireWhenTtlExists() {
        String key = key("policy:rate-limit:detail:", "user:test") + ":99";
        when(valueOperations.increment(key)).thenReturn(2L);
        when(redisTemplate.getExpire(key)).thenReturn(30L);

        policyTrafficRateLimitService.checkDetailLimit("user:test", 99L);

        verify(redisTemplate, never()).expire(key, Duration.ofSeconds(60));
    }

    @Test
    @DisplayName("검색 제한을 초과하면 429 정책 rate limit 오류를 반환한다")
    void checkSearchLimitThrowsWhenLimitExceeded() {
        when(valueOperations.increment(key("policy:rate-limit:search:", "fp:test"))).thenReturn(3L);
        when(redisTemplate.getExpire(key("policy:rate-limit:search:", "fp:test"))).thenReturn(20L);

        assertThatThrownBy(() -> policyTrafficRateLimitService.checkSearchLimit("fp:test"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.POLICY_RATE_LIMIT_EXCEEDED);
    }

    @Test
    @DisplayName("자동완성 요청은 검색과 다른 rate limit 키를 사용한다")
    void checkSuggestionLimitUsesDedicatedBucket() {
        when(valueOperations.increment(key("policy:rate-limit:suggestion:", "fp:test"))).thenReturn(1L);

        policyTrafficRateLimitService.checkSuggestionLimit("fp:test");

        verify(redisTemplate).expire(key("policy:rate-limit:suggestion:", "fp:test"), Duration.ofSeconds(60));
        verify(redisTemplate, never()).expire(key("policy:rate-limit:search:", "fp:test"), Duration.ofSeconds(60));
    }

    @Test
    @DisplayName("인기 검색어 요청은 전용 rate limit 버킷을 사용한다")
    void checkTrendingLimitUsesDedicatedBucket() {
        when(valueOperations.increment(key("policy:rate-limit:trending:", "user:test"))).thenReturn(1L);

        policyTrafficRateLimitService.checkTrendingLimit("user:test");

        verify(redisTemplate).expire(key("policy:rate-limit:trending:", "user:test"), Duration.ofSeconds(60));
    }

    @Test
    @DisplayName("상세 조회 제한을 초과하면 429 정책 rate limit 오류를 반환한다")
    void checkDetailLimitThrowsWhenLimitExceeded() {
        String key = key("policy:rate-limit:detail:", "fp:test") + ":11";
        when(valueOperations.increment(key)).thenReturn(3L);
        when(redisTemplate.getExpire(key)).thenReturn(15L);

        assertThatThrownBy(() -> policyTrafficRateLimitService.checkDetailLimit("fp:test", 11L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.POLICY_RATE_LIMIT_EXCEEDED);
    }

    @Test
    @DisplayName("목록 조회 요청은 전용 rate limit 버킷을 사용한다")
    void checkListLimitUsesDedicatedBucket() {
        when(valueOperations.increment(key("policy:rate-limit:list:", "fp:test"))).thenReturn(1L);

        policyTrafficRateLimitService.checkListLimit("fp:test");

        verify(redisTemplate).expire(key("policy:rate-limit:list:", "fp:test"), Duration.ofSeconds(60));
    }

    @Test
    @DisplayName("랭킹 조회 요청은 전용 rate limit 버킷을 사용한다")
    void checkRankingLimitUsesDedicatedBucket() {
        when(valueOperations.increment(key("policy:rate-limit:ranking:", "fp:test"))).thenReturn(1L);

        policyTrafficRateLimitService.checkRankingLimit("fp:test");

        verify(redisTemplate).expire(key("policy:rate-limit:ranking:", "fp:test"), Duration.ofSeconds(60));
    }

    @Test
    @DisplayName("정책 오류제보 제한을 초과하면 429 정책 rate limit 오류를 반환한다")
    void checkErrorReportLimitThrowsWhenLimitExceeded() {
        String key = key("policy:rate-limit:error-report:", "user:test") + ":11";
        when(valueOperations.increment(key)).thenReturn(3L);
        when(redisTemplate.getExpire(key)).thenReturn(120L);

        assertThatThrownBy(() -> policyTrafficRateLimitService.checkErrorReportLimit("user:test", 11L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.POLICY_RATE_LIMIT_EXCEEDED);
    }

    private String key(String prefix, String actorKey) {
        return prefix + RedisKeyHash.sha256Hex(actorKey);
    }
}
