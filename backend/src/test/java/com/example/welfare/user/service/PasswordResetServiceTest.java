package com.example.welfare.user.service;

import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.example.welfare.global.util.RedisKeyHash;
import com.example.welfare.notification.gateway.EmailClient;
import com.example.welfare.user.entity.AuthUser;
import com.example.welfare.user.entity.User;
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
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    @Mock private AuthIdentityReadService authIdentityReadService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private RedisTemplate<String, String> redisTemplate;
    @Mock private EmailClient emailClient;
    @Mock private UserCoreSyncService userCoreSyncService;
    @Mock private ActiveUserReadService activeUserReadService;
    @Mock private UserNotificationReadService userNotificationReadService;
    @Mock private UserSessionRevocationService userSessionRevocationService;
    @Mock private ValueOperations<String, String> valueOperations;

    private PasswordResetService passwordResetService;

    @BeforeEach
    void setUp() {
        passwordResetService = new PasswordResetService(
                authIdentityReadService,
                passwordEncoder,
                redisTemplate,
                emailClient,
                userCoreSyncService,
                activeUserReadService,
                userNotificationReadService,
                userSessionRevocationService
        );
        ReflectionTestUtils.setField(passwordResetService, "passwordResetExpirationMinutes", 30L);
        ReflectionTestUtils.setField(passwordResetService, "passwordResetRequestCooldownSeconds", 60L);
        ReflectionTestUtils.setField(passwordResetService, "appBaseUrl", "http://localhost:5173");
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("비밀번호 재설정 요청은 활성 사용자에게 토큰을 저장하고 메일을 발송한다")
    void requestPasswordResetStoresTokenAndSendsMail() {
        AuthUser authUser = AuthUser.builder()
                .userKey("user-key-7")
                .isActive(true)
                .build();
        User user = User.builder()
                .id(7L)
                .userKey("user-key-7")
                .email("legacy@example.com")
                .passwordHash("hash")
                .build();
        when(authIdentityReadService.findByEmail("user@example.com"))
                .thenReturn(Optional.of(authUser));
        when(activeUserReadService.findOptionalActiveUserByUserKey("user-key-7")).thenReturn(Optional.of(user));
        when(userNotificationReadService.getNotificationEmailByUserKey("user-key-7")).thenReturn("pii@example.com");
        when(valueOperations.setIfAbsent(any(String.class), eq("1"), eq(60L), eq(TimeUnit.SECONDS)))
                .thenReturn(true);
        when(valueOperations.get(passwordResetUserKey("user-key-7"))).thenReturn(null);
        when(valueOperations.get("password-reset:user:user-key-7")).thenReturn(null);
        when(emailClient.send(eq("pii@example.com"), eq("[청년복지] 비밀번호 재설정 안내"), any(String.class)))
                .thenReturn(true);

        passwordResetService.requestPasswordReset("USER@example.com");

        verify(valueOperations).set(
                argThat(key -> key.matches("password-reset:[0-9a-f]{64}")),
                eq("user-key-7"),
                eq(30L),
                eq(java.util.concurrent.TimeUnit.MINUTES)
        );
        verify(valueOperations).set(
                eq(passwordResetUserKey("user-key-7")),
                argThat(value -> value.matches("[0-9a-f]{64}")),
                eq(30L),
                eq(java.util.concurrent.TimeUnit.MINUTES)
        );
        verify(redisTemplate).delete("password-reset:user:user-key-7");
        verify(emailClient).send(eq("pii@example.com"), eq("[청년복지] 비밀번호 재설정 안내"), org.mockito.ArgumentMatchers.contains("/reset-password#token="));
    }

    @Test
    @DisplayName("비밀번호 재설정 요청은 cooldown 동안 반복 메일을 보내지 않는다")
    void requestPasswordResetSkipsRepeatedRequestsDuringCooldown() {
        AuthUser authUser = AuthUser.builder()
                .userKey("user-key-7")
                .isActive(true)
                .build();
        User user = User.builder()
                .id(7L)
                .userKey("user-key-7")
                .email("legacy@example.com")
                .passwordHash("hash")
                .build();
        when(authIdentityReadService.findByEmail("user@example.com"))
                .thenReturn(Optional.of(authUser));
        when(activeUserReadService.findOptionalActiveUserByUserKey("user-key-7")).thenReturn(Optional.of(user));
        when(valueOperations.setIfAbsent(any(String.class), eq("1"), eq(60L), eq(TimeUnit.SECONDS)))
                .thenReturn(false);

        passwordResetService.requestPasswordReset("user@example.com");

        verify(emailClient, never()).send(any(), any(), any());
        verify(valueOperations, never()).set(eq(passwordResetUserKey("user-key-7")), any(String.class), any(Long.class), any());
    }

    @Test
    @DisplayName("비밀번호 재설정 요청은 없는 이메일이어도 동일 성공으로 끝나며 메일을 보내지 않는다")
    void requestPasswordResetIgnoresUnknownEmail() {
        when(authIdentityReadService.findByEmail("missing@example.com"))
                .thenReturn(Optional.empty());

        passwordResetService.requestPasswordReset("missing@example.com");

        verify(emailClient, never()).send(any(), any(), any());
        verify(valueOperations, never()).set(any(), any(), any(Long.class), any());
    }

    @Test
    @DisplayName("비밀번호 재설정 요청은 user_pii 이메일이 없으면 발송 실패로 처리한다")
    void requestPasswordResetFailsWhenEncryptedEmailMissing() {
        AuthUser authUser = AuthUser.builder()
                .userKey("user-key-7")
                .isActive(true)
                .build();
        User user = User.builder()
                .id(7L)
                .userKey("user-key-7")
                .email("legacy@example.com")
                .passwordHash("hash")
                .build();
        when(authIdentityReadService.findByEmail("user@example.com"))
                .thenReturn(Optional.of(authUser));
        when(activeUserReadService.findOptionalActiveUserByUserKey("user-key-7")).thenReturn(Optional.of(user));
        when(valueOperations.setIfAbsent(any(String.class), eq("1"), eq(60L), eq(TimeUnit.SECONDS)))
                .thenReturn(true);
        when(userNotificationReadService.getNotificationEmailByUserKey("user-key-7"))
                .thenThrow(new CustomException(ErrorCode.PASSWORD_RESET_EMAIL_SEND_FAILED));

        assertThatThrownBy(() -> passwordResetService.requestPasswordReset("user@example.com"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PASSWORD_RESET_EMAIL_SEND_FAILED);

        verify(valueOperations, never()).set(any(), any(), any(Long.class), any());
        verify(emailClient, never()).send(any(), any(), any());
    }

    @Test
    @DisplayName("비밀번호 재설정 확인은 비밀번호를 바꾸고 토큰과 사용자 세션을 폐기한다")
    void confirmPasswordResetUpdatesPasswordAndClearsTokens() {
        User user = User.builder()
                .id(7L)
                .userKey("user-key-7")
                .email("user@example.com")
                .passwordHash("old-hash")
                .loginFailCount(3)
                .build();
        String resetTokenHash = OpaqueTokenHash.sha256Hex("reset-token");
        when(valueOperations.get("password-reset:" + resetTokenHash)).thenReturn("user-key-7");
        when(activeUserReadService.findOptionalActiveUserByUserKey("user-key-7")).thenReturn(Optional.of(user));
        when(valueOperations.get(passwordResetUserKey("user-key-7"))).thenReturn(resetTokenHash);
        when(passwordEncoder.encode("new-password123")).thenReturn("encoded-password");

        passwordResetService.confirmPasswordReset("reset-token", "new-password123");

        verify(userCoreSyncService).syncFromUser(user);
        verify(redisTemplate).delete("password-reset:" + resetTokenHash);
        verify(redisTemplate).delete("password-reset:reset-token");
        verify(redisTemplate).delete(passwordResetUserKey("user-key-7"));
        verify(redisTemplate).delete("password-reset:user:user-key-7");
        verify(userSessionRevocationService).revokeUserSessions(eq("user-key-7"), any(Long.class));
        assertThat(user.getPasswordHash()).isEqualTo("encoded-password");
        assertThat(user.getLoginFailCount()).isZero();
    }

    @Test
    @DisplayName("비밀번호 재설정 확인은 legacy raw token 저장값도 허용하고 폐기한다")
    void confirmPasswordResetAcceptsLegacyRawTokenStorage() {
        User user = User.builder()
                .id(7L)
                .userKey("user-key-7")
                .email("user@example.com")
                .passwordHash("old-hash")
                .build();
        when(valueOperations.get("password-reset:" + OpaqueTokenHash.sha256Hex("legacy-token"))).thenReturn(null);
        when(valueOperations.get("password-reset:legacy-token")).thenReturn("user-key-7");
        when(activeUserReadService.findOptionalActiveUserByUserKey("user-key-7")).thenReturn(Optional.of(user));
        when(valueOperations.get(passwordResetUserKey("user-key-7"))).thenReturn(null);
        when(valueOperations.get("password-reset:user:user-key-7")).thenReturn("legacy-token");
        when(passwordEncoder.encode("new-password123")).thenReturn("encoded-password");

        passwordResetService.confirmPasswordReset("legacy-token", "new-password123");

        verify(userCoreSyncService).syncFromUser(user);
        verify(redisTemplate).delete("password-reset:" + OpaqueTokenHash.sha256Hex("legacy-token"));
        verify(redisTemplate).delete("password-reset:legacy-token");
        verify(redisTemplate).delete(passwordResetUserKey("user-key-7"));
        verify(redisTemplate).delete("password-reset:user:user-key-7");
    }

    @Test
    @DisplayName("비밀번호 재설정 확인은 최신 토큰이 아니면 거부한다")
    void confirmPasswordResetRejectsStaleToken() {
        User user = User.builder()
                .id(7L)
                .userKey("user-key-7")
                .email("user@example.com")
                .passwordHash("old-hash")
                .build();
        String oldTokenHash = OpaqueTokenHash.sha256Hex("old-token");
        when(valueOperations.get("password-reset:" + oldTokenHash)).thenReturn("user-key-7");
        when(activeUserReadService.findOptionalActiveUserByUserKey("user-key-7")).thenReturn(Optional.of(user));
        when(valueOperations.get(passwordResetUserKey("user-key-7"))).thenReturn(OpaqueTokenHash.sha256Hex("new-token"));

        assertThatThrownBy(() -> passwordResetService.confirmPasswordReset("old-token", "new-password123"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PASSWORD_RESET_TOKEN_INVALID);

        verify(passwordEncoder, never()).encode(any());
    }

    private String passwordResetUserKey(String userKey) {
        return "password-reset:user:v2:" + RedisKeyHash.sha256Hex(userKey);
    }
}
