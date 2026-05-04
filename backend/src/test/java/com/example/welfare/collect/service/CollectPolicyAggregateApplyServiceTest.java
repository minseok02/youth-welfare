package com.example.welfare.collect.service;

import com.example.welfare.collect.normalization.NormalizedPolicyAggregate;
import com.example.welfare.collect.normalization.NormalizedPolicySidecarWriter;
import com.example.welfare.collect.repository.BokjiroDetailCommandRepository;
import com.example.welfare.policy.entity.ServiceTag;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.entity.WelfareServiceDetail;
import com.example.welfare.policy.service.SearchYouthRelevanceService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CollectPolicyAggregateApplyServiceTest {

    @Mock
    private BokjiroDetailCommandRepository bokjiroDetailCommandRepository;
    @Mock
    private SearchYouthRelevanceService searchYouthRelevanceService;
    @Mock
    private NormalizedPolicySidecarWriter normalizedPolicySidecarWriter;

    @Test
    @DisplayName("list collect 후처리는 aggregate가 있으면 sidecar upsert와 relevance refresh를 함께 수행한다")
    void applyCollectedItemWithAggregate() {
        CollectPolicyAggregateApplyService service = new CollectPolicyAggregateApplyService(
                bokjiroDetailCommandRepository,
                searchYouthRelevanceService,
                normalizedPolicySidecarWriter
        );
        WelfareService welfareService = WelfareService.builder()
                .id(11L)
                .sourceType(WelfareService.SourceType.YOUTH)
                .sourceId("Y-11")
                .title("청년 월세 지원")
                .build();
        NormalizedPolicyAggregate aggregate = normalizedAggregate("Y-11");
        List<ServiceTag> tags = List.of(ServiceTag.builder()
                .service(welfareService)
                .tagType(ServiceTag.TagType.KEYWORD)
                .tagValue("청년")
                .build());

        service.applyCollectedItem(welfareService, aggregate, tags);

        verify(normalizedPolicySidecarWriter).upsert(welfareService, aggregate);
        verify(searchYouthRelevanceService).refreshForService(welfareService, tags);
    }

    @Test
    @DisplayName("list collect 후처리는 aggregate가 없어도 relevance refresh는 수행한다")
    void applyCollectedItemWithoutAggregate() {
        CollectPolicyAggregateApplyService service = new CollectPolicyAggregateApplyService(
                bokjiroDetailCommandRepository,
                searchYouthRelevanceService,
                normalizedPolicySidecarWriter
        );
        WelfareService welfareService = WelfareService.builder()
                .id(12L)
                .sourceType(WelfareService.SourceType.YOUTH)
                .sourceId("Y-12")
                .title("청년 취업 지원")
                .build();

        service.applyCollectedItem(welfareService, null, List.of());

        verify(normalizedPolicySidecarWriter, never()).upsert(eq(welfareService), org.mockito.ArgumentMatchers.any());
        verify(searchYouthRelevanceService).refreshForService(welfareService, List.of());
    }

    @Test
    @DisplayName("detail collect 후처리는 detail row 저장, fallback 적용, sidecar upsert, relevance refresh를 함께 수행한다")
    void applyCollectedDetail() {
        CollectPolicyAggregateApplyService service = new CollectPolicyAggregateApplyService(
                bokjiroDetailCommandRepository,
                searchYouthRelevanceService,
                normalizedPolicySidecarWriter
        );
        WelfareService welfareService = WelfareService.builder()
                .id(13L)
                .sourceType(WelfareService.SourceType.BOKJIRO_CENTRAL)
                .sourceId("CENTRAL-13")
                .title("청년 월세 지원")
                .detailUrl("https://bokjiro.go.kr/service/CENTRAL-13")
                .build();
        WelfareServiceDetail existing = WelfareServiceDetail.builder()
                .id(301L)
                .service(welfareService)
                .supportDetail("old")
                .build();
        NormalizedPolicyAggregate aggregate = normalizedDetailAggregate("CENTRAL-13");

        service.applyCollectedDetail(welfareService, existing, aggregate);

        ArgumentCaptor<WelfareServiceDetail> detailCaptor = ArgumentCaptor.forClass(WelfareServiceDetail.class);
        verify(bokjiroDetailCommandRepository).save(detailCaptor.capture());
        assertThat(detailCaptor.getValue().getId()).isEqualTo(301L);
        assertThat(detailCaptor.getValue().getService()).isEqualTo(welfareService);
        assertThat(welfareService.getMinAge()).isEqualTo(20);
        assertThat(welfareService.getMaxAge()).isEqualTo(39);
        assertThat(welfareService.getApplyEndDate()).isEqualTo(java.time.LocalDate.of(2026, 12, 31));
        assertThat(welfareService.getIsOnlineApply()).isTrue();
        verify(normalizedPolicySidecarWriter).upsert(welfareService, aggregate);
        verify(searchYouthRelevanceService).refreshForService(welfareService);
    }

    @Test
    @DisplayName("sidecar backfill 후처리는 sidecar upsert만 수행한다")
    void applySidecarBackfill() {
        CollectPolicyAggregateApplyService service = new CollectPolicyAggregateApplyService(
                bokjiroDetailCommandRepository,
                searchYouthRelevanceService,
                normalizedPolicySidecarWriter
        );
        WelfareService welfareService = WelfareService.builder()
                .id(14L)
                .sourceType(WelfareService.SourceType.BOKJIRO_LOCAL)
                .sourceId("LOCAL-14")
                .title("청년 문화패스")
                .build();
        NormalizedPolicyAggregate aggregate = normalizedAggregate("LOCAL-14");

        service.applySidecarBackfill(welfareService, aggregate);

        verify(normalizedPolicySidecarWriter).upsert(welfareService, aggregate);
        verify(searchYouthRelevanceService, never()).refreshForService(eq(welfareService));
        verify(bokjiroDetailCommandRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    private NormalizedPolicyAggregate normalizedAggregate(String sourceId) {
        return NormalizedPolicyAggregate.builder()
                .core(NormalizedPolicyAggregate.Core.builder()
                        .sourceType(NormalizedPolicyAggregate.SourceType.YOUTH)
                        .sourceId(sourceId)
                        .title("title")
                        .build())
                .detail(NormalizedPolicyAggregate.Detail.builder().build())
                .facts(List.of())
                .taxonomyTerms(List.of())
                .build();
    }

    private NormalizedPolicyAggregate normalizedDetailAggregate(String sourceId) {
        return NormalizedPolicyAggregate.builder()
                .core(NormalizedPolicyAggregate.Core.builder()
                        .sourceType(NormalizedPolicyAggregate.SourceType.BOKJIRO_CENTRAL)
                        .sourceId(sourceId)
                        .title("title")
                        .build())
                .detail(NormalizedPolicyAggregate.Detail.builder()
                        .targetDetail("만 20세 이상 39세 이하 청년")
                        .supportDetail("문화 활동비 지원")
                        .applyMethodDetail("온라인 신청, 2026.12.31 까지 접수")
                        .build())
                .facts(List.of(
                        NormalizedPolicyAggregate.Fact.builder()
                                .factGroup("AGE")
                                .factMergeKey("BK_AGE_ELIGIBILITY")
                                .rangeMinInt(20)
                                .rangeMaxInt(39)
                                .build(),
                        NormalizedPolicyAggregate.Fact.builder()
                                .factGroup("APPLY_END_DATE")
                                .factMergeKey("BK_APPLY_END_DATE")
                                .dateValue(java.time.LocalDate.of(2026, 12, 31))
                                .build()
                ))
                .taxonomyTerms(List.of())
                .build();
    }
}
