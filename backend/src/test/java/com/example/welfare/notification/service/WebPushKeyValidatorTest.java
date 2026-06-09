package com.example.welfare.notification.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class WebPushKeyValidatorTest {

    private final WebPushKeyValidator validator = new WebPushKeyValidator();

    @Test
    @DisplayName("웹푸시 subscription p256dh는 uncompressed P-256 public key만 허용한다")
    void validatesSubscriptionPublicKey() {
        byte[] key = new byte[65];
        key[0] = 0x04;
        String valid = base64Url(key);
        String wrongPrefix = base64Url(new byte[65]);
        String wrongLength = base64Url(new byte[64]);

        assertThat(validator.isValidSubscriptionPublicKey(valid)).isTrue();
        assertThat(validator.isValidSubscriptionPublicKey(wrongPrefix)).isFalse();
        assertThat(validator.isValidSubscriptionPublicKey(wrongLength)).isFalse();
        assertThat(validator.isValidSubscriptionPublicKey("not base64url!!")).isFalse();
    }

    @Test
    @DisplayName("웹푸시 auth secret은 16 byte base64url 값만 허용한다")
    void validatesSubscriptionAuthSecret() {
        assertThat(validator.isValidAuthSecret(base64Url(new byte[16]))).isTrue();
        assertThat(validator.isValidAuthSecret(base64Url(new byte[15]))).isFalse();
        assertThat(validator.isValidAuthSecret(base64Url(new byte[17]))).isFalse();
        assertThat(validator.isValidAuthSecret("abcde")).isFalse();
    }

    @Test
    @DisplayName("VAPID private key는 32 byte base64url 값만 허용한다")
    void validatesVapidPrivateKey() {
        assertThat(validator.isValidPrivateKey(base64Url(new byte[32]))).isTrue();
        assertThat(validator.isValidPrivateKey(base64Url(new byte[31]))).isFalse();
        assertThat(validator.isValidPrivateKey(base64Url(new byte[33]))).isFalse();
    }

    private String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
