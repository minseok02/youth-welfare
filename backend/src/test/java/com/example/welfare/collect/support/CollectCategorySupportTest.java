package com.example.welfare.collect.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CollectCategorySupportTest {

    @Test
    @DisplayName("온통청년 category label을 compat category로 정규화한다")
    void mapsYouthCompatCategory() {
        assertThat(CollectCategorySupport.mapYouthCompatCategory("교육지원")).isEqualTo("교육·직업훈련");
        assertThat(CollectCategorySupport.mapYouthCompatCategory("참여권리")).isEqualTo("참여·기회");
        assertThat(CollectCategorySupport.mapYouthCompatCategory("알수없음")).isEqualTo("기타");
    }

    @Test
    @DisplayName("온통청년 복지문화 계열은 title/description/keyword 힌트로 문화와 건강 카테고리를 보정한다")
    void mapsYouthBroadWelfareCultureByHeuristics() {
        assertThat(CollectCategorySupport.mapYouthCompatCategory(
                "복지문화",
                "청년 문화예술인 활동 지원",
                "지역 문화예술 프로그램 참여 지원",
                "문화예술"
        )).isEqualTo("문화·여가");

        assertThat(CollectCategorySupport.mapYouthCompatCategory(
                "금융·복지·문화",
                "청년 정신건강 조기중재사업",
                "정신건강 심리상담 및 치료 연계",
                "상담"
        )).isEqualTo("건강·의료");
    }

    @Test
    @DisplayName("복지로 관심주제 csv의 첫 label만 compat category로 사용한다")
    void mapsBokjiroCompatCategoryFromFirstTheme() {
        assertThat(CollectCategorySupport.mapBokjiroCompatCategory("민간금융,주거")).isEqualTo("금융·생활지원");
        assertThat(CollectCategorySupport.mapBokjiroCompatCategory("보호·돌봄,문화·여가")).isEqualTo("가족·돌봄");
        assertThat(CollectCategorySupport.mapBokjiroCompatCategory(null)).isEqualTo("기타");
    }

    @Test
    @DisplayName("Gov24 생활안정 분야라도 명확한 월세/전세/주거비 지원은 주거로 보정한다")
    void mapsGov24LifeSupportHousingBenefitsToHousing() {
        assertThat(CollectCategorySupport.mapGov24CompatCategory(
                "생활안정",
                "청년월세 지원",
                "독립거주 무주택 청년에게 월 최대 20만원 임차료 지원",
                "현금"
        )).isEqualTo("주거");

        assertThat(CollectCategorySupport.mapGov24CompatCategory(
                "생활안정",
                "청년 전월세보증금 대출이자 지원",
                "전월세보증금 대출이자를 지원합니다.",
                "현금"
        )).isEqualTo("주거");
    }

    @Test
    @DisplayName("Gov24 주거·자립 분야의 순수 통장/자산형성 정책은 금융·생활지원으로 보정한다")
    void mapsGov24HousingSelfRelianceAssetFormationToFinanceLife() {
        assertThat(CollectCategorySupport.mapGov24CompatCategory(
                "주거·자립",
                "경기도 청년 노동자 통장",
                "매월 10만원 저축, 2년 만기시 580만원 지급",
                "현금"
        )).isEqualTo("금융·생활지원");

        assertThat(CollectCategorySupport.mapGov24CompatCategory(
                "주거·자립",
                "미래두배 청년통장",
                "근로 청년이 매월 적립하면 동일 금액을 지원",
                "현금"
        )).isEqualTo("금융·생활지원");
    }

    @Test
    @DisplayName("주거 목적 금융상품은 통장 키워드가 있어도 주거로 유지한다")
    void keepsHousingPurposeAssetProductsAsHousing() {
        assertThat(CollectCategorySupport.mapGov24CompatCategory(
                "주거·자립",
                "청년주택드림 청약통장",
                "저소득 무주택 청년 대상으로 청약통장 우대금리 및 비과세 혜택 제공",
                "현금"
        )).isEqualTo("주거");
    }

    @Test
    @DisplayName("복지로 주거 첫 테마라도 자산형성 자체가 본질이면 금융·생활지원으로 보정한다")
    void mapsBokjiroHousingAssetFormationToFinanceLife() {
        assertThat(CollectCategorySupport.mapBokjiroCompatCategory(
                "주거,생활지원",
                "전북청년 함께 두배적금",
                "취·창업, 주거, 결혼, 교육 등 자립기반 조성 목적의 자산 형성 지원",
                null
        )).isEqualTo("금융·생활지원");

        assertThat(CollectCategorySupport.mapBokjiroCompatCategory(
                "주거,서민금융",
                "청년 임차보증금 대출이자 지원",
                "무주택 청년의 임차보증금 대출이자를 지원",
                null
        )).isEqualTo("주거");
    }
}
