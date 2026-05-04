package com.example.welfare.user.service;

import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.example.welfare.user.dto.request.LoginRequest;
import com.example.welfare.user.dto.response.TokenResponse;
import com.example.welfare.user.entity.AuthUser;
import com.example.welfare.user.entity.User;
import com.example.welfare.user.util.EmailLookupKeyGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthLoginService {

    private static final int MAX_LOGIN_FAIL = 5;
    private static final int LOCK_MINUTES = 30;

    private final AuthIdentityReadService authIdentityReadService;
    private final PasswordEncoder passwordEncoder;
    private final ActiveUserReadService activeUserReadService;
    private final UserCoreSyncService userCoreSyncService;
    private final AuthTokenService authTokenService;
    private final AuthAdminRoleService authAdminRoleService;

    @Transactional
    public TokenResponse login(LoginRequest request) {
        String normalizedEmail = EmailLookupKeyGenerator.normalize(request.getEmail());
        AuthUser authUser = authIdentityReadService.findByEmail(normalizedEmail)
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_CREDENTIALS));

        if (!authUser.isActive()) {
            throw new CustomException(ErrorCode.WITHDRAWN_USER);
        }

        User user = activeUserReadService.getActiveUserByUserKey(authUser.getUserKey());

        if (authUser.getLockedUntil() != null && LocalDateTime.now().isBefore(authUser.getLockedUntil())) {
            throw new CustomException(ErrorCode.ACCOUNT_LOCKED);
        }

        if (!passwordEncoder.matches(request.getPassword(), authUser.getPasswordHash())) {
            user.increaseLoginFailCount();
            if (user.getLoginFailCount() >= MAX_LOGIN_FAIL) {
                user.lock(LocalDateTime.now().plusMinutes(LOCK_MINUTES));
                log.warn("Account locked: userId={}", user.getId());
            }
            userCoreSyncService.syncFromUser(user);
            throw new CustomException(ErrorCode.INVALID_CREDENTIALS);
        }

        user.resetLoginFail();
        userCoreSyncService.syncFromUser(user);

        return authTokenService.issueTokens(
                authUser.getUserKey(),
                user.getId(),
                authAdminRoleService.resolveRoles(normalizedEmail)
        );
    }
}
