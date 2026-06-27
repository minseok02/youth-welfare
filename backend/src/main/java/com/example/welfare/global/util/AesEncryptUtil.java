package com.example.welfare.global.util;

import com.example.welfare.global.config.AesProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

@Slf4j
@Component
public class AesEncryptUtil {

    private static final String LEGACY_ALGORITHM = "AES/CBC/PKCS5Padding";
    private static final int LEGACY_IV_LENGTH = 16;
    private static final String CURRENT_ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_NONCE_LENGTH = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final String VERSION_PREFIX = "v2:";

    private final String secretKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public AesEncryptUtil(AesProperties aesProperties) {
        this.secretKey = aesProperties.secretKey();
    }

    public String encrypt(String plainText) {
        if (plainText == null) return null;
        try {
            SecretKeySpec keySpec = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "AES");
            byte[] nonce = new byte[GCM_NONCE_LENGTH];
            secureRandom.nextBytes(nonce);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce);
            Cipher cipher = Cipher.getInstance(CURRENT_ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, parameterSpec);
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            byte[] payload = new byte[GCM_NONCE_LENGTH + encrypted.length];
            System.arraycopy(nonce, 0, payload, 0, GCM_NONCE_LENGTH);
            System.arraycopy(encrypted, 0, payload, GCM_NONCE_LENGTH, encrypted.length);
            return VERSION_PREFIX + Base64.getEncoder().encodeToString(payload);
        } catch (Exception e) {
            log.error("AES encrypt failed errorType={}", e.getClass().getSimpleName());
            throw new RuntimeException("암호화 처리 중 오류가 발생했습니다.");
        }
    }

    public String decrypt(String encryptedText) {
        if (encryptedText == null) return null;
        if (encryptedText.startsWith(VERSION_PREFIX)) {
            return decryptCurrent(encryptedText.substring(VERSION_PREFIX.length()));
        }
        return decryptLegacy(encryptedText);
    }

    public boolean isCurrentCipherText(String encryptedText) {
        return encryptedText != null && encryptedText.startsWith(VERSION_PREFIX);
    }

    private String decryptCurrent(String encodedPayload) {
        try {
            SecretKeySpec keySpec = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "AES");
            byte[] decoded = Base64.getDecoder().decode(encodedPayload);
            if (decoded.length <= GCM_NONCE_LENGTH) {
                throw new IllegalArgumentException("encrypted payload is too short");
            }

            byte[] nonce = Arrays.copyOfRange(decoded, 0, GCM_NONCE_LENGTH);
            byte[] encrypted = Arrays.copyOfRange(decoded, GCM_NONCE_LENGTH, decoded.length);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce);
            Cipher cipher = Cipher.getInstance(CURRENT_ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, parameterSpec);
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("AES decrypt failed errorType={}", e.getClass().getSimpleName());
            throw new RuntimeException("복호화 처리 중 오류가 발생했습니다.");
        }
    }

    private String decryptLegacy(String encryptedText) {
        try {
            SecretKeySpec keySpec = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "AES");
            byte[] decoded = Base64.getDecoder().decode(encryptedText);
            if (decoded.length <= LEGACY_IV_LENGTH) {
                throw new IllegalArgumentException("encrypted payload is too short");
            }

            byte[] iv = Arrays.copyOfRange(decoded, 0, LEGACY_IV_LENGTH);
            byte[] encrypted = Arrays.copyOfRange(decoded, LEGACY_IV_LENGTH, decoded.length);
            IvParameterSpec ivSpec = new IvParameterSpec(iv);
            Cipher cipher = Cipher.getInstance(LEGACY_ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("AES decrypt failed errorType={}", e.getClass().getSimpleName());
            throw new RuntimeException("복호화 처리 중 오류가 발생했습니다.");
        }
    }
}
