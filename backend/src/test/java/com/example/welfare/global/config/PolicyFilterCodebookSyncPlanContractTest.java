package com.example.welfare.global.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PolicyFilterCodebookSyncPlanContractTest {

    private static final Path PLAN_DOC = Path.of("../docs/policy/policy-filter-codebook-sync-plan.md");
    private static final Path VERIFY_SCRIPT = Path.of("../deploy/smoke/verify-policy-filter-codebook-sync-plan.sh");

    @Test
    @DisplayName("정책 필터 codebook sync plan은 현재 계약과 runtime API 보류 결정을 고정한다")
    void planSeparatesCurrentContractFromRuntimeApiCandidate() throws IOException {
        String doc = Files.readString(PLAN_DOC);

        assertThat(doc)
                .contains("POLICY_FILTER_CODEBOOK_CURRENT_CONTRACT")
                .contains("POLICY_FILTER_CODEBOOK_NOT_RUNTIME_API_YET")
                .contains("POLICY_FILTER_CODEBOOK_GENERATED_CONSTANTS_CANDIDATE")
                .contains("POLICY_FILTER_CODEBOOK_PUBLIC_API_CANDIDATE")
                .contains("POLICY_FILTER_CODEBOOK_REOPEN_CONDITIONS")
                .contains("frontend/src/lib/policyFilterOptions.js")
                .contains("frontend/src/lib/policyFilterOptions.test.js")
                .contains("Gov24ServiceFieldSupport.managedLabels()")
                .contains("Gov24UserTypeSupport.managedTokens()")
                .contains("Gov24BenefitTypeSupport.managedTokens()")
                .contains("Gov24PolicyFilterSupportTest")
                .contains("`GET /api/policies/filter-codebooks` 같은 공개 runtime API는 지금 만들지 않습니다.");
    }

    @Test
    @DisplayName("정책 필터 codebook sync plan은 reference codebook API와 공개 필터 API 후보를 구분한다")
    void planSeparatesReferenceCodebookApiFromFilterApiCandidate() throws IOException {
        String doc = Files.readString(PLAN_DOC);

        assertThat(doc)
                .contains("OfficialCodebookReadService")
                .contains("/api/reference/official-codes")
                .contains("admin/reference 용도의 공식 코드북 조회")
                .contains("GET /api/policies/filter-codebooks")
                .contains("build-time generated constants")
                .contains("checked-in fallback")
                .contains("repository에 checked-in 합니다")
                .contains("runtime에서 달라집니다")
                .contains("배포 없이 option을 변경해야 합니다");
    }

    @Test
    @DisplayName("정책 필터 codebook sync plan 검증 스크립트는 같은 계약 식별자를 확인한다")
    void verificationScriptChecksPlanContractIds() throws IOException {
        String script = Files.readString(VERIFY_SCRIPT);

        assertThat(script)
                .contains("POLICY_FILTER_CODEBOOK_CURRENT_CONTRACT")
                .contains("POLICY_FILTER_CODEBOOK_NOT_RUNTIME_API_YET")
                .contains("POLICY_FILTER_CODEBOOK_GENERATED_CONSTANTS_CANDIDATE")
                .contains("POLICY_FILTER_CODEBOOK_PUBLIC_API_CANDIDATE")
                .contains("POLICY_FILTER_CODEBOOK_REOPEN_CONDITIONS")
                .contains("POLICY_FILTER_CODEBOOK_VERIFICATION")
                .contains("frontend/src/lib/policyFilterOptions.js")
                .contains("Gov24PolicyFilterSupportTest")
                .contains("GET /api/policies/filter-codebooks")
                .contains("policy filter codebook sync plan contract passed");
    }
}
