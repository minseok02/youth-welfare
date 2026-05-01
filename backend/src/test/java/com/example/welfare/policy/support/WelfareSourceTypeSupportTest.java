package com.example.welfare.policy.support;

import com.example.welfare.collect.normalization.NormalizedPolicyAggregate;
import com.example.welfare.policy.entity.WelfareService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WelfareSourceTypeSupportTest {

    @Test
    @DisplayName("source type 문자열을 canonical enum/name 으로 정규화한다")
    void normalizesSupportedSourceTypes() {
        assertThat(WelfareSourceTypeSupport.parseNullable(" youth "))
                .isEqualTo(WelfareService.SourceType.YOUTH);
        assertThat(WelfareSourceTypeSupport.normalizeNullable(" bokjiro_central "))
                .isEqualTo("BOKJIRO_CENTRAL");
        assertThat(WelfareSourceTypeSupport.normalizeNullable(null))
                .isNull();
    }

    @Test
    @DisplayName("entity와 aggregate source type 모두 primary source system 으로 매핑한다")
    void mapsPrimarySourceSystemAcrossSourceEnums() {
        assertThat(WelfareSourceTypeSupport.primarySourceSystem(WelfareService.SourceType.YOUTH))
                .isEqualTo("YOUTH");
        assertThat(WelfareSourceTypeSupport.primarySourceSystem(NormalizedPolicyAggregate.SourceType.BOKJIRO_LOCAL))
                .isEqualTo("BOKJIRO");
    }

    @Test
    @DisplayName("지원하지 않는 source type 문자열은 예외를 던진다")
    void rejectsUnknownSourceType() {
        assertThatThrownBy(() -> WelfareSourceTypeSupport.parseNullable("gov24"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("gov24");
    }
}
