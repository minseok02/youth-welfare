package com.example.welfare.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;

@ConfigurationProperties(prefix = "aes")
public record AesProperties(String secretKey) {

    public AesProperties {
        if (!StringUtils.hasText(secretKey)) {
            throw new IllegalArgumentException("aes.secret-key must not be blank");
        }

        int utf8Length = secretKey.getBytes(StandardCharsets.UTF_8).length;
        if (utf8Length != 32) {
            throw new IllegalArgumentException("aes.secret-key must be exactly 32 bytes in UTF-8");
        }
    }
}
