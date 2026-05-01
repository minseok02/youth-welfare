package com.example.welfare.global.util;

import com.example.welfare.global.config.AesProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

@Slf4j
@Component
public class AesEncryptUtil {

    private static final String ALGORITHM = "AES/CBC/PKCS5Padding";
    private static final int IV_LENGTH = 16;

    private final String secretKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public AesEncryptUtil(AesProperties aesProperties) {
        this.secretKey = aesProperties.secretKey();
    }

    public String encrypt(String plainText) {
        if (plainText == null) return null;
        try {
            SecretKeySpec keySpec = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "AES");
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);
            IvParameterSpec ivSpec = new IvParameterSpec(iv);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            byte[] payload = new byte[IV_LENGTH + encrypted.length];
            System.arraycopy(iv, 0, payload, 0, IV_LENGTH);
            System.arraycopy(encrypted, 0, payload, IV_LENGTH, encrypted.length);
            return Base64.getEncoder().encodeToString(payload);
        } catch (Exception e) {
            log.error("AES encrypt failed", e);
            throw new RuntimeException("암호화 처리 중 오류가 발생했습니다.");
        }
    }

    public String decrypt(String encryptedText) {
        if (encryptedText == null) return null;
        try {
            SecretKeySpec keySpec = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "AES");
            byte[] decoded = Base64.getDecoder().decode(encryptedText);
            if (decoded.length <= IV_LENGTH) {
                throw new IllegalArgumentException("encrypted payload is too short");
            }

            byte[] iv = Arrays.copyOfRange(decoded, 0, IV_LENGTH);
            byte[] encrypted = Arrays.copyOfRange(decoded, IV_LENGTH, decoded.length);

            IvParameterSpec ivSpec = new IvParameterSpec(iv);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("AES decrypt failed", e);
            throw new RuntimeException("복호화 처리 중 오류가 발생했습니다.");
        }
    }
}
