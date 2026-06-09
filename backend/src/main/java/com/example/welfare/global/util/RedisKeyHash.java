package com.example.welfare.global.util;

import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

public final class RedisKeyHash {

    private RedisKeyHash() {
    }

    public static String sha256Hex(String value) {
        if (!StringUtils.hasText(value)) {
            return "blank";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.trim().getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("failed to hash redis key fragment", e);
        }
    }
}
