package com.example.welfare.global.util;

import com.example.welfare.global.config.AesProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AesEncryptUtilTest {

    private static final String SECRET_KEY = "12345678901234567890123456789012";

    private final AesEncryptUtil aesEncryptUtil =
            new AesEncryptUtil(new AesProperties(SECRET_KEY));

    @Test
    @DisplayName("encrypt/decrypt round-trip이 된다")
    void roundTrip() {
        String encrypted = aesEncryptUtil.encrypt("user@example.com");

        assertThat(encrypted).isNotBlank();
        assertThat(encrypted).startsWith("v2:");
        assertThat(aesEncryptUtil.decrypt(encrypted)).isEqualTo("user@example.com");
    }

    @Test
    @DisplayName("같은 평문도 랜덤 IV 때문에 서로 다른 암호문이 나온다")
    void samePlainTextProducesDifferentCipherTexts() {
        String encrypted1 = aesEncryptUtil.encrypt("same-value");
        String encrypted2 = aesEncryptUtil.encrypt("same-value");

        assertThat(encrypted1).isNotEqualTo(encrypted2);
        assertThat(aesEncryptUtil.decrypt(encrypted1)).isEqualTo("same-value");
        assertThat(aesEncryptUtil.decrypt(encrypted2)).isEqualTo("same-value");
    }

    @Test
    @DisplayName("변조된 GCM payload는 decrypt에서 실패한다")
    void tamperedGcmPayloadFailsToDecrypt() {
        String encrypted = aesEncryptUtil.encrypt("tamper-target");
        String payload = encrypted.substring("v2:".length());
        byte[] decoded = Base64.getDecoder().decode(payload);
        decoded[decoded.length - 1] ^= 0x01;
        String tampered = "v2:" + Base64.getEncoder().encodeToString(decoded);

        assertThatThrownBy(() -> aesEncryptUtil.decrypt(tampered))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("복호화 처리 중 오류가 발생했습니다.");
    }

    @Test
    @DisplayName("기존 CBC payload도 fallback 복호화한다")
    void decryptSupportsLegacyCbcPayload() throws Exception {
        String legacyEncrypted = legacyEncrypt("legacy-user@example.com");

        assertThat(aesEncryptUtil.decrypt(legacyEncrypted)).isEqualTo("legacy-user@example.com");
    }

    @Test
    @DisplayName("잘못된 legacy payload는 decrypt에서 실패한다")
    void invalidLegacyPayloadFailsToDecrypt() {
        assertThatThrownBy(() -> aesEncryptUtil.decrypt("Zm9v"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("복호화 처리 중 오류가 발생했습니다.");
    }

    private String legacyEncrypt(String plainText) throws Exception {
        SecretKeySpec keySpec = new SecretKeySpec(SECRET_KEY.getBytes(StandardCharsets.UTF_8), "AES");
        byte[] iv = new byte[16];
        new SecureRandom().nextBytes(iv);
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, new IvParameterSpec(iv));
        byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
        byte[] payload = new byte[iv.length + encrypted.length];
        System.arraycopy(iv, 0, payload, 0, iv.length);
        System.arraycopy(encrypted, 0, payload, iv.length, encrypted.length);
        return Base64.getEncoder().encodeToString(payload);
    }
}
