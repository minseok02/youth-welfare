package com.example.welfare.recommend.service;

import com.example.welfare.policy.entity.ServiceTag;
import com.example.welfare.policy.entity.WelfareService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class YouthPolicyFilterTest {

    private YouthPolicyFilter filter;

    @BeforeEach
    void setUp() {
        filter = new YouthPolicyFilter();
    }

    @Test
    void includesWhenTitleExplicitlyMentionsYouth() {
        WelfareService service = baseService(WelfareService.SourceType.YOUTH, "청년정책");

        assertThat(filter.isYouthRelevant(service, List.of())).isTrue();
    }

    @Test
    void includesWhenLifeStageContainsYouth() {
        WelfareService service = WelfareService.builder()
                .sourceType(WelfareService.SourceType.BOKJIRO_CENTRAL)
                .sourceId("C1")
                .title("정책")
                .status(WelfareService.ServiceStatus.ACTIVE)
                .lifeStage("청년")
                .build();

        assertThat(filter.isYouthRelevant(service, List.of())).isTrue();
    }

    @Test
    void includesWhenTargetGroupContainsYouthSignal() {
        WelfareService service = baseService(WelfareService.SourceType.BOKJIRO_LOCAL, "주거 지원");
        List<ServiceTag> tags = List.of(tag(service, ServiceTag.TagType.TARGET_GROUP, "취업준비생"));

        assertThat(filter.isYouthRelevant(service, tags)).isTrue();
    }

    @Test
    void includesWhenAgeRangeOverlapsYouth() {
        WelfareService service = WelfareService.builder()
                .sourceType(WelfareService.SourceType.BOKJIRO_LOCAL)
                .sourceId("L2")
                .title("월세 지원")
                .status(WelfareService.ServiceStatus.ACTIVE)
                .minAge(19)
                .maxAge(34)
                .build();

        assertThat(filter.isYouthRelevant(service, List.of())).isTrue();
    }

    @Test
    void excludesGenericPolicyWithoutYouthSignal() {
        WelfareService service = WelfareService.builder()
                .sourceType(WelfareService.SourceType.BOKJIRO_LOCAL)
                .sourceId("L3")
                .title("재난적의료비 지원 사업")
                .description("과도한 의료비 부담을 겪는 가구 지원")
                .status(WelfareService.ServiceStatus.ACTIVE)
                .build();

        assertThat(filter.isYouthRelevant(service, List.of())).isFalse();
    }

    @Test
    void excludesWhenLifeStageIsBroadAcrossMultipleGenerations() {
        WelfareService service = WelfareService.builder()
                .sourceType(WelfareService.SourceType.BOKJIRO_CENTRAL)
                .sourceId("C2")
                .title("국가유공자 대부 지원")
                .status(WelfareService.ServiceStatus.ACTIVE)
                .lifeStage("청년,중장년,노년")
                .build();

        assertThat(filter.isYouthRelevant(service, List.of())).isFalse();
    }

    @Test
    void excludesWhenOnlyMinimumAgeExists() {
        WelfareService service = WelfareService.builder()
                .sourceType(WelfareService.SourceType.BOKJIRO_CENTRAL)
                .sourceId("C3")
                .title("일반 성인 지원")
                .status(WelfareService.ServiceStatus.ACTIVE)
                .minAge(18)
                .build();

        assertThat(filter.isYouthRelevant(service, List.of())).isFalse();
    }

    @Test
    void excludesWhenOnlyBroadLifeStageTagContainsYouth() {
        WelfareService service = baseService(WelfareService.SourceType.BOKJIRO_CENTRAL, "일반 복지");
        List<ServiceTag> tags = List.of(tag(service, ServiceTag.TagType.LIFE_STAGE, "청년,중장년"));

        assertThat(filter.isYouthRelevant(service, tags)).isFalse();
    }

    @Test
    void excludesWhenSplitLifeStageTagsContainYouthAndOlderStagesTogether() {
        WelfareService service = baseService(WelfareService.SourceType.BOKJIRO_CENTRAL, "일반 복지");
        List<ServiceTag> tags = List.of(
                tag(service, ServiceTag.TagType.LIFE_STAGE, "청년"),
                tag(service, ServiceTag.TagType.LIFE_STAGE, "중장년"),
                tag(service, ServiceTag.TagType.LIFE_STAGE, "노년")
        );

        assertThat(filter.isYouthRelevant(service, tags)).isFalse();
    }

    @Test
    void includesStructuredLocalPolicyWithMixedLifeStagesAndHousingSignals() {
        WelfareService service = WelfareService.builder()
                .sourceType(WelfareService.SourceType.BOKJIRO_LOCAL)
                .sourceId("L6")
                .title("주거급여수급자 월세보증금 지원")
                .unifiedCategory("주거")
                .status(WelfareService.ServiceStatus.ACTIVE)
                .build();
        List<ServiceTag> tags = List.of(
                tag(service, ServiceTag.TagType.LIFE_STAGE, "청년"),
                tag(service, ServiceTag.TagType.LIFE_STAGE, "중장년"),
                tag(service, ServiceTag.TagType.INTEREST_THEME, "주거"),
                tag(service, ServiceTag.TagType.KEYWORD, "월세보증금")
        );

        assertThat(filter.isYouthRelevant(service, tags)).isTrue();
    }

    private WelfareService baseService(WelfareService.SourceType sourceType, String title) {
        return WelfareService.builder()
                .sourceType(sourceType)
                .sourceId("S1")
                .title(title)
                .status(WelfareService.ServiceStatus.ACTIVE)
                .build();
    }

    private ServiceTag tag(WelfareService service, ServiceTag.TagType type, String value) {
        return ServiceTag.builder()
                .service(service)
                .tagType(type)
                .tagValue(value)
                .build();
    }
}
