package com.example.welfare.notification.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationUnsubscribeTokenServiceTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private NotificationUnsubscribeTokenService notificationUnsubscribeTokenService;

    @BeforeEach
    void setUp() {
        notificationUnsubscribeTokenService = new NotificationUnsubscribeTokenService(redisTemplate);
        ReflectionTestUtils.setField(notificationUnsubscribeTokenService, "unsubscribeTokenExpirationMillis", 2_592_000_000L);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("unsubscribe token은 원문을 저장하지 않고 SHA-256 기반 Redis key로 저장한다")
    void issueTokenStoresOnlyHashedKey() {
        String opaqueToken = notificationUnsubscribeTokenService.issueToken("user-key-7");

        String storageKey = notificationUnsubscribeTokenService.storageKeyForTest(opaqueToken);
        assertThat(storageKey).startsWith("notification:unsubscribe:");
        assertThat(storageKey).doesNotContain(opaqueToken);
        verify(valueOperations).set(eq(storageKey), eq("user-key-7"), eq(2_592_000_000L), eq(TimeUnit.MILLISECONDS));
    }

    @Test
    @DisplayName("유효한 unsubscribe token은 userKey를 돌려주고 재사용되지 않게 삭제한다")
    void consumeUserKeyReturnsUserKeyAndDeletesToken() {
        String opaqueToken = "opaque-token";
        String storageKey = notificationUnsubscribeTokenService.storageKeyForTest(opaqueToken);
        when(valueOperations.get(storageKey)).thenReturn("user-key-7");

        Optional<String> userKey = notificationUnsubscribeTokenService.consumeUserKey(opaqueToken);

        assertThat(userKey).contains("user-key-7");
        verify(redisTemplate).delete(storageKey);
    }

    @Test
    @DisplayName("잘못된 unsubscribe token은 빈 결과를 반환하고 삭제하지 않는다")
    void consumeUserKeyReturnsEmptyForUnknownToken() {
        when(valueOperations.get(anyString())).thenReturn(null);

        Optional<String> userKey = notificationUnsubscribeTokenService.consumeUserKey("unknown-token");

        assertThat(userKey).isEmpty();
        verify(redisTemplate, never()).delete(anyString());
    }
}
