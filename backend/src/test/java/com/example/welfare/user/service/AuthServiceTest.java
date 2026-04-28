package com.example.welfare.user.service;

import com.example.welfare.chat.service.ChatSessionCleanupService;
import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.example.welfare.global.util.AesEncryptUtil;
import com.example.welfare.global.util.JwtUtil;
import com.example.welfare.notification.gateway.EmailClient;
import com.example.welfare.user.entity.AuthUser;
import com.example.welfare.user.entity.User;
import com.example.welfare.user.repository.AuthUserRepository;
import com.example.welfare.user.repository.UserPiiReadModel;
import com.example.welfare.user.repository.UserPiiReadWriteRepository;
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
    private AuthUserRepository authUserRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private UserPiiReadWriteRepository userPiiReadWriteRepository;
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
    private UserCoreSyncService userCoreSyncService;
    @Mock
    private AesEncryptUtil aesEncryptUtil;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                authUserRepository,
                userRepository,
                userPiiReadWriteRepository,
                passwordEncoder,
                jwtUtil,
                redisTemplate,
                chatSessionCleanupService,
                emailClient,
                userCoreSyncService,
                aesEncryptUtil
        );
        ReflectionTestUtils.setField(authService, "passwordResetExpirationMinutes", 30L);
        ReflectionTestUtils.setField(authService, "appBaseUrl", "http://localhost:5173");
        ReflectionTestUtils.setField(authService, "adminEmailsProperty", "");
        authService.initAdminEmails();
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("이메일 중복확인은 대소문자를 무시한 auth_users lookup hash 기준으로 판단한다")
    void checkEmailAvailabilityUsesLookupHash() {
        when(authUserRepository.existsByEmailLookupHash("b4c9a289323b21a01c3e940f150eb9b8c542587f1abfd8f0e1cc1ffc5e475514"))
                .thenReturn(true);

        org.assertj.core.api.Assertions.assertThat(authService.checkEmailAvailability(" USER@example.com ").available())
                .isFalse();
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
        when(authUserRepository.findByEmailLookupHash("b4c9a289323b21a01c3e940f150eb9b8c542587f1abfd8f0e1cc1ffc5e475514"))
                .thenReturn(Optional.of(authUser));
        when(userRepository.findByUserKey("user-key-7")).thenReturn(Optional.of(user));
        when(userPiiReadWriteRepository.findByUserKey("user-key-7"))
                .thenReturn(Optional.of(new UserPiiReadModel("user-key-7", "encrypted-email", null, null, null)));
        when(aesEncryptUtil.decrypt("encrypted-email")).thenReturn("pii@example.com");
        when(valueOperations.get("password-reset:user:user-key-7")).thenReturn(null);
        when(emailClient.send(eq("pii@example.com"), eq("[청년복지] 비밀번호 재설정 안내"), any(String.class)))
                .thenReturn(true);

        authService.requestPasswordReset("USER@example.com");

        verify(valueOperations).set(eq("password-reset:user:user-key-7"), any(String.class), eq(30L), eq(java.util.concurrent.TimeUnit.MINUTES));
        verify(valueOperations).set(org.mockito.ArgumentMatchers.startsWith("password-reset:"), eq("user-key-7"), eq(30L), eq(java.util.concurrent.TimeUnit.MINUTES));
        verify(emailClient).send(eq("pii@example.com"), eq("[청년복지] 비밀번호 재설정 안내"), org.mockito.ArgumentMatchers.contains("/reset-password?token="));
    }

    @Test
    @DisplayName("비밀번호 재설정 요청은 없는 이메일이어도 동일 성공으로 끝나며 메일을 보내지 않는다")
    void requestPasswordResetIgnoresUnknownEmail() {
        when(authUserRepository.findByEmailLookupHash("62065901fb8d47d884b2737489920faedfdf935aa5cd9e0c34cad99b99a6a91b"))
                .thenReturn(Optional.empty());

        authService.requestPasswordReset("missing@example.com");

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
        when(authUserRepository.findByEmailLookupHash("b4c9a289323b21a01c3e940f150eb9b8c542587f1abfd8f0e1cc1ffc5e475514"))
                .thenReturn(Optional.of(authUser));
        when(userRepository.findByUserKey("user-key-7")).thenReturn(Optional.of(user));
        when(userPiiReadWriteRepository.findByUserKey("user-key-7")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.requestPasswordReset("user@example.com"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PASSWORD_RESET_EMAIL_SEND_FAILED);

        verify(valueOperations, never()).set(any(), any(), any(Long.class), any());
        verify(emailClient, never()).send(any(), any(), any());
    }

    @Test
    @DisplayName("비밀번호 재설정 확인은 비밀번호를 바꾸고 토큰과 refresh 토큰을 폐기한다")
    void confirmPasswordResetUpdatesPasswordAndClearsTokens() {
        User user = User.builder()
                .id(7L)
                .userKey("user-key-7")
                .email("user@example.com")
                .passwordHash("old-hash")
                .loginFailCount(3)
                .build();
        when(valueOperations.get("password-reset:reset-token")).thenReturn("user-key-7");
        when(userRepository.findByUserKey("user-key-7")).thenReturn(Optional.of(user));
        when(valueOperations.get("password-reset:user:user-key-7")).thenReturn("reset-token");
        when(passwordEncoder.encode("new-password123")).thenReturn("encoded-password");

        authService.confirmPasswordReset("reset-token", "new-password123");

        verify(userCoreSyncService).syncFromUser(user);
        verify(redisTemplate).delete("password-reset:reset-token");
        verify(redisTemplate).delete("password-reset:user:user-key-7");
        verify(redisTemplate).delete("refresh:user-key-7");
        org.assertj.core.api.Assertions.assertThat(user.getPasswordHash()).isEqualTo("encoded-password");
        org.assertj.core.api.Assertions.assertThat(user.getLoginFailCount()).isZero();
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
        when(valueOperations.get("password-reset:old-token")).thenReturn("user-key-7");
        when(userRepository.findByUserKey("user-key-7")).thenReturn(Optional.of(user));
        when(valueOperations.get("password-reset:user:user-key-7")).thenReturn("new-token");

        assertThatThrownBy(() -> authService.confirmPasswordReset("old-token", "new-password123"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PASSWORD_RESET_TOKEN_INVALID);

        verify(passwordEncoder, never()).encode(any());
    }
}
