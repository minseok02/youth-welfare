package com.example.welfare.support.service;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SupportInquiryRateLimitServiceTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private SupportInquiryRateLimitService supportInquiryRateLimitService;

    @BeforeEach
    void setUp() {
        supportInquiryRateLimitService = new SupportInquiryRateLimitService(redisTemplate, 2, 300);
    }

    @Test
    @DisplayName("첫 문의 요청이면 rate limit 키에 만료시간을 설정한다")
    void checkInquiryLimitSetsExpiryForFirstRequest() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        String key = inquiryKey("fp:test");
        when(valueOperations.increment(key)).thenReturn(1L);

        supportInquiryRateLimitService.checkInquiryLimit("fp:test");

        verify(redisTemplate).expire(key, Duration.ofSeconds(300));
    }

    @Test
    @DisplayName("TTL이 남아 있으면 만료시간을 다시 설정하지 않는다")
    void checkInquiryLimitSkipsExpireWhenTtlExists() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        String key = inquiryKey("fp:test");
        when(valueOperations.increment(key)).thenReturn(2L);
        when(redisTemplate.getExpire(key)).thenReturn(120L);

        supportInquiryRateLimitService.checkInquiryLimit("fp:test");

        verify(redisTemplate, never()).expire(key, Duration.ofSeconds(300));
    }

    @Test
    @DisplayName("문의 제한을 초과하면 429 support rate limit 오류를 반환한다")
    void checkInquiryLimitThrowsWhenLimitExceeded() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        String key = inquiryKey("fp:test");
        when(valueOperations.increment(key)).thenReturn(3L);
        when(redisTemplate.getExpire(key)).thenReturn(120L);

        assertThatThrownBy(() -> supportInquiryRateLimitService.checkInquiryLimit("fp:test"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SUPPORT_RATE_LIMIT_EXCEEDED);
    }

    @Test
    @DisplayName("빈 actor key는 anonymous 버킷으로 정규화한다")
    void buildInquiryRateLimitKeyUsesAnonymousFallback() {
        assertThat(supportInquiryRateLimitService.buildInquiryRateLimitKey(" "))
                .isEqualTo("support:rate-limit:inquiry:anonymous");
    }

    private String inquiryKey(String actorKey) {
        return "support:rate-limit:inquiry:" + RedisKeyHash.sha256Hex(actorKey);
    }
}
