package com.example.welfare.chat.service;

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
class ChatRateLimitServiceTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private ChatRateLimitService chatRateLimitService;

    @BeforeEach
    void setUp() {
        chatRateLimitService = new ChatRateLimitService(redisTemplate, 2, 60);
    }

    @Test
    @DisplayName("첫 요청이면 rate limit 키에 만료시간을 설정한다")
    void checkMessageSendLimitSetsExpiryForFirstRequest() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        String key = messageKey(1L);
        when(valueOperations.increment(key)).thenReturn(1L);

        chatRateLimitService.checkMessageSendLimit(1L);

        verify(redisTemplate).expire(key, Duration.ofSeconds(60));
    }

    @Test
    @DisplayName("이미 TTL이 있는 키는 요청 증가만 허용한다")
    void checkMessageSendLimitSkipsExpireWhenTtlExists() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        String key = messageKey(1L);
        when(valueOperations.increment(key)).thenReturn(2L);
        when(redisTemplate.getExpire(key)).thenReturn(30L);

        chatRateLimitService.checkMessageSendLimit(1L);

        verify(redisTemplate, never()).expire(key, Duration.ofSeconds(60));
    }

    @Test
    @DisplayName("제한 횟수를 초과하면 429 챗봇 rate limit 오류를 반환한다")
    void checkMessageSendLimitThrowsWhenLimitExceeded() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        String key = messageKey(1L);
        when(valueOperations.increment(key)).thenReturn(3L);
        when(redisTemplate.getExpire(key)).thenReturn(20L);

        assertThatThrownBy(() -> chatRateLimitService.checkMessageSendLimit(1L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CHAT_RATE_LIMIT_EXCEEDED);
    }

    @Test
    @DisplayName("사용자 식별자가 없으면 공유 rate limit 키를 만들지 않고 invalid input을 반환한다")
    void checkMessageSendLimitRejectsNullUserId() {
        assertThatThrownBy(() -> chatRateLimitService.checkMessageSendLimit(null))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);
        verify(redisTemplate, never()).opsForValue();
    }

    private String messageKey(Long userId) {
        return "chat:rate-limit:message:" + RedisKeyHash.sha256Hex(String.valueOf(userId));
    }
}
