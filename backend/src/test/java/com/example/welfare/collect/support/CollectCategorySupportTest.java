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
}
