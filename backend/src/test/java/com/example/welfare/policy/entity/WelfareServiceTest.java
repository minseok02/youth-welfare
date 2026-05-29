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

    @Test
    @DisplayName("detail fallback은 non-positive max age를 open upper bound로 정규화한다")
    void applyDetailFallbacksNormalizesNonPositiveMaxAgeToNull() {
        WelfareService service = WelfareService.builder()
                .sourceType(WelfareService.SourceType.YOUTH)
                .sourceId("YOUTH-OPEN-BOUND")
                .title("청년 정책")
                .status(WelfareService.ServiceStatus.ACTIVE)
                .minAge(19)
                .maxAge(0)
                .build();

        service.applyDetailFallbacks(null, null, 19, 0, null, null, null);

        assertThat(service.getMinAge()).isEqualTo(19);
        assertThat(service.getMaxAge()).isNull();
    }

    @Test
    @DisplayName("detail fallback은 max-only bound만 있어도 invalid age range를 upper-bound-only로 복구한다")
    void applyDetailFallbacksRepairsInvalidAgeRangeWithMaxOnlyBound() {
        WelfareService service = WelfareService.builder()
                .sourceType(WelfareService.SourceType.GOV24)
                .sourceId("GOV24-MAX-ONLY")
                .title("복합 연령 정책")
                .status(WelfareService.ServiceStatus.ACTIVE)
                .minAge(75)
                .maxAge(69)
                .build();

        service.applyDetailFallbacks(null, null, null, 13, null, null, null);

        assertThat(service.getMinAge()).isNull();
        assertThat(service.getMaxAge()).isEqualTo(13);
    }

    @Test
    @DisplayName("detail fallback에 sane한 age bound가 전혀 없으면 invalid age range를 unknown으로 비운다")
    void applyDetailFallbacksClearsInvalidAgeRangeWhenNoSaneFallbackExists() {
        WelfareService service = WelfareService.builder()
                .sourceType(WelfareService.SourceType.GOV24)
                .sourceId("GOV24-UNKNOWN-AGE")
                .title("모호한 연령 정책")
                .status(WelfareService.ServiceStatus.ACTIVE)
                .minAge(65)
                .maxAge(55)
                .build();

        service.applyDetailFallbacks(null, null, null, null, null, null, null);

        assertThat(service.getMinAge()).isNull();
        assertThat(service.getMaxAge()).isNull();
    }
}
