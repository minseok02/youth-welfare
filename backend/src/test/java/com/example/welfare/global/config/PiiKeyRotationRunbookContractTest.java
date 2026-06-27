package com.example.welfare.global.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PiiKeyRotationRunbookContractTest {

    private static final Path RUNBOOK = Path.of("../docs/core/pii-key-rotation-runbook.md");
    private static final Path VERIFY_SCRIPT = Path.of("../deploy/smoke/verify-pii-key-rotation-runbook.sh");

    @Test
    @DisplayName("PII key rotation runbook은 현재 가능한 legacy cipher rotation과 금지된 key swap을 구분한다")
    void runbookSeparatesLegacyCipherRotationFromKeySwap() throws IOException {
        String doc = Files.readString(RUNBOOK);

        assertThat(doc)
                .contains("PII_KEY_ROTATION_CURRENT_SCOPE")
                .contains("PII_KEY_ROTATION_FORBIDDEN_DIRECT_AES_SECRET_SWAP")
                .contains("PII_LEGACY_CIPHER_ROTATION_RUNBOOK")
                .contains("PII_KEY_ROTATION_FUTURE_DUAL_KEY_CONTRACT")
                .contains("PII_KEY_ROTATION_EVIDENCE")
                .contains("POST /api/admin/users/pii-encryption-rotation")
                .contains("현재 코드에서 `AES_SECRET_KEY` 를 바로 교체하는 것은 금지합니다.")
                .contains("AES/GCM/NoPadding")
                .contains("AES/CBC/PKCS5Padding")
                .contains("failedCount=0")
                .contains("run-local-pii-sync-cutover-smoke.sh")
                .contains("DB snapshot restore");
    }

    @Test
    @DisplayName("PII key rotation runbook은 future dual-key 구현 전제 조건을 명시한다")
    void runbookContainsFutureDualKeyContract() throws IOException {
        String doc = Files.readString(RUNBOOK);

        assertThat(doc)
                .contains("AES_ACTIVE_KEY_ID")
                .contains("AES_DECRYPT_KEYS")
                .contains("새 암호문 payload에 key id 저장")
                .contains("old key decrypt + active key encrypt 재암호화 job")
                .contains("key id별 잔여 암호문 계수 export")
                .contains("이 dual-key 계약이 구현되기 전까지는 `AES_SECRET_KEY` 값을 운영에서 교체하지 않습니다.");
    }

    @Test
    @DisplayName("PII key rotation runbook 검증 스크립트는 같은 계약 식별자를 확인한다")
    void verificationScriptChecksRunbookContractIds() throws IOException {
        String script = Files.readString(VERIFY_SCRIPT);

        assertThat(script)
                .contains("PII_KEY_ROTATION_CURRENT_SCOPE")
                .contains("PII_KEY_ROTATION_FORBIDDEN_DIRECT_AES_SECRET_SWAP")
                .contains("PII_LEGACY_CIPHER_ROTATION_RUNBOOK")
                .contains("PII_KEY_ROTATION_FUTURE_DUAL_KEY_CONTRACT")
                .contains("PII_KEY_ROTATION_EVIDENCE")
                .contains("POST /api/admin/users/pii-encryption-rotation")
                .contains("AES_ACTIVE_KEY_ID")
                .contains("AES_DECRYPT_KEYS")
                .contains("PII key rotation runbook contract passed");
    }
}
