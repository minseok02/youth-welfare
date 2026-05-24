package com.example.welfare.user.service;

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
class AuthRateLimitServiceTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private AuthRateLimitService authRateLimitService;

    @BeforeEach
    void setUp() {
        authRateLimitService = new AuthRateLimitService(redisTemplate, 10, 60, 2, 300);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("이메일 인증 발송 첫 요청이면 fingerprint rate limit 키에 만료시간을 설정한다")
    void checkEmailVerificationSendLimitSetsExpiryForFirstRequest() {
        when(valueOperations.increment("auth:rate-limit:email-verification-send:fp:test")).thenReturn(1L);

        authRateLimitService.checkEmailVerificationSendLimit("fp:test");

        verify(redisTemplate).expire("auth:rate-limit:email-verification-send:fp:test", Duration.ofSeconds(300));
    }

    @Test
    @DisplayName("이메일 인증 발송 키에 TTL이 남아 있으면 만료시간을 다시 설정하지 않는다")
    void checkEmailVerificationSendLimitSkipsExpireWhenTtlExists() {
        when(valueOperations.increment("auth:rate-limit:email-verification-send:fp:test")).thenReturn(2L);
        when(redisTemplate.getExpire("auth:rate-limit:email-verification-send:fp:test")).thenReturn(120L);

        authRateLimitService.checkEmailVerificationSendLimit("fp:test");

        verify(redisTemplate, never()).expire("auth:rate-limit:email-verification-send:fp:test", Duration.ofSeconds(300));
    }

    @Test
    @DisplayName("이메일 인증 발송 제한을 초과하면 429 auth rate limit 오류를 반환한다")
    void checkEmailVerificationSendLimitThrowsWhenLimitExceeded() {
        when(valueOperations.increment("auth:rate-limit:email-verification-send:fp:test")).thenReturn(3L);
        when(redisTemplate.getExpire("auth:rate-limit:email-verification-send:fp:test")).thenReturn(100L);

        assertThatThrownBy(() -> authRateLimitService.checkEmailVerificationSendLimit("fp:test"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AUTH_RATE_LIMIT_EXCEEDED);
    }
}
