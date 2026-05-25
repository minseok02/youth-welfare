package com.example.welfare.notification.service;

import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class NotificationUnsubscribeTokenService {

    private static final String UNSUBSCRIBE_TOKEN_PREFIX = "notification:unsubscribe:";

    private final RedisTemplate<String, String> redisTemplate;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${notification.unsubscribe-token.expiration-millis:2592000000}")
    private long unsubscribeTokenExpirationMillis;

    public String issueToken(String userKey) {
        if (!StringUtils.hasText(userKey)) {
            throw new CustomException(ErrorCode.INVALID_TOKEN);
        }

        String opaqueToken = generateOpaqueToken();
        redisTemplate.opsForValue().set(
                unsubscribeTokenKey(opaqueToken),
                userKey,
                unsubscribeTokenExpirationMillis,
                TimeUnit.MILLISECONDS
        );
        return opaqueToken;
    }

    public Optional<String> consumeUserKey(String opaqueToken) {
        if (!StringUtils.hasText(opaqueToken)) {
            return Optional.empty();
        }

        String key = unsubscribeTokenKey(opaqueToken);
        String userKey = redisTemplate.opsForValue().get(key);
        if (!StringUtils.hasText(userKey)) {
            return Optional.empty();
        }
        redisTemplate.delete(key);
        return Optional.of(userKey);
    }

    String storageKeyForTest(String opaqueToken) {
        return unsubscribeTokenKey(opaqueToken);
    }

    private String generateOpaqueToken() {
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    private String unsubscribeTokenKey(String opaqueToken) {
        return UNSUBSCRIBE_TOKEN_PREFIX + sha256(opaqueToken);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("failed to hash unsubscribe token", e);
        }
    }
}
