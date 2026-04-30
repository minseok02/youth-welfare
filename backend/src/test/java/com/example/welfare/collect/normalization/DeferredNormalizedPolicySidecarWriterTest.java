package com.example.welfare.collect.normalization;

import com.example.welfare.policy.entity.WelfareService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeferredNormalizedPolicySidecarWriterTest {

    private final DeferredNormalizedPolicySidecarWriter writer =
            new DeferredNormalizedPolicySidecarWriter(new NormalizedFactMergeSupport());

    @Test
    void upsert_allowsYouthMidRawAliasWhenSummaryYouthMidIsNull() {
        WelfareService service = WelfareService.builder()
                .id(1L)
                .sourceType(WelfareService.SourceType.YOUTH)
                .build();

        NormalizedPolicyAggregate aggregate = NormalizedPolicyAggregate.builder()
                .core(NormalizedPolicyAggregate.Core.builder()
                        .sourceType(NormalizedPolicyAggregate.SourceType.YOUTH)
                        .sourceId("Y001")
                        .title("청년 역량 지원")
                        .status(NormalizedPolicyAggregate.ServiceStatus.ACTIVE)
                        .build())
                .taxonomy(NormalizedPolicyAggregate.TaxonomySummary.builder()
                        .youthMajor("일자리")
                        .youthMid(null)
                        .authority(NormalizedPolicyAggregate.Authority.OFFICIAL)
                        .confidence(BigDecimal.ONE)
                        .build())
                .taxonomyTerms(List.of(
                        NormalizedPolicyAggregate.TaxonomyTerm.builder()
                                .termGroup("YOUTH_MID")
                                .termLabel("취업")
                                .sourceField("category_sub")
                                .authority(NormalizedPolicyAggregate.Authority.OFFICIAL)
                                .sortOrder(0)
                                .build(),
                        NormalizedPolicyAggregate.TaxonomyTerm.builder()
                                .termGroup("YOUTH_MID_RAW_ALIAS")
                                .termLabel("온·오프라인교육")
                                .sourceField("category_sub")
                                .authority(NormalizedPolicyAggregate.Authority.OFFICIAL)
                                .sortOrder(1)
                                .build()
                ))
                .facts(List.of())
                .build();

        assertThatCode(() -> writer.upsert(service, aggregate))
                .doesNotThrowAnyException();
    }

    @Test
    void upsert_rejectsYouthMidRawAliasWhenSummaryYouthMidIsPresent() {
        WelfareService service = WelfareService.builder()
                .id(1L)
                .sourceType(WelfareService.SourceType.YOUTH)
                .build();

        NormalizedPolicyAggregate aggregate = NormalizedPolicyAggregate.builder()
                .core(NormalizedPolicyAggregate.Core.builder()
                        .sourceType(NormalizedPolicyAggregate.SourceType.YOUTH)
                        .sourceId("Y002")
                        .title("청년 역량 지원")
                        .status(NormalizedPolicyAggregate.ServiceStatus.ACTIVE)
                        .build())
                .taxonomy(NormalizedPolicyAggregate.TaxonomySummary.builder()
                        .youthMajor("일자리")
                        .youthMid("취업")
                        .authority(NormalizedPolicyAggregate.Authority.OFFICIAL)
                        .confidence(BigDecimal.ONE)
                        .build())
                .taxonomyTerms(List.of(
                        NormalizedPolicyAggregate.TaxonomyTerm.builder()
                                .termGroup("YOUTH_MID_RAW_ALIAS")
                                .termLabel("온·오프라인교육")
                                .sourceField("category_sub")
                                .authority(NormalizedPolicyAggregate.Authority.OFFICIAL)
                                .sortOrder(0)
                                .build()
                ))
                .facts(List.of())
                .build();

        assertThatThrownBy(() -> writer.upsert(service, aggregate))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("YOUTH_MID_RAW_ALIAS");
    }
}
