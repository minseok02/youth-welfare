package com.example.welfare.collect.service;

import com.example.welfare.collect.dto.Gov24ServiceDetailDto;
import com.example.welfare.collect.dto.InvertedAgeBackfillResponse;
import com.example.welfare.collect.dto.YouthApiDto;
import com.example.welfare.collect.entity.RawApiPayload;
import com.example.welfare.collect.mapper.WelfareServiceMapper;
import com.example.welfare.collect.repository.RawApiPayloadReadRepository;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.repository.WelfareServiceDetailRepository;
import com.example.welfare.policy.repository.WelfareServiceRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class InvertedAgeBackfillServiceTest {

    @Mock
    private WelfareServiceRepository welfareServiceRepository;
    @Mock
    private WelfareServiceDetailRepository welfareServiceDetailRepository;
    @Mock
    private RawApiPayloadReadRepository rawApiPayloadReadRepository;
    @Mock
    private CollectPolicyAggregateApplyService collectPolicyAggregateApplyService;

    private final WelfareServiceMapper welfareServiceMapper = new WelfareServiceMapper();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private InvertedAgeBackfillService invertedAgeBackfillService;

    @BeforeEach
    void setUp() {
        invertedAgeBackfillService = new InvertedAgeBackfillService(
                welfareServiceRepository,
                welfareServiceDetailRepository,
                rawApiPayloadReadRepository,
                welfareServiceMapper,
                collectPolicyAggregateApplyService,
                objectMapper
        );
    }

    @Test
    @DisplayName("청년 detail raw payload를 replay해 뒤집힌 age range를 repair한다")
    void backfillUsesYouthDetailRawPayload() throws Exception {
        WelfareService invalid = WelfareService.builder()
                .id(11L)
                .sourceType(WelfareService.SourceType.YOUTH)
                .sourceId("Y-11")
                .title("청년 정책")
                .description("청년 정책 설명")
                .supportContent("청년 지원")
                .applyMethodName("온라인 신청")
                .status(WelfareService.ServiceStatus.ACTIVE)
                .minAge(35)
                .maxAge(34)
                .build();
        WelfareService repaired = WelfareService.builder()
                .id(11L)
                .sourceType(WelfareService.SourceType.YOUTH)
                .sourceId("Y-11")
                .title("청년 정책")
                .status(WelfareService.ServiceStatus.ACTIVE)
                .minAge(35)
                .maxAge(39)
                .build();
        YouthApiDto.Item detail = new YouthApiDto.Item();
        ReflectionTestUtils.setField(detail, "plcyNo", "Y-11");
        ReflectionTestUtils.setField(detail, "plcyAplyMthdCn", "온라인 신청");
        ReflectionTestUtils.setField(detail, "sprtTrgtMinAge", 35);
        ReflectionTestUtils.setField(detail, "sprtTrgtMaxAge", 39);
        ReflectionTestUtils.setField(detail, "aplyUrlAddr", "https://apply.example.com");
        RawApiPayload rawPayload = RawApiPayload.builder()
                .sourceType(WelfareService.SourceType.YOUTH)
                .sourceId("Y-11")
                .apiCategory(RawApiPayload.ApiCategory.DETAIL)
                .payloadJson(objectMapper.writeValueAsString(detail))
                .payloadHash("hash-y11")
                .fetchedAt(LocalDateTime.now())
                .build();

        given(welfareServiceRepository.findInvalidAgeRangeTargetsBySourceType(
                eq(WelfareService.SourceType.YOUTH),
                any(Pageable.class)
        )).willReturn(List.of(invalid));
        given(welfareServiceRepository.findInvalidAgeRangeTargetsBySourceType(
                eq(WelfareService.SourceType.BOKJIRO_CENTRAL),
                any(Pageable.class)
        )).willReturn(List.of());
        given(welfareServiceRepository.findInvalidAgeRangeTargetsBySourceType(
                eq(WelfareService.SourceType.BOKJIRO_LOCAL),
                any(Pageable.class)
        )).willReturn(List.of());
        given(welfareServiceRepository.findInvalidAgeRangeTargetsBySourceType(
                eq(WelfareService.SourceType.GOV24),
                any(Pageable.class)
        )).willReturn(List.of());
        given(rawApiPayloadReadRepository.findBySourceTypeAndSourceIdAndApiCategory(
                WelfareService.SourceType.YOUTH,
                "Y-11",
                RawApiPayload.ApiCategory.DETAIL
        )).willReturn(Optional.of(rawPayload));
        given(welfareServiceDetailRepository.findByServiceId(11L)).willReturn(Optional.empty());
        given(welfareServiceRepository.findById(11L)).willReturn(Optional.of(repaired));

        InvertedAgeBackfillResponse response = invertedAgeBackfillService.backfill(null, 1000);

        ArgumentCaptor<com.example.welfare.collect.normalization.NormalizedPolicyAggregate> aggregateCaptor =
                ArgumentCaptor.forClass(com.example.welfare.collect.normalization.NormalizedPolicyAggregate.class);
        verify(collectPolicyAggregateApplyService).applyCollectedDetail(eq(invalid), eq(null), aggregateCaptor.capture());
        assertThat(aggregateCaptor.getValue().facts()).anySatisfy(fact -> {
            assertThat(fact.factGroup()).isEqualTo("AGE");
            assertThat(fact.rangeMinInt()).isEqualTo(35);
            assertThat(fact.rangeMaxInt()).isEqualTo(39);
        });
        assertThat(response.scope()).isEqualTo("all-source-types");
        assertThat(response.scannedCount()).isEqualTo(1);
        assertThat(response.repairedCount()).isEqualTo(1);
        assertThat(response.missingRawPayloadCount()).isZero();
        assertThat(response.unrepairedCount()).isZero();
        assertThat(response.failedCount()).isZero();
    }

    @Test
    @DisplayName("Gov24 detail raw payload를 replay하고 raw가 없으면 missingRawPayload로 집계한다")
    void backfillUsesGov24RawAndCountsMissingPayload() throws Exception {
        WelfareService gov24Invalid = WelfareService.builder()
                .id(21L)
                .sourceType(WelfareService.SourceType.GOV24)
                .sourceId("G-21")
                .title("Gov24 정책")
                .description("설명")
                .supportContent("지원")
                .status(WelfareService.ServiceStatus.ACTIVE)
                .minAge(40)
                .maxAge(39)
                .build();
        WelfareService youthMissingRaw = WelfareService.builder()
                .id(22L)
                .sourceType(WelfareService.SourceType.YOUTH)
                .sourceId("Y-22")
                .title("청년 정책")
                .status(WelfareService.ServiceStatus.ACTIVE)
                .minAge(35)
                .maxAge(34)
                .build();
        WelfareService stillInvalid = WelfareService.builder()
                .id(21L)
                .sourceType(WelfareService.SourceType.GOV24)
                .sourceId("G-21")
                .title("Gov24 정책")
                .status(WelfareService.ServiceStatus.ACTIVE)
                .minAge(40)
                .maxAge(39)
                .build();
        Gov24ServiceDetailDto.Item detail = new Gov24ServiceDetailDto.Item();
        ReflectionTestUtils.setField(detail, "serviceId", "G-21");
        ReflectionTestUtils.setField(detail, "supportTarget", "만 39세 이하 청년");
        ReflectionTestUtils.setField(detail, "selectionCriteria", "연령 조건");
        ReflectionTestUtils.setField(detail, "supportContent", "지원 내용");
        RawApiPayload gov24RawPayload = RawApiPayload.builder()
                .sourceType(WelfareService.SourceType.GOV24)
                .sourceId("G-21")
                .apiCategory(RawApiPayload.ApiCategory.DETAIL)
                .payloadJson(objectMapper.writeValueAsString(detail))
                .payloadHash("hash-g21")
                .fetchedAt(LocalDateTime.now())
                .build();

        given(welfareServiceRepository.findInvalidAgeRangeTargetsBySourceType(
                eq(WelfareService.SourceType.GOV24),
                any(Pageable.class)
        )).willReturn(List.of(gov24Invalid));
        given(welfareServiceRepository.findInvalidAgeRangeTargetsBySourceType(
                eq(WelfareService.SourceType.YOUTH),
                any(Pageable.class)
        )).willReturn(List.of(youthMissingRaw));
        given(rawApiPayloadReadRepository.findBySourceTypeAndSourceIdAndApiCategory(
                WelfareService.SourceType.GOV24,
                "G-21",
                RawApiPayload.ApiCategory.DETAIL
        )).willReturn(Optional.of(gov24RawPayload));
        given(rawApiPayloadReadRepository.findBySourceTypeAndSourceIdAndApiCategory(
                WelfareService.SourceType.YOUTH,
                "Y-22",
                RawApiPayload.ApiCategory.DETAIL
        )).willReturn(Optional.empty());
        given(welfareServiceRepository.findById(21L)).willReturn(Optional.of(stillInvalid));
        InvertedAgeBackfillResponse response = invertedAgeBackfillService.backfill(
                List.of(WelfareService.SourceType.GOV24, WelfareService.SourceType.YOUTH),
                25
        );

        verify(collectPolicyAggregateApplyService).applyCollectedDetail(eq(gov24Invalid), eq(null), any());
        verify(collectPolicyAggregateApplyService, never()).applyCollectedDetail(eq(youthMissingRaw), any(), any());
        assertThat(response.scope()).isEqualTo("selected-source-types");
        assertThat(response.sourceTypes()).containsExactly(WelfareService.SourceType.GOV24, WelfareService.SourceType.YOUTH);
        assertThat(response.scannedCount()).isEqualTo(2);
        assertThat(response.repairedCount()).isZero();
        assertThat(response.missingRawPayloadCount()).isEqualTo(1);
        assertThat(response.unrepairedCount()).isEqualTo(1);
        assertThat(response.failedCount()).isZero();
    }
}
