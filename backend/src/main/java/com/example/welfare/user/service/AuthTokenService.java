package com.example.welfare.user.service;

import com.example.welfare.chat.service.ChatSessionCleanupService;
import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.example.welfare.global.util.JwtUtil;
import com.example.welfare.user.dto.response.TokenResponse;
import com.example.welfare.user.entity.AuthUser;
import com.example.welfare.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthTokenService {

    private final JwtUtil jwtUtil;
    private final RedisTemplate<String, String> redisTemplate;
    private final ActiveUserReadService activeUserReadService;
    private final AccessTokenRevocationService accessTokenRevocationService;
    private final ChatSessionCleanupService chatSessionCleanupService;
    private final UserKeyLookupService userKeyLookupService;
    private final AuthIdentityReadService authIdentityReadService;
    private final UserSessionRevocationService userSessionRevocationService;

    public TokenResponse issueTokens(String userKey, Long userId, List<String> roles) {
        long accessIssuedAtMillis = userSessionRevocationService.resolveNextAccessIssuedAtMillis(
                userKey,
                System.currentTimeMillis()
        );
        String accessToken = jwtUtil.generateAccessToken(userKey, userId, roles, accessIssuedAtMillis);
        String refreshToken = jwtUtil.generateRefreshToken(userKey, userId);
        saveRefreshToken(userKey, refreshToken);
        return TokenResponse.of(accessToken, refreshToken);
    }

    @Transactional
    public TokenResponse refresh(String refreshToken, Function<String, List<String>> rolesResolver) {
        jwtUtil.validate(refreshToken);

        String userKey = resolveTokenUserKey(refreshToken);
        Long userId = jwtUtil.getUserId(refreshToken);
        User user = getRefreshableUser(userKey, userId);

        String stored = redisTemplate.opsForValue().get(refreshTokenKey(userKey));
        boolean legacyRefreshKeyMatched = false;
        if (!StringUtils.hasText(stored)) {
            stored = redisTemplate.opsForValue().get(legacyRefreshTokenKey(userKey));
            legacyRefreshKeyMatched = StringUtils.hasText(stored);
        }
        if (!matchesStoredRefreshToken(stored, refreshToken)) {
            redisTemplate.delete(refreshTokenKeys(userKey));
            throw new CustomException(ErrorCode.REUSED_REFRESH_TOKEN);
        }
        if (legacyRefreshKeyMatched) {
            redisTemplate.delete(legacyRefreshTokenKey(userKey));
        }

        AuthUser authUser = authIdentityReadService.findByUserKey(userKey)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        return issueTokens(userKey, userId, rolesResolver.apply(authUser.getEmailLookupHash()));
    }

    @Transactional
    public void logout(Long userId, String accessToken) {
        String userKey = userKeyLookupService.findRequired(userId);
        logoutByUserKey(userKey, accessToken);
    }

    @Transactional
    public void logoutByUserKey(String userKey, String accessToken) {
        revokeUserSessions(userKey);
        revokePresentedAccessToken(accessToken);
        chatSessionCleanupService.deleteAllByUserKey(userKey);
    }

    @Transactional
    public void logoutByRefreshToken(String refreshToken, String accessToken) {
        String userKey = resolveTokenUserKeyAllowExpired(refreshToken);
        revokeUserSessions(userKey);
        revokePresentedAccessToken(accessToken);
        chatSessionCleanupService.deleteAllByUserKey(userKey);
    }

    public void invalidateRefreshToken(String userKey) {
        redisTemplate.delete(refreshTokenKeys(userKey));
    }

    private void saveRefreshToken(String userKey, String refreshToken) {
        redisTemplate.opsForValue().set(
                refreshTokenKey(userKey),
                OpaqueTokenHash.sha256Hex(refreshToken),
                7,
                TimeUnit.DAYS
        );
        redisTemplate.delete(legacyRefreshTokenKey(userKey));
    }

    private void revokePresentedAccessToken(String accessToken) {
        if (!StringUtils.hasText(accessToken)) {
            return;
        }
        try {
            accessTokenRevocationService.revoke(accessToken);
        } catch (CustomException e) {
            log.debug("Skipping access-token revocation during logout because presented token was invalid");
        }
    }

    private void revokeUserSessions(String userKey) {
        userSessionRevocationService.revokeUserSessions(userKey, System.currentTimeMillis());
    }

    private String resolveTokenUserKey(String token) {
        return requireUserKeySubject(jwtUtil.getSubject(token));
    }

    private String resolveTokenUserKeyAllowExpired(String token) {
        return requireUserKeySubject(jwtUtil.getSubjectAllowExpired(token));
    }

    private String requireUserKeySubject(String subject) {
        if (!StringUtils.hasText(subject)) {
            throw new CustomException(ErrorCode.INVALID_TOKEN);
        }
        if (subject.chars().allMatch(Character::isDigit)) {
            throw new CustomException(ErrorCode.INVALID_TOKEN);
        }
        return subject;
    }

    private String refreshTokenKey(String userKey) {
        return UserRedisKeys.refreshTokenKey(userKey);
    }

    private String legacyRefreshTokenKey(String userKey) {
        return UserRedisKeys.legacyRefreshTokenKey(userKey);
    }

    private java.util.List<String> refreshTokenKeys(String userKey) {
        return UserRedisKeys.refreshTokenKeys(userKey);
    }

    private boolean matchesStoredRefreshToken(String stored, String presentedRefreshToken) {
        if (!StringUtils.hasText(stored)) {
            return false;
        }
        String presentedHash = OpaqueTokenHash.sha256Hex(presentedRefreshToken);
        return stored.equals(presentedHash)
                || stored.equals(presentedRefreshToken);
    }

    private User getRefreshableUser(String userKey, Long userId) {
        try {
            return activeUserReadService.getActiveUserContext(userId).user();
        } catch (CustomException e) {
            if (e.getErrorCode() == ErrorCode.WITHDRAWN_USER) {
                invalidateRefreshToken(userKey);
            }
            throw e;
        }
    }
}
