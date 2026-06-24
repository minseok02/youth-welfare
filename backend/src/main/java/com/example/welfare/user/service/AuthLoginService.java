package com.example.welfare.user.service;

import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.example.welfare.global.util.RedisKeyHash;
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
    private final AuthLoginFailureCommandService authLoginFailureCommandService;
    private final AuthTokenService authTokenService;
    private final AuthAdminRoleService authAdminRoleService;

    @Transactional
    public TokenResponse login(LoginRequest request) {
        String normalizedEmail = EmailLookupKeyGenerator.normalize(request.getEmail());
        String emailHash = EmailLookupKeyGenerator.hash(normalizedEmail);
        AuthUser authUser = authIdentityReadService.findByEmail(normalizedEmail)
                .orElseThrow(() -> {
                    log.warn("[AuthAudit] event=login outcome=invalid_credentials emailHash={}", emailHash);
                    return new CustomException(ErrorCode.INVALID_CREDENTIALS);
                });

        if (!authUser.isActive()) {
            log.warn("[AuthAudit] event=login outcome=withdrawn userKeyHash={}",
                    RedisKeyHash.sha256Hex(authUser.getUserKey()));
            throw new CustomException(ErrorCode.WITHDRAWN_USER);
        }

        User user = activeUserReadService.getActiveUserByUserKey(authUser.getUserKey());

        if (authUser.getLockedUntil() != null && LocalDateTime.now().isBefore(authUser.getLockedUntil())) {
            log.warn("[AuthAudit] event=login outcome=account_locked userKeyHash={} lockedUntil={}",
                    RedisKeyHash.sha256Hex(authUser.getUserKey()), authUser.getLockedUntil());
            throw new CustomException(ErrorCode.ACCOUNT_LOCKED);
        }

        if (!passwordEncoder.matches(request.getPassword(), authUser.getPasswordHash())) {
            boolean locked = authLoginFailureCommandService.recordFailedAttempt(
                    user.getId(),
                    MAX_LOGIN_FAIL,
                    LOCK_MINUTES
            );
            if (locked) {
                log.warn("[AuthAudit] event=login outcome=locked_after_failure userKeyHash={} maxLoginFail={}",
                        RedisKeyHash.sha256Hex(user.getUserKey()), MAX_LOGIN_FAIL);
            } else {
                log.warn("[AuthAudit] event=login outcome=invalid_credentials userKeyHash={}",
                        RedisKeyHash.sha256Hex(user.getUserKey()));
            }
            throw new CustomException(ErrorCode.INVALID_CREDENTIALS);
        }

        user.resetLoginFail();
        userCoreSyncService.syncFromUser(user);

        TokenResponse tokenResponse = authTokenService.issueTokens(
                authUser.getUserKey(),
                user.getId(),
                authAdminRoleService.resolveRoles(normalizedEmail)
        );
        log.info("[AuthAudit] event=login outcome=success userKeyHash={}",
                RedisKeyHash.sha256Hex(authUser.getUserKey()));
        return tokenResponse;
    }
}
