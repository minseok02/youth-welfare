package com.example.welfare.collect.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class Gov24LabelTokenSupportTest {

    @Test
    @DisplayName("사용자구분 token은 순서를 유지하고 중복을 제거한다")
    void userTypeTokensPreserveOrderAndDeduplicate() {
        assertThat(Gov24LabelTokenSupport.userTypeTokens("개인||가구||개인||소상공인"))
                .containsExactly("개인", "가구", "소상공인");
    }

    @Test
    @DisplayName("지원유형 token은 공백을 정리하고 빈 token은 제거한다")
    void benefitTypeTokensTrimAndFilterBlank() {
        assertThat(Gov24LabelTokenSupport.benefitTypeTokens(" 현금 ||  || 서비스(의료) || 현금 "))
                .containsExactly("현금", "서비스(의료)");
    }

    @Test
    @DisplayName("null 또는 blank label은 빈 목록으로 처리한다")
    void emptyLabelReturnsEmptyList() {
        assertThat(Gov24LabelTokenSupport.userTypeTokens(null)).isEmpty();
        assertThat(Gov24LabelTokenSupport.benefitTypeTokens("   ")).isEmpty();
    }
}
