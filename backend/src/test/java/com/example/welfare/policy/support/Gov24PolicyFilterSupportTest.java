package com.example.welfare.policy.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class Gov24PolicyFilterSupportTest {

    @Test
    @DisplayName("Gov24 서비스분야 managed label은 프론트 필터 option과 같은 순서로 유지한다")
    void managedServiceFieldLabels() {
        assertThat(Gov24ServiceFieldSupport.managedLabels()).containsExactly(
                "생활안정",
                "농림축산어업",
                "보육·교육",
                "보건·의료",
                "임신·출산",
                "고용·창업",
                "문화·환경",
                "보호·돌봄",
                "행정·안전",
                "주거·자립"
        );
        assertThat(Gov24ServiceFieldSupport.normalizeManagedLabel(" 주거·자립 ")).isEqualTo("주거·자립");
        assertThat(Gov24ServiceFieldSupport.normalizeManagedLabel("알수없음")).isNull();
    }

    @Test
    @DisplayName("Gov24 사용자구분 managed token은 프론트 필터 option과 같은 순서로 유지한다")
    void managedUserTypeTokens() {
        assertThat(Gov24UserTypeSupport.managedTokens()).containsExactly(
                "개인",
                "가구",
                "법인/시설/단체",
                "소상공인"
        );
        assertThat(Gov24UserTypeSupport.normalizeManagedToken(" 개인 ")).isEqualTo("개인");
        assertThat(Gov24UserTypeSupport.normalizeManagedToken("청년")).isNull();
    }

    @Test
    @DisplayName("Gov24 지원유형 managed token은 프론트 필터 option과 같은 순서로 유지한다")
    void managedBenefitTypeTokens() {
        List<String> expected = List.of(
                "현금",
                "현물",
                "기타",
                "현금(감면)",
                "이용권",
                "서비스(의료)",
                "시설이용",
                "기타(교육)",
                "현금(보험)",
                "현금(장학금)",
                "현금(융자)",
                "기타(상담)",
                "서비스(돌봄)",
                "서비스(일자리)",
                "의료지원",
                "상담/법률지원",
                "기술지원",
                "문화/여가지원",
                "민원",
                "봉사/기부"
        );

        assertThat(Gov24BenefitTypeSupport.managedTokens()).containsExactlyElementsOf(expected);
        assertThat(Gov24BenefitTypeSupport.normalizeManagedToken(" 현금(장학금) ")).isEqualTo("현금(장학금)");
        assertThat(Gov24BenefitTypeSupport.normalizeManagedToken("생소한유형")).isNull();
    }
}
