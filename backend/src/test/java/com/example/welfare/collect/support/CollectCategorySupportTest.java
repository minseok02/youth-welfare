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
    @DisplayName("복지로 관심주제 csv의 첫 label만 compat category로 사용한다")
    void mapsBokjiroCompatCategoryFromFirstTheme() {
        assertThat(CollectCategorySupport.mapBokjiroCompatCategory("민간금융,주거")).isEqualTo("금융·생활지원");
        assertThat(CollectCategorySupport.mapBokjiroCompatCategory("보호·돌봄,문화·여가")).isEqualTo("가족·돌봄");
        assertThat(CollectCategorySupport.mapBokjiroCompatCategory(null)).isEqualTo("기타");
    }
}
