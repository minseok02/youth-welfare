package com.example.welfare.user.service;

import com.example.welfare.user.dto.response.TokenResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthSessionServiceTest {

    @Mock
    private AuthTokenService authTokenService;

    private AuthAdminRoleService authAdminRoleService;

    private AuthSessionService authSessionService;

    @BeforeEach
    void setUp() {
        authAdminRoleService = new AuthAdminRoleService();
        ReflectionTestUtils.setField(authAdminRoleService, "adminEmailsProperty", "admin@example.com");
        ReflectionTestUtils.invokeMethod(authAdminRoleService, "initAdminEmails");
        authSessionService = new AuthSessionService(authTokenService, authAdminRoleService);
    }

    @Test
    @DisplayName("refresh는 role resolver를 넘겨 AuthTokenService에 위임한다")
    void refreshDelegatesToTokenService() {
        when(authTokenService.refresh(eq("refresh-token"), any()))
                .thenReturn(TokenResponse.of("access", "refresh"));

        TokenResponse response = authSessionService.refresh("refresh-token");

        assertThat(response.getAccessToken()).isEqualTo("access");
    }

    @Test
    @DisplayName("logout은 AuthTokenService에 위임한다")
    void logoutDelegates() {
        authSessionService.logoutByRefreshToken("refresh-token", "access-token");
        authSessionService.logoutByUserKey("user-key-1", "access-token");
        authSessionService.logout(1L, "access-token");

        verify(authTokenService).logoutByRefreshToken("refresh-token", "access-token");
        verify(authTokenService).logoutByUserKey("user-key-1", "access-token");
        verify(authTokenService).logout(1L, "access-token");
    }
}
