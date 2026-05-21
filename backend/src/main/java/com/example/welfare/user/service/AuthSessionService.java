package com.example.welfare.user.service;

import com.example.welfare.user.dto.response.TokenResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthSessionService {

    private final AuthTokenService authTokenService;
    private final AuthAdminRoleService authAdminRoleService;

    @Transactional
    public TokenResponse refresh(String refreshToken) {
        return authTokenService.refresh(refreshToken, authAdminRoleService::resolveRolesByEmailLookupHash);
    }

    @Transactional
    public void logout(Long userId, String accessToken) {
        authTokenService.logout(userId, accessToken);
    }

    @Transactional
    public void logoutByUserKey(String userKey, String accessToken) {
        authTokenService.logoutByUserKey(userKey, accessToken);
    }

    @Transactional
    public void logoutByRefreshToken(String refreshToken, String accessToken) {
        authTokenService.logoutByRefreshToken(refreshToken, accessToken);
    }
}
