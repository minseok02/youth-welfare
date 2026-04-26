package com.example.welfare.user.service;

import com.example.welfare.chat.service.ChatSessionCleanupService;
import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.example.welfare.global.util.JwtUtil;
import com.example.welfare.notification.gateway.EmailClient;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private RedisTemplate<String, String> redisTemplate;
    @Mock
    private ChatSessionCleanupService chatSessionCleanupService;
    @Mock
    private EmailClient emailClient;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                userRepository,
                passwordEncoder,
                jwtUtil,
                redisTemplate,
                chatSessionCleanupService,
                emailClient
        );
        ReflectionTestUtils.setField(authService, "passwordResetExpirationMinutes", 30L);
        ReflectionTestUtils.setField(authService, "appBaseUrl", "http://localhost:5173");
        ReflectionTestUtils.setField(authService, "adminEmailsProperty", "");
        authService.initAdminEmails();
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("비밀번호 재설정 요청은 활성 사용자에게 토큰을 저장하고 메일을 발송한다")
    void requestPasswordResetStoresTokenAndSendsMail() {
        User user = User.builder()
                .id(7L)
                .email("user@example.com")
                .passwordHash("hash")
                .build();
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(valueOperations.get("password-reset:user:7")).thenReturn(null);
        when(emailClient.send(eq("user@example.com"), eq("[청년복지] 비밀번호 재설정 안내"), any(String.class)))
                .thenReturn(true);

        authService.requestPasswordReset("USER@example.com");

        verify(valueOperations).set(eq("password-reset:user:7"), any(String.class), eq(30L), eq(java.util.concurrent.TimeUnit.MINUTES));
        verify(valueOperations).set(org.mockito.ArgumentMatchers.startsWith("password-reset:"), eq("7"), eq(30L), eq(java.util.concurrent.TimeUnit.MINUTES));
        verify(emailClient).send(eq("user@example.com"), eq("[청년복지] 비밀번호 재설정 안내"), org.mockito.ArgumentMatchers.contains("/reset-password?token="));
    }

    @Test
    @DisplayName("비밀번호 재설정 요청은 없는 이메일이어도 동일 성공으로 끝나며 메일을 보내지 않는다")
    void requestPasswordResetIgnoresUnknownEmail() {
        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        authService.requestPasswordReset("missing@example.com");

        verify(emailClient, never()).send(any(), any(), any());
        verify(valueOperations, never()).set(any(), any(), any(Long.class), any());
    }

    @Test
    @DisplayName("비밀번호 재설정 확인은 비밀번호를 바꾸고 토큰과 refresh 토큰을 폐기한다")
    void confirmPasswordResetUpdatesPasswordAndClearsTokens() {
        User user = User.builder()
                .id(7L)
                .email("user@example.com")
                .passwordHash("old-hash")
                .loginFailCount(3)
                .build();
        when(valueOperations.get("password-reset:reset-token")).thenReturn("7");
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(valueOperations.get("password-reset:user:7")).thenReturn("reset-token");
        when(passwordEncoder.encode("new-password123")).thenReturn("encoded-password");

        authService.confirmPasswordReset("reset-token", "new-password123");

        verify(redisTemplate).delete("password-reset:reset-token");
        verify(redisTemplate).delete("password-reset:user:7");
        verify(redisTemplate).delete("refresh:7");
        org.assertj.core.api.Assertions.assertThat(user.getPasswordHash()).isEqualTo("encoded-password");
        org.assertj.core.api.Assertions.assertThat(user.getLoginFailCount()).isZero();
    }

    @Test
    @DisplayName("비밀번호 재설정 확인은 최신 토큰이 아니면 거부한다")
    void confirmPasswordResetRejectsStaleToken() {
        User user = User.builder()
                .id(7L)
                .email("user@example.com")
                .passwordHash("old-hash")
                .build();
        when(valueOperations.get("password-reset:old-token")).thenReturn("7");
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(valueOperations.get("password-reset:user:7")).thenReturn("new-token");

        assertThatThrownBy(() -> authService.confirmPasswordReset("old-token", "new-password123"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PASSWORD_RESET_TOKEN_INVALID);

        verify(passwordEncoder, never()).encode(any());
    }
}
