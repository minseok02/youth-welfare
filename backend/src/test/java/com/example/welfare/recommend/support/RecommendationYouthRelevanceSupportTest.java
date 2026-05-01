package com.example.welfare.recommend.support;

import com.example.welfare.policy.entity.ServiceTag;
import com.example.welfare.policy.entity.WelfareService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RecommendationYouthRelevanceSupportTest {

    private RecommendationYouthRelevanceSupport support;

    @BeforeEach
    void setUp() {
        support = new RecommendationYouthRelevanceSupport();
    }

    @Test
    void includesWhenTitleExplicitlyMentionsYouth() {
        WelfareService service = baseService(WelfareService.SourceType.YOUTH, "청년정책");

        assertThat(support.isYouthRelevant(service, List.of())).isTrue();
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

        assertThat(support.isYouthRelevant(service, List.of())).isFalse();
    }

    @Test
    void computesAgeOnlyBonusWhenYouthRangeOverlaps() {
        WelfareService service = WelfareService.builder()
                .sourceType(WelfareService.SourceType.BOKJIRO_LOCAL)
                .sourceId("L2")
                .title("월세 지원")
                .status(WelfareService.ServiceStatus.ACTIVE)
                .minAge(19)
                .maxAge(34)
                .build();

        assertThat(support.relevanceBonus(service, List.of())).isEqualTo(3.0);
    }

    @Test
    void excludesWhenSplitLifeStageTagsContainYouthAndOlderStagesTogether() {
        WelfareService service = baseService(WelfareService.SourceType.BOKJIRO_CENTRAL, "일반 복지");
        List<ServiceTag> tags = List.of(
                tag(service, ServiceTag.TagType.LIFE_STAGE, "청년"),
                tag(service, ServiceTag.TagType.LIFE_STAGE, "중장년"),
                tag(service, ServiceTag.TagType.LIFE_STAGE, "노년")
        );

        assertThat(support.isYouthRelevant(service, tags)).isFalse();
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
