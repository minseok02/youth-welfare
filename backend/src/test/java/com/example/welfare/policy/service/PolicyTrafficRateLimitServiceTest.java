package com.example.welfare.policy.service;

import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
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
        policyTrafficRateLimitService = new PolicyTrafficRateLimitService(redisTemplate, 2, 60, 2, 60);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("첫 검색 요청이면 rate limit 키에 만료시간을 설정한다")
    void checkSearchLimitSetsExpiryForFirstRequest() {
        when(valueOperations.increment("policy:rate-limit:search:fp:test")).thenReturn(1L);

        policyTrafficRateLimitService.checkSearchLimit("fp:test");

        verify(redisTemplate).expire("policy:rate-limit:search:fp:test", Duration.ofSeconds(60));
    }

    @Test
    @DisplayName("상세 조회 키에 TTL이 남아 있으면 만료시간을 다시 설정하지 않는다")
    void checkDetailLimitSkipsExpireWhenTtlExists() {
        when(valueOperations.increment("policy:rate-limit:detail:user:test:99")).thenReturn(2L);
        when(redisTemplate.getExpire("policy:rate-limit:detail:user:test:99")).thenReturn(30L);

        policyTrafficRateLimitService.checkDetailLimit("user:test", 99L);

        verify(redisTemplate, never()).expire("policy:rate-limit:detail:user:test:99", Duration.ofSeconds(60));
    }

    @Test
    @DisplayName("검색 제한을 초과하면 429 정책 rate limit 오류를 반환한다")
    void checkSearchLimitThrowsWhenLimitExceeded() {
        when(valueOperations.increment("policy:rate-limit:search:fp:test")).thenReturn(3L);
        when(redisTemplate.getExpire("policy:rate-limit:search:fp:test")).thenReturn(20L);

        assertThatThrownBy(() -> policyTrafficRateLimitService.checkSearchLimit("fp:test"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.POLICY_RATE_LIMIT_EXCEEDED);
    }

    @Test
    @DisplayName("상세 조회 제한을 초과하면 429 정책 rate limit 오류를 반환한다")
    void checkDetailLimitThrowsWhenLimitExceeded() {
        when(valueOperations.increment("policy:rate-limit:detail:fp:test:11")).thenReturn(3L);
        when(redisTemplate.getExpire("policy:rate-limit:detail:fp:test:11")).thenReturn(15L);

        assertThatThrownBy(() -> policyTrafficRateLimitService.checkDetailLimit("fp:test", 11L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.POLICY_RATE_LIMIT_EXCEEDED);
    }
}
