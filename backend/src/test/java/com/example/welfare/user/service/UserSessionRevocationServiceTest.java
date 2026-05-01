package com.example.welfare.user.service;

import com.example.welfare.global.auth.AuthenticatedUser;
import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.example.welfare.global.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserSessionRevocationServiceTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;
    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private AccessTokenRevocationService accessTokenRevocationService;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private UserSessionRevocationService userSessionRevocationService;

    @BeforeEach
    void setUp() {
        userSessionRevocationService = new UserSessionRevocationService(
                redisTemplate,
                jwtUtil,
                accessTokenRevocationService
        );
        ReflectionTestUtils.setField(userSessionRevocationService, "accessExpiration", 3_600_000L);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("revokeUserSessions는 refresh key를 지우고 access cutoff를 TTL과 함께 기록한다")
    void revokeUserSessionsDeletesRefreshAndWritesCutoff() {
        userSessionRevocationService.revokeUserSessions("user-key-7", 1_777_588_800_000L);

        verify(redisTemplate).delete("refresh:user-key-7");
        verify(valueOperations).set(
                "access-cutoff:user-key-7",
                "1777588800000",
                3_660_000L,
                TimeUnit.MILLISECONDS
        );
    }

    @Test
    @DisplayName("exact token revoke가 있으면 access는 허용되지 않는다")
    void isAccessAllowedReturnsFalseWhenExactTokenRevoked() {
        when(accessTokenRevocationService.isRevoked("access-token")).thenReturn(true);

        boolean allowed = userSessionRevocationService.isAccessAllowed("access-token");

        assertThat(allowed).isFalse();
        verify(jwtUtil, never()).getAuthenticatedUser("access-token");
    }

    @Test
    @DisplayName("cutoff가 없으면 access는 허용된다")
    void isAccessAllowedReturnsTrueWhenNoCutoffExists() {
        when(accessTokenRevocationService.isRevoked("access-token")).thenReturn(false);
        when(jwtUtil.getAuthenticatedUser("access-token")).thenReturn(new AuthenticatedUser(7L, "user-key-7"));
        when(valueOperations.get("access-cutoff:user-key-7")).thenReturn(null);

        boolean allowed = userSessionRevocationService.isAccessAllowed("access-token");

        assertThat(allowed).isTrue();
        verify(jwtUtil, never()).getIssuedAtMillis("access-token");
    }

    @Test
    @DisplayName("issuedAtMillis가 cutoff 이전이면 access는 허용되지 않는다")
    void isAccessAllowedReturnsFalseWhenIssuedAtIsBeforeCutoff() {
        when(accessTokenRevocationService.isRevoked("access-token")).thenReturn(false);
        when(jwtUtil.getAuthenticatedUser("access-token")).thenReturn(new AuthenticatedUser(7L, "user-key-7"));
        when(valueOperations.get("access-cutoff:user-key-7")).thenReturn("2000");
        when(jwtUtil.getIssuedAtMillis("access-token")).thenReturn(2000L);

        boolean allowed = userSessionRevocationService.isAccessAllowed("access-token");

        assertThat(allowed).isFalse();
    }

    @Test
    @DisplayName("issuedAtMillis가 cutoff 이후면 access는 허용된다")
    void isAccessAllowedReturnsTrueWhenIssuedAtIsAfterCutoff() {
        when(accessTokenRevocationService.isRevoked("access-token")).thenReturn(false);
        when(jwtUtil.getAuthenticatedUser("access-token")).thenReturn(new AuthenticatedUser(7L, "user-key-7"));
        when(valueOperations.get("access-cutoff:user-key-7")).thenReturn("2000");
        when(jwtUtil.getIssuedAtMillis("access-token")).thenReturn(2001L);

        boolean allowed = userSessionRevocationService.isAccessAllowed("access-token");

        assertThat(allowed).isTrue();
    }

    @Test
    @DisplayName("iatm이 없는 legacy token은 access를 허용하지 않는다")
    void isAccessAllowedReturnsFalseForLegacyTokenWithoutIatm() {
        when(accessTokenRevocationService.isRevoked("access-token")).thenReturn(false);
        when(jwtUtil.getAuthenticatedUser("access-token")).thenReturn(new AuthenticatedUser(7L, "user-key-7"));
        when(valueOperations.get("access-cutoff:user-key-7")).thenReturn("2000");
        when(jwtUtil.getIssuedAtMillis("access-token"))
                .thenThrow(new CustomException(ErrorCode.INVALID_TOKEN));

        boolean allowed = userSessionRevocationService.isAccessAllowed("access-token");

        assertThat(allowed).isFalse();
    }
}
