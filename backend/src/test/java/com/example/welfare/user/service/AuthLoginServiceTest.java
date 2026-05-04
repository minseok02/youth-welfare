package com.example.welfare.user.service;

import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.example.welfare.user.dto.request.LoginRequest;
import com.example.welfare.user.dto.response.TokenResponse;
import com.example.welfare.user.entity.AuthUser;
import com.example.welfare.user.entity.User;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthLoginServiceTest {

    @Mock
    private AuthIdentityReadService authIdentityReadService;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private ActiveUserReadService activeUserReadService;
    @Mock
    private UserCoreSyncService userCoreSyncService;
    @Mock
    private AuthTokenService authTokenService;

    private AuthAdminRoleService authAdminRoleService;

    private AuthLoginService authLoginService;

    @BeforeEach
    void setUp() {
        authAdminRoleService = new AuthAdminRoleService();
        ReflectionTestUtils.setField(authAdminRoleService, "adminEmailsProperty", "admin@example.com");
        ReflectionTestUtils.invokeMethod(authAdminRoleService, "initAdminEmails");
        authLoginService = new AuthLoginService(
                authIdentityReadService,
                passwordEncoder,
                activeUserReadService,
                userCoreSyncService,
                authTokenService,
                authAdminRoleService
        );
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

        when(authIdentityReadService.findByEmail(anyString())).thenReturn(Optional.of(authUser));
        when(activeUserReadService.getActiveUserByUserKey("user-key-1")).thenReturn(user);
        when(passwordEncoder.matches("password123!", "encoded")).thenReturn(true);
        when(authTokenService.issueTokens(eq("user-key-1"), eq(1L), anyList()))
                .thenReturn(TokenResponse.of("access", "refresh"));

        TokenResponse response = authLoginService.login(request);

        assertThat(response.getAccessToken()).isEqualTo("access");
        verify(authTokenService).issueTokens("user-key-1", 1L, List.of("ROLE_USER", "ROLE_ADMIN"));
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

        when(authIdentityReadService.findByEmail("user@example.com")).thenReturn(Optional.of(authUser));
        when(activeUserReadService.getActiveUserByUserKey("user-key-1")).thenReturn(user);
        when(passwordEncoder.matches("wrong-password", "encoded")).thenReturn(false);

        assertThatThrownBy(() -> authLoginService.login(request))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_CREDENTIALS);

        verify(authTokenService, never()).issueTokens(eq("user-key-1"), eq(1L), anyList());
    }
}
