package com.example.welfare.collect.support;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class YouthOfficialCodeSupportTest {

    @Test
    void resolvesOfficialProvisionMethodLabelFromCode() {
        assertThat(YouthOfficialCodeSupport.resolveProvisionMethodLabel("0042007", "온라인 신청"))
                .isEqualTo("대출보증");
    }

    @Test
    void fallsBackToExistingLabelWhenOfficialCodeIsMissing() {
        assertThat(YouthOfficialCodeSupport.resolveProvisionMethodLabel(null, "온라인 신청"))
                .isEqualTo("온라인 신청");
    }

    @Test
    void splitOfficialCodes_preservesOrderAndDeduplicates() {
        assertThat(YouthOfficialCodeSupport.splitOfficialCodes("0013003, 0013006,0013003,,0013001"))
                .containsExactly("0013003", "0013006", "0013001");
    }

    @Test
    void resolveEmploymentRequirementLabels_handlesMultiCodeCsv() {
        assertThat(YouthOfficialCodeSupport.resolveEmploymentRequirementLabels("0013003,0013006,0013003"))
                .containsExactly("미취업자", "(예비)창업자");
    }

    @Test
    void resolveEducationRequirementLabels_handlesMultiCodeCsv() {
        assertThat(YouthOfficialCodeSupport.resolveEducationRequirementLabels("0049005,0049006,0049007"))
                .containsExactly("대학 재학", "대졸 예정", "대학 졸업");
    }

    @Test
    void resolveEducationRequirementCodes_handlesMultiCodeCsv() {
        assertThat(YouthOfficialCodeSupport.resolveEducationRequirementCodes("0049005,0049006,0049005,0049007"))
                .containsExactly("0049005", "0049006", "0049007");
    }

    @Test
    void resolveSpecialRequirementLabels_handlesScalarAndUnknownCodes() {
        assertThat(YouthOfficialCodeSupport.resolveSpecialRequirementLabels("0014008,9999999,0014010"))
                .containsExactly("지역인재", "제한없음");
        assertThat(YouthOfficialCodeSupport.resolveSpecialRequirementCodes("0014008,9999999,0014010,0014008"))
                .containsExactly("0014008", "0014010");
    }

    @Test
    void resolveMaritalAndIncomeConditionLabels_handleBlankSafely() {
        assertThat(YouthOfficialCodeSupport.resolveMaritalStatusLabels("0055001,,0055003"))
                .containsExactly("기혼", "제한없음");
        assertThat(YouthOfficialCodeSupport.resolveIncomeConditionTypeLabels(null))
                .isEmpty();
    }
}
