package com.example.welfare.policy.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CompatCategorySupportTest {

    @Test
    @DisplayName("compat label을 공통 code로 정규화한다")
    void resolvesCompatCode() {
        assertThat(CompatCategorySupport.compatCode("주거")).isEqualTo("HOUSING");
        assertThat(CompatCategorySupport.compatCode("금융·생활지원")).isEqualTo("FINANCE_LIFE_SUPPORT");
        assertThat(CompatCategorySupport.compatCode("기타")).isEqualTo("OTHER");
        assertThat(CompatCategorySupport.compatCode(null)).isNull();
    }

    @Test
    @DisplayName("priority bucket이 있는 compat label만 bucket으로 매핑한다")
    void resolvesPriorityBucket() {
        assertThat(CompatCategorySupport.priorityBucket("교육·직업훈련")).isEqualTo("EDUCATION");
        assertThat(CompatCategorySupport.priorityBucket("참여·기회")).isEqualTo("PARTICIPATION");
        assertThat(CompatCategorySupport.priorityBucket("건강·의료")).isNull();
    }
}
