package com.example.welfare.notification.service;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Base64;
import java.util.regex.Pattern;

@Component
public class WebPushKeyValidator {

    private static final Pattern BASE64URL_PATTERN = Pattern.compile("^[A-Za-z0-9_-]+={0,2}$");

    public boolean isValidPublicKey(String publicKey) {
        return isValidUncompressedP256PublicKey(publicKey);
    }

    public boolean isValidPrivateKey(String privateKey) {
        byte[] decoded = decodeUrlBase64(privateKey);
        return decoded != null && decoded.length == 32;
    }

    public boolean isValidSubscriptionPublicKey(String p256dh) {
        return isValidUncompressedP256PublicKey(p256dh);
    }

    public boolean isValidAuthSecret(String authSecret) {
        byte[] decoded = decodeUrlBase64(authSecret);
        return decoded != null && decoded.length == 16;
    }

    private boolean isValidUncompressedP256PublicKey(String value) {
        byte[] decoded = decodeUrlBase64(value);
        return decoded != null && decoded.length == 65 && decoded[0] == 0x04;
    }

    private byte[] decodeUrlBase64(String value) {
        String normalized = StringUtils.trimWhitespace(value);
        if (!StringUtils.hasText(normalized) || !BASE64URL_PATTERN.matcher(normalized).matches()) {
            return null;
        }
        int remainder = normalized.length() % 4;
        if (remainder == 1) {
            return null;
        }
        if (remainder > 0) {
            normalized = normalized + "=".repeat(4 - remainder);
        }
        try {
            return Base64.getUrlDecoder().decode(normalized);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
