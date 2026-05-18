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
}
