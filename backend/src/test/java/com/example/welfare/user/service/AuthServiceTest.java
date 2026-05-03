package com.example.welfare.user.service;

import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.example.welfare.user.dto.request.LoginRequest;
import com.example.welfare.user.dto.response.TokenResponse;
import com.example.welfare.user.entity.AuthUser;
import com.example.welfare.user.entity.User;
import com.example.welfare.user.repository.AuthUserRepository;
import com.example.welfare.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
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
    private PasswordEncoder passwordEncoder;
    @Mock
    private UserCoreSyncService userCoreSyncService;
    @Mock
    private AuthTokenService authTokenService;
    @Mock
    private PasswordResetService passwordResetService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                authUserRepository,
                userRepository,
                passwordEncoder,
                userCoreSyncService,
                authTokenService,
                passwordResetService
        );
        ReflectionTestUtils.setField(authService, "adminEmailsProperty", "admin@example.com");
        authService.initAdminEmails();
    }

    @Test
    @DisplayName("이메일 중복확인은 대소문자를 무시한 auth_users lookup hash 기준으로 판단한다")
    void checkEmailAvailabilityUsesLookupHash() {
        when(authUserRepository.existsByEmailLookupHash("b4c9a289323b21a01c3e940f150eb9b8c542587f1abfd8f0e1cc1ffc5e475514"))
                .thenReturn(true);

        assertThat(authService.checkEmailAvailability(" USER@example.com ").available()).isFalse();
    }

    @Test
    @DisplayName("로그인은 역할 해석 후 토큰 발급을 AuthTokenService에 위임한다")
    void loginDelegatesTokenIssueWithResolvedRoles() {
        LoginRequest request = new LoginRequest();
        ReflectionTestUtils.setField(request, "email", "admin@example.com");
        ReflectionTestUtils.setField(request, "password", "password123!");

        AuthUser authUser = AuthUser.builder()
                .userKey("user-key-1")
                .passwordHash("encoded")
                .isActive(true)
                .build();
        User user = User.builder()
                .id(1L)
                .userKey("user-key-1")
                .email("admin@example.com")
                .passwordHash("encoded")
                .build();

        when(authUserRepository.findByEmailLookupHash(anyString()))
                .thenReturn(Optional.of(authUser));
        when(userRepository.findByUserKey("user-key-1")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123!", "encoded")).thenReturn(true);
        when(authTokenService.issueTokens(eq("user-key-1"), eq(1L), anyList()))
                .thenReturn(TokenResponse.of("access", "refresh"));

        TokenResponse response = authService.login(request);

        assertThat(response.getAccessToken()).isEqualTo("access");
        verify(authTokenService).issueTokens("user-key-1", 1L, List.of("ROLE_USER", "ROLE_ADMIN"));
    }

    @Test
    @DisplayName("비밀번호 재설정 요청은 PasswordResetService에 위임한다")
    void requestPasswordResetDelegates() {
        authService.requestPasswordReset("user@example.com");
        verify(passwordResetService).requestPasswordReset("user@example.com");
    }

    @Test
    @DisplayName("비밀번호 재설정 확인은 PasswordResetService에 위임한다")
    void confirmPasswordResetDelegates() {
        authService.confirmPasswordReset("reset-token", "new-password123");
        verify(passwordResetService).confirmPasswordReset("reset-token", "new-password123");
    }

    @Test
    @DisplayName("로그아웃은 AuthTokenService에 위임한다")
    void logoutByUserKeyDelegates() {
        authService.logoutByUserKey("user-key-7", "access-token-value");
        verify(authTokenService).logoutByUserKey("user-key-7", "access-token-value");
    }

    @Test
    @DisplayName("비밀번호가 틀리면 토큰 발급을 시도하지 않는다")
    void loginRejectsInvalidPasswordBeforeTokenIssue() {
        LoginRequest request = new LoginRequest();
        ReflectionTestUtils.setField(request, "email", "user@example.com");
        ReflectionTestUtils.setField(request, "password", "wrong-password");

        AuthUser authUser = AuthUser.builder()
                .userKey("user-key-1")
                .passwordHash("encoded")
                .isActive(true)
                .build();
        User user = User.builder()
                .id(1L)
                .userKey("user-key-1")
                .email("user@example.com")
                .passwordHash("encoded")
                .build();

        when(authUserRepository.findByEmailLookupHash("b4c9a289323b21a01c3e940f150eb9b8c542587f1abfd8f0e1cc1ffc5e475514"))
                .thenReturn(Optional.of(authUser));
        when(userRepository.findByUserKey("user-key-1")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-password", "encoded")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_CREDENTIALS);

        verify(authTokenService, never()).issueTokens(eq("user-key-1"), eq(1L), anyList());
    }
}
