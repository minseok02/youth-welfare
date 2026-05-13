package com.example.welfare.policy.service;

import com.example.welfare.collect.dto.YouthApiDto;
import com.example.welfare.collect.entity.RawApiPayload;
import com.example.welfare.collect.mapper.WelfareServiceMapper;
import com.example.welfare.collect.normalization.NormalizedPolicyAggregate;
import com.example.welfare.collect.repository.NormalizedPolicySidecarBackfillReadRepository;
import com.example.welfare.collect.repository.NormalizedPolicySidecarBackfillTarget;
import com.example.welfare.collect.service.CollectPolicyAggregateApplyService;
import com.example.welfare.policy.dto.PolicyReferenceUrlBackfillResponse;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.entity.WelfareServiceDetail;
import com.example.welfare.policy.repository.WelfareServiceDetailRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PolicyReferenceUrlAdminServiceTest {

    @Mock
    private NormalizedPolicySidecarBackfillReadRepository normalizedPolicySidecarBackfillReadRepository;
    @Mock
    private WelfareServiceDetailRepository welfareServiceDetailRepository;
    @Mock
    private CollectPolicyAggregateApplyService collectPolicyAggregateApplyService;

    private PolicyReferenceUrlAdminService policyReferenceUrlAdminService;
    private final WelfareServiceMapper welfareServiceMapper = new WelfareServiceMapper();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        policyReferenceUrlAdminService = new PolicyReferenceUrlAdminService(
                normalizedPolicySidecarBackfillReadRepository,
                welfareServiceDetailRepository,
                welfareServiceMapper,
                collectPolicyAggregateApplyService,
                objectMapper
        );
    }

    @Test
    @DisplayName("청년 detail raw payload로 reference url backfill을 재적용한다")
    void rebuildReferenceUrlsUsesYouthDetailRawPayload() throws Exception {
        WelfareService service = WelfareService.builder()
                .id(11L)
                .sourceType(WelfareService.SourceType.YOUTH)
                .sourceId("Y-11")
                .title("청년 정책")
                .applyMethodName("온라인 접수")
                .status(WelfareService.ServiceStatus.ACTIVE)
                .build();
        YouthApiDto.Item detail = objectMapper.readValue("""
                {
                  "plcyNo":"Y-11",
                  "plcyAplyMthdCn":"온라인 접수 https://apply.example.com",
                  "plcySprtCn":"지원 내용 https://support.example.com",
                  "plcyExplnCn":"소개",
                  "aplyUrlAddr":"https://apply.example.com",
                  "refUrlAddr1":"https://guide.example.com"
                }
                """, YouthApiDto.Item.class);
        RawApiPayload raw = RawApiPayload.builder()
                .sourceType(WelfareService.SourceType.YOUTH)
                .sourceId("Y-11")
                .apiCategory(RawApiPayload.ApiCategory.DETAIL)
                .payloadJson(objectMapper.writeValueAsString(detail))
                .payloadHash("hash")
                .fetchedAt(LocalDateTime.now())
                .build();

        given(normalizedPolicySidecarBackfillReadRepository.findTargetsBySourceTypeAndApiCategoryOrderByFetchedAtAsc(
                WelfareService.SourceType.YOUTH,
                RawApiPayload.ApiCategory.DETAIL,
                0
        )).willReturn(List.of(new NormalizedPolicySidecarBackfillTarget(raw, service)));
        given(normalizedPolicySidecarBackfillReadRepository.findTargetsBySourceTypeAndApiCategoryOrderByFetchedAtAsc(
                WelfareService.SourceType.BOKJIRO_CENTRAL,
                RawApiPayload.ApiCategory.DETAIL,
                0
        )).willReturn(List.of());
        given(normalizedPolicySidecarBackfillReadRepository.findTargetsBySourceTypeAndApiCategoryOrderByFetchedAtAsc(
                WelfareService.SourceType.BOKJIRO_LOCAL,
                RawApiPayload.ApiCategory.DETAIL,
                0
        )).willReturn(List.of());
        given(welfareServiceDetailRepository.findByServiceId(11L)).willReturn(Optional.of(WelfareServiceDetail.builder().build()));

        PolicyReferenceUrlBackfillResponse response = policyReferenceUrlAdminService.rebuildReferenceUrls(null, 0, true);

        org.mockito.ArgumentCaptor<NormalizedPolicyAggregate> aggregateCaptor =
                org.mockito.ArgumentCaptor.forClass(NormalizedPolicyAggregate.class);
        verify(collectPolicyAggregateApplyService).applyCollectedDetail(eq(service), any(), aggregateCaptor.capture());
        assertNotNull(aggregateCaptor.getValue().detail());
        assertNotNull(aggregateCaptor.getValue().detail().referenceUrlsJson());
        assertTrue(aggregateCaptor.getValue().detail().referenceUrlsJson().contains("https://apply.example.com"));
        assertTrue(aggregateCaptor.getValue().detail().referenceUrlsJson().contains("https://guide.example.com"));
        assertEquals("all-detail-sources", response.scope());
        assertTrue(response.missingOnly());
        assertEquals(1, response.scannedCount());
        assertEquals(0, response.skippedCount());
        assertEquals(1, response.updatedCount());
        assertEquals(0, response.failedCount());
    }

    @Test
    @DisplayName("매칭 서비스가 없으면 missing count로 집계한다")
    void rebuildReferenceUrlsCountsMissingService() {
        RawApiPayload raw = RawApiPayload.builder()
                .sourceType(WelfareService.SourceType.BOKJIRO_LOCAL)
                .sourceId("B-99")
                .apiCategory(RawApiPayload.ApiCategory.DETAIL)
                .payloadJson("{}")
                .payloadHash("hash")
                .fetchedAt(LocalDateTime.now())
                .build();
        given(normalizedPolicySidecarBackfillReadRepository.findTargetsBySourceTypeAndApiCategoryOrderByFetchedAtAsc(
                WelfareService.SourceType.BOKJIRO_LOCAL,
                RawApiPayload.ApiCategory.DETAIL,
                25
        )).willReturn(List.of(new NormalizedPolicySidecarBackfillTarget(raw, null)));

        PolicyReferenceUrlBackfillResponse response = policyReferenceUrlAdminService.rebuildReferenceUrls(
                List.of(WelfareService.SourceType.BOKJIRO_LOCAL),
                25,
                true
        );

        assertEquals("selected-detail-sources", response.scope());
        assertEquals(List.of(WelfareService.SourceType.BOKJIRO_LOCAL), response.sourceTypes());
        assertEquals(1, response.scannedCount());
        assertEquals(0, response.skippedCount());
        assertEquals(0, response.updatedCount());
        assertEquals(1, response.missingServiceCount());
        assertEquals(0, response.failedCount());
    }

    @Test
    @DisplayName("missingOnly가 켜져 있으면 이미 reference url이 있는 detail은 건너뛴다")
    void rebuildReferenceUrlsSkipsExistingReferenceUrlsWhenMissingOnly() {
        WelfareService service = WelfareService.builder()
                .id(12L)
                .sourceType(WelfareService.SourceType.BOKJIRO_LOCAL)
                .sourceId("B-12")
                .title("복지로 정책")
                .status(WelfareService.ServiceStatus.ACTIVE)
                .build();
        RawApiPayload raw = RawApiPayload.builder()
                .sourceType(WelfareService.SourceType.BOKJIRO_LOCAL)
                .sourceId("B-12")
                .apiCategory(RawApiPayload.ApiCategory.DETAIL)
                .payloadJson("{}")
                .payloadHash("hash")
                .fetchedAt(LocalDateTime.now())
                .build();
        WelfareServiceDetail existing = WelfareServiceDetail.builder()
                .referenceUrlsJson("[{\"url\":\"https://existing.example.com\"}]")
                .build();

        given(normalizedPolicySidecarBackfillReadRepository.findTargetsBySourceTypeAndApiCategoryOrderByFetchedAtAsc(
                WelfareService.SourceType.BOKJIRO_LOCAL,
                RawApiPayload.ApiCategory.DETAIL,
                10
        )).willReturn(List.of(new NormalizedPolicySidecarBackfillTarget(raw, service)));
        given(welfareServiceDetailRepository.findByServiceId(12L)).willReturn(Optional.of(existing));

        PolicyReferenceUrlBackfillResponse response = policyReferenceUrlAdminService.rebuildReferenceUrls(
                List.of(WelfareService.SourceType.BOKJIRO_LOCAL),
                10,
                true
        );

        assertEquals(1, response.scannedCount());
        assertEquals(1, response.skippedCount());
        assertEquals(0, response.updatedCount());
        verify(collectPolicyAggregateApplyService, org.mockito.Mockito.never()).applyCollectedDetail(any(), any(), any());
    }
}
