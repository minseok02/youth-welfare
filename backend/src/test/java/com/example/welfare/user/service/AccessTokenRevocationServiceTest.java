package com.example.welfare.user.service;

import com.example.welfare.global.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Date;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccessTokenRevocationServiceTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;
    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private AccessTokenRevocationService accessTokenRevocationService;

    @BeforeEach
    void setUp() {
        accessTokenRevocationService = new AccessTokenRevocationService(redisTemplate, jwtUtil);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("revoke는 access token 원문 대신 SHA-256 기반 Redis key를 저장한다")
    void revokeStoresHashedRedisKey() {
        String accessToken = "header.payload.signature";
        when(jwtUtil.getExpirationAllowExpired(accessToken))
                .thenReturn(new Date(System.currentTimeMillis() + 60_000));

        accessTokenRevocationService.revoke(accessToken);

        String storageKey = accessTokenRevocationService.storageKeyForTest(accessToken);
        assertThat(storageKey).startsWith("access-revoked:");
        assertThat(storageKey).doesNotContain(accessToken);
        verify(valueOperations).set(
                org.mockito.ArgumentMatchers.eq(storageKey),
                org.mockito.ArgumentMatchers.eq("1"),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.eq(TimeUnit.MILLISECONDS)
        );
    }

    @Test
    @DisplayName("isRevoked는 같은 해시 key를 사용해 revoked 여부를 확인한다")
    void isRevokedChecksHashedRedisKey() {
        String accessToken = "header.payload.signature";
        String storageKey = accessTokenRevocationService.storageKeyForTest(accessToken);
        when(redisTemplate.hasKey(storageKey)).thenReturn(true);

        boolean revoked = accessTokenRevocationService.isRevoked(accessToken);

        assertThat(revoked).isTrue();
        verify(redisTemplate).hasKey(storageKey);
    }

    @Test
    @DisplayName("이미 만료된 access token은 revocation key를 저장하지 않는다")
    void revokeSkipsExpiredToken() {
        String accessToken = "expired.token.value";
        when(jwtUtil.getExpirationAllowExpired(accessToken))
                .thenReturn(new Date(System.currentTimeMillis() - 1_000));

        accessTokenRevocationService.revoke(accessToken);

        verify(valueOperations, never()).set(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any()
        );
    }
}
