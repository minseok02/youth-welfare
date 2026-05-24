package com.example.welfare.user.service;

import com.example.welfare.global.auth.AuthenticatedUser;
import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class UserSessionRevocationService {

    private static final String REFRESH_TOKEN_PREFIX = "refresh:";
    private static final String ACCESS_CUTOFF_PREFIX = "access-cutoff:";
    private static final long CUTOFF_TTL_SAFETY_MARGIN_MILLIS = 60_000L;

    private final RedisTemplate<String, String> redisTemplate;
    private final JwtUtil jwtUtil;
    private final AccessTokenRevocationService accessTokenRevocationService;

    @Value("${jwt.access-expiration}")
    private long accessExpiration;

    public boolean isAccessAllowed(String accessToken) {
        if (!StringUtils.hasText(accessToken)) {
            return true;
        }
        if (accessTokenRevocationService.isRevoked(accessToken)) {
            return false;
        }

        try {
            AuthenticatedUser authenticatedUser = jwtUtil.getAuthenticatedUser(accessToken);
            String userKey = authenticatedUser.userKey();
            if (!StringUtils.hasText(userKey)) {
                return false;
            }

            String cutoffValue = redisTemplate.opsForValue().get(accessCutoffKey(userKey));
            if (!StringUtils.hasText(cutoffValue)) {
                return true;
            }

            long cutoffMillis = Long.parseLong(cutoffValue);
            long issuedAtMillis = jwtUtil.getIssuedAtMillis(accessToken);
            return issuedAtMillis > cutoffMillis;
        } catch (CustomException | NumberFormatException e) {
            return false;
        }
    }

    public void revokeUserSessions(String userKey, long cutoffMillis) {
        redisTemplate.delete(refreshTokenKey(userKey));
        redisTemplate.opsForValue().set(
                accessCutoffKey(userKey),
                String.valueOf(cutoffMillis),
                accessExpiration + CUTOFF_TTL_SAFETY_MARGIN_MILLIS,
                TimeUnit.MILLISECONDS
        );
    }

    public long resolveNextAccessIssuedAtMillis(String userKey, long candidateIssuedAtMillis) {
        String cutoffValue = redisTemplate.opsForValue().get(accessCutoffKey(userKey));
        if (!StringUtils.hasText(cutoffValue)) {
            return candidateIssuedAtMillis;
        }

        try {
            long cutoffMillis = Long.parseLong(cutoffValue);
            return candidateIssuedAtMillis <= cutoffMillis ? cutoffMillis + 1 : candidateIssuedAtMillis;
        } catch (NumberFormatException e) {
            return candidateIssuedAtMillis;
        }
    }

    private String refreshTokenKey(String userKey) {
        return REFRESH_TOKEN_PREFIX + userKey;
    }

    private String accessCutoffKey(String userKey) {
        return ACCESS_CUTOFF_PREFIX + userKey;
    }
}
