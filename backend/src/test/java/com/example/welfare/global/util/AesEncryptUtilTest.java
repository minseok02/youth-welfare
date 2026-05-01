package com.example.welfare.global.util;

import com.example.welfare.global.config.AesProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AesEncryptUtilTest {

    private final AesEncryptUtil aesEncryptUtil =
            new AesEncryptUtil(new AesProperties("12345678901234567890123456789012"));

    @Test
    @DisplayName("encrypt/decrypt round-trip이 된다")
    void roundTrip() {
        String encrypted = aesEncryptUtil.encrypt("user@example.com");

        assertThat(encrypted).isNotBlank();
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
    @DisplayName("잘못된 payload는 decrypt에서 실패한다")
    void invalidPayloadFailsToDecrypt() {
        assertThatThrownBy(() -> aesEncryptUtil.decrypt("Zm9v"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("복호화 처리 중 오류가 발생했습니다.");
    }
}
