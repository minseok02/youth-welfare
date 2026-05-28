package com.example.welfare.policy.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WelfareServiceTest {

    @Test
    @DisplayName("detail fallback은 기존 age range가 뒤집혀 있으면 sane한 bound로 복구한다")
    void applyDetailFallbacksRepairsInvalidAgeRange() {
        WelfareService service = WelfareService.builder()
                .sourceType(WelfareService.SourceType.BOKJIRO_LOCAL)
                .sourceId("WLF00004717")
                .title("인천형 청년월세 지원사업")
                .status(WelfareService.ServiceStatus.ACTIVE)
                .minAge(35)
                .maxAge(34)
                .build();

        service.applyDetailFallbacks(null, null, null, 39, null, null, null);

        assertThat(service.getMinAge()).isEqualTo(35);
        assertThat(service.getMaxAge()).isEqualTo(39);
    }
}
