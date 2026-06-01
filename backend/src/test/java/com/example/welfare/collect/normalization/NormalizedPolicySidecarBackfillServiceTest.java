package com.example.welfare.collect.normalization;

import com.example.welfare.collect.dto.BokjiroLocalDto;
import com.example.welfare.collect.dto.Gov24ServiceListDto;
import com.example.welfare.collect.entity.RawApiPayload;
import com.example.welfare.collect.gateway.BokjiroDetailClient;
import com.example.welfare.collect.mapper.WelfareServiceMapper;
import com.example.welfare.collect.repository.NormalizedPolicySidecarBackfillReadRepository;
import com.example.welfare.collect.repository.NormalizedPolicySidecarBackfillTarget;
import com.example.welfare.collect.service.CollectPolicyAggregateApplyService;
import com.example.welfare.policy.entity.WelfareService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NormalizedPolicySidecarBackfillServiceTest {

    @Mock
    private NormalizedPolicySidecarBackfillReadRepository normalizedPolicySidecarBackfillReadRepository;
    @Mock
    private CollectPolicyAggregateApplyService collectPolicyAggregateApplyService;

    private final WelfareServiceMapper welfareServiceMapper = new WelfareServiceMapper();
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private NormalizedPolicySidecarBackfillService service;

    @BeforeEach
    void setUp() {
        service = new NormalizedPolicySidecarBackfillService(
                normalizedPolicySidecarBackfillReadRepository,
                welfareServiceMapper,
                collectPolicyAggregateApplyService,
                objectMapper
        );
    }

    @Test
    @DisplayName("복지로 list raw payload를 읽어 taxonomy/facts aggregate를 다시 sidecar writer로 보낸다")
    void backfillBokjiroListSidecars() throws Exception {
        BokjiroLocalDto.Item item = new BokjiroLocalDto.Item();
        ReflectionTestUtils.setField(item, "servId", "LOCAL-1");
        ReflectionTestUtils.setField(item, "servNm", "청년 문화패스");
        ReflectionTestUtils.setField(item, "servDgst", "만 19세 이상 34세 이하 청년에게 문화 활동비를 지원합니다.");
        ReflectionTestUtils.setField(item, "lifeNmArray", "청년");
        ReflectionTestUtils.setField(item, "intrsThemaNmArray", "문화·여가");
        ReflectionTestUtils.setField(item, "trgterIndvdlNmArray", "청년,1인가구");
        ReflectionTestUtils.setField(item, "aplyMtdNm", "온라인 신청 가능");
        ReflectionTestUtils.setField(item, "servDtlLink", "https://bokjiro.go.kr/service/LOCAL-1");
        ReflectionTestUtils.setField(item, "sprtCycNm", "분기별");
        ReflectionTestUtils.setField(item, "srvPvsnNm", "바우처");

        RawApiPayload raw = RawApiPayload.builder()
                .sourceType(WelfareService.SourceType.BOKJIRO_LOCAL)
                .sourceId("LOCAL-1")
                .apiCategory(RawApiPayload.ApiCategory.LIST)
                .payloadJson(objectMapper.writeValueAsString(item))
                .payloadHash("hash")
                .fetchedAt(LocalDateTime.now())
                .build();

        WelfareService saved = WelfareService.builder()
                .id(101L)
                .sourceType(WelfareService.SourceType.BOKJIRO_LOCAL)
                .sourceId("LOCAL-1")
                .title("청년 문화패스")
                .status(WelfareService.ServiceStatus.ACTIVE)
                .build();

        given(normalizedPolicySidecarBackfillReadRepository.findTargetsBySourceTypeAndApiCategoryOrderByFetchedAtAsc(
                WelfareService.SourceType.BOKJIRO_CENTRAL,
                RawApiPayload.ApiCategory.LIST,
                10
        )).willReturn(List.of());
        given(normalizedPolicySidecarBackfillReadRepository.findTargetsBySourceTypeAndApiCategoryOrderByFetchedAtAsc(
                WelfareService.SourceType.BOKJIRO_LOCAL,
                RawApiPayload.ApiCategory.LIST,
                10
        )).willReturn(List.of(new NormalizedPolicySidecarBackfillTarget(raw, saved)));
        NormalizedPolicySidecarBackfillService.BackfillResult result = service.backfillBokjiroListSidecars(10);

        assertThat(result.scannedCount()).isEqualTo(1);
        assertThat(result.upsertedCount()).isEqualTo(1);
        assertThat(result.missingServiceCount()).isZero();
        assertThat(result.failedCount()).isZero();

        ArgumentCaptor<NormalizedPolicyAggregate> aggregateCaptor = ArgumentCaptor.forClass(NormalizedPolicyAggregate.class);
        verify(collectPolicyAggregateApplyService).applySidecarBackfill(eq(saved), aggregateCaptor.capture());
        assertThat(aggregateCaptor.getValue().taxonomyTerms())
                .extracting(NormalizedPolicyAggregate.TaxonomyTerm::termGroup,
                        NormalizedPolicyAggregate.TaxonomyTerm::termLabel)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("LIFE_STAGE", "청년"),
                        org.assertj.core.groups.Tuple.tuple("INTEREST_THEME", "문화·여가"),
                        org.assertj.core.groups.Tuple.tuple("TARGET_GROUP", "청년"),
                        org.assertj.core.groups.Tuple.tuple("TARGET_GROUP", "1인가구")
                );
        assertThat(aggregateCaptor.getValue().facts())
                .singleElement()
                .satisfies(fact -> assertThat(fact.factMergeKey()).isEqualTo("BK_AGE_ELIGIBILITY"));
    }

    @Test
    @DisplayName("복지로 detail raw payload를 읽어 detail facts aggregate를 다시 sidecar writer로 보낸다")
    void backfillBokjiroDetailSidecars() throws Exception {
        BokjiroDetailClient.DetailPayload payload = BokjiroDetailClient.DetailPayload.builder()
                .targetDetail("만 20세 이상 39세 이하 청년")
                .supportDetail("문화 활동비 지원")
                .applyMethodDetail("온라인 신청, 2026.12.31 까지 접수")
                .selectionCriteria("연령 요건 확인")
                .build();

        RawApiPayload raw = RawApiPayload.builder()
                .sourceType(WelfareService.SourceType.BOKJIRO_LOCAL)
                .sourceId("LOCAL-2")
                .apiCategory(RawApiPayload.ApiCategory.DETAIL)
                .payloadJson(objectMapper.writeValueAsString(payload))
                .payloadHash("hash")
                .fetchedAt(LocalDateTime.now())
                .build();

        WelfareService saved = WelfareService.builder()
                .id(102L)
                .sourceType(WelfareService.SourceType.BOKJIRO_LOCAL)
                .sourceId("LOCAL-2")
                .title("청년 문화패스")
                .detailUrl("https://bokjiro.go.kr/service/LOCAL-2")
                .unifiedCategory("문화·여가")
                .status(WelfareService.ServiceStatus.ACTIVE)
                .build();

        given(normalizedPolicySidecarBackfillReadRepository.findTargetsBySourceTypeAndApiCategoryOrderByFetchedAtAsc(
                WelfareService.SourceType.BOKJIRO_CENTRAL,
                RawApiPayload.ApiCategory.DETAIL,
                10
        )).willReturn(List.of());
        given(normalizedPolicySidecarBackfillReadRepository.findTargetsBySourceTypeAndApiCategoryOrderByFetchedAtAsc(
                WelfareService.SourceType.BOKJIRO_LOCAL,
                RawApiPayload.ApiCategory.DETAIL,
                10
        )).willReturn(List.of(new NormalizedPolicySidecarBackfillTarget(raw, saved)));

        NormalizedPolicySidecarBackfillService.BackfillResult result = service.backfillBokjiroDetailSidecars(10);

        assertThat(result.scannedCount()).isEqualTo(1);
        assertThat(result.upsertedCount()).isEqualTo(1);
        assertThat(result.missingServiceCount()).isZero();
        assertThat(result.failedCount()).isZero();

        ArgumentCaptor<NormalizedPolicyAggregate> aggregateCaptor = ArgumentCaptor.forClass(NormalizedPolicyAggregate.class);
        verify(collectPolicyAggregateApplyService).applySidecarBackfill(eq(saved), aggregateCaptor.capture());
        assertThat(aggregateCaptor.getValue().facts())
                .extracting(NormalizedPolicyAggregate.Fact::factMergeKey)
                .containsExactlyInAnyOrder("BK_AGE_ELIGIBILITY", "BK_APPLY_END_DATE");
    }

    @Test
    @DisplayName("매칭 서비스가 없으면 missingServiceCount만 올리고 writer는 호출하지 않는다")
    void backfillCountsMissingService() throws Exception {
        BokjiroDetailClient.DetailPayload payload = BokjiroDetailClient.DetailPayload.builder()
                .supportDetail("지원 내용")
                .build();
        RawApiPayload raw = RawApiPayload.builder()
                .sourceType(WelfareService.SourceType.BOKJIRO_CENTRAL)
                .sourceId("CENTRAL-1")
                .apiCategory(RawApiPayload.ApiCategory.DETAIL)
                .payloadJson(objectMapper.writeValueAsString(payload))
                .payloadHash("hash")
                .fetchedAt(LocalDateTime.now())
                .build();

        given(normalizedPolicySidecarBackfillReadRepository.findTargetsBySourceTypeAndApiCategoryOrderByFetchedAtAsc(
                WelfareService.SourceType.BOKJIRO_CENTRAL,
                RawApiPayload.ApiCategory.DETAIL,
                10
        )).willReturn(List.of(new NormalizedPolicySidecarBackfillTarget(raw, null)));
        given(normalizedPolicySidecarBackfillReadRepository.findTargetsBySourceTypeAndApiCategoryOrderByFetchedAtAsc(
                WelfareService.SourceType.BOKJIRO_LOCAL,
                RawApiPayload.ApiCategory.DETAIL,
                10
        )).willReturn(List.of());

        NormalizedPolicySidecarBackfillService.BackfillResult result = service.backfillBokjiroDetailSidecars(10);

        assertThat(result.scannedCount()).isEqualTo(1);
        assertThat(result.upsertedCount()).isZero();
        assertThat(result.missingServiceCount()).isEqualTo(1);
        assertThat(result.failedCount()).isZero();
        verify(collectPolicyAggregateApplyService, never()).applySidecarBackfill(any(), any());
    }

    @Test
    @DisplayName("payload 역직렬화 실패는 failedCount로 집계한다")
    void backfillCountsInvalidPayloadFailure() {
        RawApiPayload raw = RawApiPayload.builder()
                .sourceType(WelfareService.SourceType.BOKJIRO_LOCAL)
                .sourceId("LOCAL-BAD")
                .apiCategory(RawApiPayload.ApiCategory.LIST)
                .payloadJson("{invalid-json")
                .payloadHash("hash")
                .fetchedAt(LocalDateTime.now())
                .build();

        WelfareService saved = WelfareService.builder()
                .id(103L)
                .sourceType(WelfareService.SourceType.BOKJIRO_LOCAL)
                .sourceId("LOCAL-BAD")
                .status(WelfareService.ServiceStatus.ACTIVE)
                .build();

        given(normalizedPolicySidecarBackfillReadRepository.findTargetsBySourceTypeAndApiCategoryOrderByFetchedAtAsc(
                WelfareService.SourceType.BOKJIRO_CENTRAL,
                RawApiPayload.ApiCategory.LIST,
                10
        )).willReturn(List.of());
        given(normalizedPolicySidecarBackfillReadRepository.findTargetsBySourceTypeAndApiCategoryOrderByFetchedAtAsc(
                WelfareService.SourceType.BOKJIRO_LOCAL,
                RawApiPayload.ApiCategory.LIST,
                10
        )).willReturn(List.of(new NormalizedPolicySidecarBackfillTarget(raw, saved)));
        NormalizedPolicySidecarBackfillService.BackfillResult result = service.backfillBokjiroListSidecars(10);

        assertThat(result.scannedCount()).isEqualTo(1);
        assertThat(result.upsertedCount()).isZero();
        assertThat(result.missingServiceCount()).isZero();
        assertThat(result.failedCount()).isEqualTo(1);
        verify(collectPolicyAggregateApplyService, never()).applySidecarBackfill(any(), any());
    }

    @Test
    @DisplayName("Gov24 list raw payload를 읽어 summary label aggregate를 다시 sidecar writer로 보낸다")
    void backfillGov24ListSidecars() throws Exception {
        Gov24ServiceListDto.Item item = new Gov24ServiceListDto.Item();
        ReflectionTestUtils.setField(item, "serviceId", "GOV24-1");
        ReflectionTestUtils.setField(item, "serviceName", "청년 임대료 지원");
        ReflectionTestUtils.setField(item, "servicePurposeSummary", "청년에게 임대료를 지원합니다.");
        ReflectionTestUtils.setField(item, "serviceField", "주거");
        ReflectionTestUtils.setField(item, "userType", "개인");
        ReflectionTestUtils.setField(item, "supportType", "현금");

        NormalizedPolicyAggregate directAggregate = welfareServiceMapper.toNormalizedGov24(item);
        assertThat(directAggregate.taxonomy().summaryLabels())
                .containsEntry("GOV24_SERVICE_FIELD", "주거")
                .containsEntry("GOV24_USER_TYPE", "개인")
                .containsEntry("GOV24_BENEFIT_TYPE", "현금");

        RawApiPayload raw = RawApiPayload.builder()
                .sourceType(WelfareService.SourceType.GOV24)
                .sourceId("GOV24-1")
                .apiCategory(RawApiPayload.ApiCategory.LIST)
                .payloadJson(objectMapper.writeValueAsString(item))
                .payloadHash("hash")
                .fetchedAt(LocalDateTime.now())
                .build();

        WelfareService saved = WelfareService.builder()
                .id(201L)
                .sourceType(WelfareService.SourceType.GOV24)
                .sourceId("GOV24-1")
                .title("청년 임대료 지원")
                .status(WelfareService.ServiceStatus.ACTIVE)
                .build();

        given(normalizedPolicySidecarBackfillReadRepository.findTargetsBySourceTypeAndApiCategoryOrderByFetchedAtAsc(
                WelfareService.SourceType.GOV24,
                RawApiPayload.ApiCategory.LIST,
                10
        )).willReturn(List.of(new NormalizedPolicySidecarBackfillTarget(raw, saved)));

        NormalizedPolicySidecarBackfillService.BackfillResult result = service.backfillGov24ListSidecars(10);

        assertThat(result.scannedCount()).isEqualTo(1);
        assertThat(result.upsertedCount()).isEqualTo(1);
        assertThat(result.missingServiceCount()).isZero();
        assertThat(result.failedCount()).isZero();

        ArgumentCaptor<NormalizedPolicyAggregate> aggregateCaptor = ArgumentCaptor.forClass(NormalizedPolicyAggregate.class);
        verify(collectPolicyAggregateApplyService).applySidecarBackfill(eq(saved), aggregateCaptor.capture());
        assertThat(aggregateCaptor.getValue().taxonomy().summaryLabels())
                .containsEntry("GOV24_SERVICE_FIELD", "주거")
                .containsEntry("GOV24_USER_TYPE", "개인")
                .containsEntry("GOV24_BENEFIT_TYPE", "현금");
    }

    @Test
    @DisplayName("Gov24 support raw payload를 읽어 사업체/업종/창업 조건 facts를 다시 생성한다")
    void backfillGov24SupportConditionSidecars() throws Exception {
        Map<String, Object> payload = Map.of(
                "서비스ID", "GOV24-SUPPORT-1",
                "서비스명", "소상공인 제조업 창업 지원",
                "conditions", Map.of(
                        "JA1101", "Y",
                        "JA1202", 1,
                        "JA2101", true,
                        "JA2203", "Y"
                )
        );
        RawApiPayload raw = RawApiPayload.builder()
                .sourceType(WelfareService.SourceType.GOV24)
                .sourceId("GOV24-SUPPORT-1")
                .apiCategory(RawApiPayload.ApiCategory.SUPPORT)
                .payloadJson(objectMapper.writeValueAsString(payload))
                .payloadHash("hash")
                .fetchedAt(LocalDateTime.now())
                .build();

        WelfareService saved = WelfareService.builder()
                .id(202L)
                .sourceType(WelfareService.SourceType.GOV24)
                .sourceId("GOV24-SUPPORT-1")
                .title("소상공인 제조업 창업 지원")
                .status(WelfareService.ServiceStatus.ACTIVE)
                .build();

        given(normalizedPolicySidecarBackfillReadRepository.findTargetsBySourceTypeAndApiCategoryOrderByFetchedAtAsc(
                WelfareService.SourceType.GOV24,
                RawApiPayload.ApiCategory.SUPPORT,
                10
        )).willReturn(List.of(new NormalizedPolicySidecarBackfillTarget(raw, saved)));

        NormalizedPolicySidecarBackfillService.BackfillResult result = service.backfillGov24SupportConditionSidecars(10);

        assertThat(result.scannedCount()).isEqualTo(1);
        assertThat(result.upsertedCount()).isEqualTo(1);
        assertThat(result.missingServiceCount()).isZero();
        assertThat(result.failedCount()).isZero();

        ArgumentCaptor<NormalizedPolicyAggregate> aggregateCaptor = ArgumentCaptor.forClass(NormalizedPolicyAggregate.class);
        verify(collectPolicyAggregateApplyService).replaceFactCodeSet(
                eq(saved),
                eq("GOV24_SUPPORT_CONDITION"),
                aggregateCaptor.capture()
        );
        assertThat(aggregateCaptor.getValue().facts())
                .extracting(NormalizedPolicyAggregate.Fact::factMergeKey)
                .containsExactlyInAnyOrder(
                        "GOV24_BUSINESS_STAGE:JA1101",
                        "GOV24_INDUSTRY:JA1202",
                        "GOV24_BUSINESS_TYPE:JA2101",
                        "GOV24_INDUSTRY:JA2203"
                );
    }
}
