package com.example.welfare.user.service;

import com.example.welfare.chat.service.ChatSessionCleanupService;
import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.example.welfare.global.util.JwtUtil;
import com.example.welfare.user.dto.response.TokenResponse;
import com.example.welfare.user.entity.User;
import com.example.welfare.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthTokenServiceTest {

    @Mock private JwtUtil jwtUtil;
    @Mock private RedisTemplate<String, String> redisTemplate;
    @Mock private UserRepository userRepository;
    @Mock private AccessTokenRevocationService accessTokenRevocationService;
    @Mock private ChatSessionCleanupService chatSessionCleanupService;
    @Mock private ValueOperations<String, String> valueOperations;

    private AuthTokenService authTokenService;

    @BeforeEach
    void setUp() {
        authTokenService = new AuthTokenService(
                jwtUtil,
                redisTemplate,
                userRepository,
                accessTokenRevocationService,
                chatSessionCleanupService
        );
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("로그아웃은 refresh token을 지우고 현재 access token을 revoke한다")
    void logoutByUserKeyDeletesRefreshTokenAndRevokesAccessToken() {
        authTokenService.logoutByUserKey("user-key-7", "access-token-value");

        verify(redisTemplate).delete("refresh:user-key-7");
        verify(accessTokenRevocationService).revoke("access-token-value");
        verify(chatSessionCleanupService).deleteAllByUserKey("user-key-7");
    }

    @Test
    @DisplayName("refresh는 저장된 refresh token이 일치하면 새 토큰을 발급한다")
    void refreshRotatesRefreshToken() {
        User user = User.builder()
                .id(7L)
                .userKey("user-key-7")
                .email("user@example.com")
                .passwordHash("hash")
                .build();
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(valueOperations.get("refresh:user-key-7")).thenReturn("refresh-token");
        when(jwtUtil.getSubject("refresh-token")).thenReturn("user-key-7");
        when(jwtUtil.getUserId("refresh-token")).thenReturn(7L);
        when(jwtUtil.generateAccessToken("user-key-7", 7L, List.of("ROLE_USER"))).thenReturn("new-access");
        when(jwtUtil.generateRefreshToken("user-key-7", 7L)).thenReturn("new-refresh");

        TokenResponse response = authTokenService.refresh("refresh-token", email -> List.of("ROLE_USER"));

        assertThat(response.getAccessToken()).isEqualTo("new-access");
        assertThat(response.getRefreshToken()).isEqualTo("new-refresh");
        verify(valueOperations).set("refresh:user-key-7", "new-refresh", 7, java.util.concurrent.TimeUnit.DAYS);
    }

    @Test
    @DisplayName("refresh는 저장된 refresh token과 다르면 reuse로 간주하고 거부한다")
    void refreshRejectsReusedRefreshToken() {
        User user = User.builder()
                .id(7L)
                .userKey("user-key-7")
                .email("user@example.com")
                .passwordHash("hash")
                .build();
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(valueOperations.get("refresh:user-key-7")).thenReturn("different-refresh-token");
        when(jwtUtil.getSubject("refresh-token")).thenReturn("user-key-7");
        when(jwtUtil.getUserId("refresh-token")).thenReturn(7L);

        assertThatThrownBy(() -> authTokenService.refresh("refresh-token", email -> List.of("ROLE_USER")))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.REUSED_REFRESH_TOKEN);

        verify(redisTemplate).delete("refresh:user-key-7");
        verify(jwtUtil, never()).generateAccessToken(any(), any(), any());
    }
}
