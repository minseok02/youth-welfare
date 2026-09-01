package com.example.welfare.policy.support;

import com.example.welfare.policy.entity.WelfareService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PolicyRegionLabelSupportTest {

    @Test
    void resolvesLocalOrganizationHintToSpecificRegion() {
        WelfareService service = WelfareService.builder()
                .sourceType(WelfareService.SourceType.GOV24)
                .sourceId("gov24-local")
                .title("지역 정책")
                .hostOrg("충청남도 아산시")
                .build();

        String label = PolicyRegionLabelSupport.resolvePreferredRegionLabel(service, List.of(
                "충청남도 천안시 동남구",
                "충청남도 아산시",
                "충청남도 공주시"
        ));

        assertThat(label).isEqualTo("충청남도 아산시");
    }

    @Test
    void collapsesManyDistrictsInSameTopLevelRegionToSido() {
        WelfareService service = WelfareService.builder()
                .sourceType(WelfareService.SourceType.YOUTH)
                .sourceId("youth-chungnam")
                .title("미혼청년 주거급여 분리지급")
                .hostOrg("충청남도")
                .build();

        String label = PolicyRegionLabelSupport.resolvePreferredRegionLabel(service, List.of(
                "충청남도 천안시 동남구",
                "충청남도 천안시 서북구",
                "충청남도 공주시",
                "충청남도 보령시",
                "충청남도 아산시",
                "충청남도 서산시",
                "충청남도 논산시",
                "충청남도 계룡시"
        ));

        assertThat(label).isEqualTo("충청남도");
    }

    @Test
    void keepsCentralGov24RegionSuppressed() {
        WelfareService service = WelfareService.builder()
                .sourceType(WelfareService.SourceType.GOV24)
                .sourceId("gov24-central")
                .title("중앙 정책")
                .hostOrg("국토교통부")
                .build();

        String label = PolicyRegionLabelSupport.resolvePreferredRegionLabel(service, List.of(
                "충청남도 아산시"
        ));

        assertThat(label).isNull();
    }
}
