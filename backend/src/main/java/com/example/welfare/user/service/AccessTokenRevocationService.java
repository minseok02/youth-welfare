package com.example.welfare.user.service;

import com.example.welfare.global.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class AccessTokenRevocationService {

    private static final String REVOKED_ACCESS_TOKEN_PREFIX = "access-revoked:";

    private final RedisTemplate<String, String> redisTemplate;
    private final JwtUtil jwtUtil;

    public void revoke(String accessToken) {
        if (!StringUtils.hasText(accessToken)) {
            return;
        }

        Date expiration = jwtUtil.getExpirationAllowExpired(accessToken);
        long ttlMillis = expiration.getTime() - System.currentTimeMillis();
        if (ttlMillis <= 0) {
            return;
        }

        redisTemplate.opsForValue().set(
                revocationKey(accessToken),
                "1",
                ttlMillis,
                TimeUnit.MILLISECONDS
        );
    }

    public boolean isRevoked(String accessToken) {
        if (!StringUtils.hasText(accessToken)) {
            return false;
        }
        return Boolean.TRUE.equals(redisTemplate.hasKey(revocationKey(accessToken)));
    }

    private String revocationKey(String accessToken) {
        return REVOKED_ACCESS_TOKEN_PREFIX + accessToken;
    }
}
