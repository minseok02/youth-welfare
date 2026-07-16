package com.example.welfare.global.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RegionCodeUtilTest {

    @Test
    void returnsCurrentYouthSidoPrefixForSpecialSelfGoverningProvinces() {
        assertThat(RegionCodeUtil.getSidoCode("강원특별자치도")).isEqualTo("51");
        assertThat(RegionCodeUtil.getSidoCode("전북특별자치도")).isEqualTo("52");
    }

    @Test
    void returnsCurrentYouthRegionCodesForMovedOrRenumberedRegions() {
        assertThat(RegionCodeUtil.getRegionCode("대구광역시", "군위군")).isEqualTo("27720");
        assertThat(RegionCodeUtil.getRegionCode("강원특별자치도", "춘천시")).isEqualTo("51110");
        assertThat(RegionCodeUtil.getRegionCode("전북특별자치도", "고창군")).isEqualTo("52790");
    }

    @Test
    void resolvesWardLevelRegionCodesForMultiDistrictCities() {
        assertThat(RegionCodeUtil.getRegionCode("경기도", "장안구")).isEqualTo("41111");
        assertThat(RegionCodeUtil.getRegionCode("경기도", "분당구")).isEqualTo("41135");
        assertThat(RegionCodeUtil.getRegionCode("충청북도", "청원구")).isEqualTo("43114");
        assertThat(RegionCodeUtil.getRegionCode("충청남도", "동남구")).isEqualTo("44131");
        assertThat(RegionCodeUtil.getRegionCode("전북특별자치도", "덕진구")).isEqualTo("52113");
        assertThat(RegionCodeUtil.getRegionCode("경상북도", "북구")).isEqualTo("47113");
        assertThat(RegionCodeUtil.getRegionCode("경상남도", "진해구")).isEqualTo("48129");
    }

    @Test
    void noLongerTreatsGunwiAsGyeongbukRegionCode() {
        assertThat(RegionCodeUtil.getRegionCode("경상북도", "군위군")).isNull();
    }

    @Test
    void expandsHostOrgInferenceForMultiDistrictCityParents() {
        assertThat(RegionCodeUtil.inferFromHostOrg("수원시청"))
                .containsExactlyInAnyOrder("41111", "41113", "41115", "41117");
        assertThat(RegionCodeUtil.inferFromHostOrg("창원시청"))
                .containsExactlyInAnyOrder("48121", "48123", "48125", "48127", "48129");
    }

    @Test
    void expandsLocalAgencyInferenceForMultiDistrictCityParents() {
        List<String> regionCodes = RegionCodeUtil.inferRegionNamesFromLocalAgency("시군구", "수원시청")
                .stream()
                .map(RegionCodeUtil.RegionName::regionCode)
                .toList();

        assertThat(regionCodes).containsExactlyInAnyOrder("41111", "41113", "41115", "41117");
    }

    @Test
    void resolvesGov24LocalAgencyCodesToRegions() {
        List<String> jeonjuCodes = RegionCodeUtil.inferRegionNamesFromAgencyCode("4641000")
                .stream()
                .map(RegionCodeUtil.RegionName::regionCode)
                .toList();
        List<String> goseongCodes = RegionCodeUtil.inferRegionNamesFromAgencyCode("5420000")
                .stream()
                .map(RegionCodeUtil.RegionName::regionCode)
                .toList();

        assertThat(jeonjuCodes).containsExactlyInAnyOrder("52111", "52113");
        assertThat(goseongCodes).containsExactly("48820");
        assertThat(RegionCodeUtil.inferRegionNamesFromAgencyCode("1741000")).isEmpty();
    }

    @Test
    void doesNotInferRegionFromCommonWordsContainingRegionStem() {
        assertThat(RegionCodeUtil.inferRegionNamesFromText(
                "예산 범위 내에서 차등지원",
                "재무설계와 고령층 금융사기예방 교육",
                "다문화 가정 영주권자 자녀 포함",
                "지자체 담당자 역량강화",
                "취업활동계획 수립 참여수당"
        )).isEmpty();
    }

    @Test
    void stillInfersRegionFromUniqueSggNameWithAdministrativeSuffix() {
        List<String> regionCodes = RegionCodeUtil.inferRegionNamesFromText("서산시 청년 지원 사업")
                .stream()
                .map(RegionCodeUtil.RegionName::regionCode)
                .toList();

        assertThat(regionCodes).containsExactly("44210");
    }
}
