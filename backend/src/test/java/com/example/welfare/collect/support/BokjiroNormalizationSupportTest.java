package com.example.welfare.collect.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BokjiroNormalizationSupportTest {

    @Test
    @DisplayName("복지로 상세 문구에서 기초생활수급자와 차상위계층 버킷을 공통 규칙으로 추출한다")
    void extractsBeneficiaryLabels() {
        assertThat(BokjiroNormalizationSupport.beneficiaryLabels(
                "국민기초생활보장수급자 우대",
                "차상위 본인부담경감대상자 포함"
        )).containsExactly("기초생활수급자", "차상위계층");
    }

    @Test
    @DisplayName("넓은 비공식 취약계층 표현은 beneficiary bucket으로 승격하지 않는다")
    void ignoresBroadLabels() {
        assertThat(BokjiroNormalizationSupport.beneficiaryLabels(
                "취업취약계층 및 정보 소외계층 지원",
                "저소득 한부모가족 우대"
        )).isEmpty();
    }
}
