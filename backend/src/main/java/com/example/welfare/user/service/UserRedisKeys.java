package com.example.welfare.user.service;

import com.example.welfare.global.util.RedisKeyHash;

import java.util.List;

final class UserRedisKeys {

    private static final String REFRESH_TOKEN_PREFIX = "refresh:v2:";
    private static final String LEGACY_REFRESH_TOKEN_PREFIX = "refresh:";
    private static final String ACCESS_CUTOFF_PREFIX = "access-cutoff:v2:";
    private static final String LEGACY_ACCESS_CUTOFF_PREFIX = "access-cutoff:";
    private static final String PASSWORD_RESET_USER_PREFIX = "password-reset:user:v2:";
    private static final String LEGACY_PASSWORD_RESET_USER_PREFIX = "password-reset:user:";

    private UserRedisKeys() {
    }

    static String refreshTokenKey(String userKey) {
        return REFRESH_TOKEN_PREFIX + RedisKeyHash.sha256Hex(userKey);
    }

    static String legacyRefreshTokenKey(String userKey) {
        return LEGACY_REFRESH_TOKEN_PREFIX + userKey;
    }

    static List<String> refreshTokenKeys(String userKey) {
        return List.of(refreshTokenKey(userKey), legacyRefreshTokenKey(userKey));
    }

    static String accessCutoffKey(String userKey) {
        return ACCESS_CUTOFF_PREFIX + RedisKeyHash.sha256Hex(userKey);
    }

    static String legacyAccessCutoffKey(String userKey) {
        return LEGACY_ACCESS_CUTOFF_PREFIX + userKey;
    }

    static String passwordResetUserKey(String userKey) {
        return PASSWORD_RESET_USER_PREFIX + RedisKeyHash.sha256Hex(userKey);
    }

    static String legacyPasswordResetUserKey(String userKey) {
        return LEGACY_PASSWORD_RESET_USER_PREFIX + userKey;
    }
}
