package com.example.welfare.collect.service;

import com.example.welfare.collect.dto.BokjiroCentralDto;
import com.example.welfare.collect.dto.Gov24ServiceDetailDto;
import com.example.welfare.collect.dto.Gov24ServiceListDto;
import com.example.welfare.collect.dto.Gov24SupportConditionsDto;
import com.example.welfare.collect.dto.YouthApiDto;
import com.example.welfare.collect.mapper.WelfareServiceMapper;
import com.example.welfare.collect.repository.RawApiPayloadCommandRepository;
import com.example.welfare.collect.repository.RawApiPayloadReadRepository;
import com.example.welfare.collect.support.CollectSourceRegistry;
import com.example.welfare.collect.support.ListCollectSourceBinding;
import com.example.welfare.collect.support.ListCollectSourceBindings;
import com.example.welfare.collect.normalization.NormalizedPolicyAggregate;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.entity.ServiceRegion;
import com.example.welfare.policy.entity.ServiceTag;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RawApiPayloadServiceTest {

    @Mock
    private RawApiPayloadCommandRepository rawApiPayloadCommandRepository;
    @Mock
    private RawApiPayloadReadRepository rawApiPayloadReadRepository;
    @Mock
    private WelfareServiceMapper welfareServiceMapper;

    private RawApiPayloadService rawApiPayloadService;

    @BeforeEach
    void setUp() {
        rawApiPayloadService = new RawApiPayloadService(
                rawApiPayloadCommandRepository,
                rawApiPayloadReadRepository,
                new ObjectMapper()
        );
    }

    @Test
    void saveBokjiroCentralListCreatesOrUpdatesRawPayload() {
        BokjiroCentralDto.Item item = new BokjiroCentralDto.Item();
        ListCollectSourceBinding<BokjiroCentralDto.Item> binding = ListCollectSourceBindings.bokjiroCentral(welfareServiceMapper);
        ReflectionTestUtils.setField(item, "servId", "WLF00000060");
        ReflectionTestUtils.setField(item, "servNm", "청년내일저축계좌");

        boolean saved = rawApiPayloadService.saveList(binding, item);

        assertThat(saved).isTrue();
        verify(rawApiPayloadCommandRepository).upsert(
                org.mockito.Mockito.eq(WelfareService.SourceType.BOKJIRO_CENTRAL),
                org.mockito.Mockito.eq("WLF00000060"),
                org.mockito.Mockito.eq(com.example.welfare.collect.entity.RawApiPayload.ApiCategory.LIST),
                any(String.class),
                any(String.class),
                any(java.time.LocalDateTime.class)
        );
    }

    @Test
    void saveSkipsWhenSourceIdMissing() {
        BokjiroCentralDto.Item item = new BokjiroCentralDto.Item();
        ListCollectSourceBinding<BokjiroCentralDto.Item> binding = ListCollectSourceBindings.bokjiroCentral(welfareServiceMapper);
        ReflectionTestUtils.setField(item, "servNm", "이름만 있는 정책");

        boolean saved = rawApiPayloadService.saveList(binding, item);

        assertThat(saved).isFalse();
        verifyNoInteractions(rawApiPayloadCommandRepository);
    }

    @Test
    @DisplayName("generic list binding 은 synthetic item raw payload 도 source-specific 메서드 없이 저장한다")
    void saveListSupportsSyntheticBinding() {
        SyntheticItem item = new SyntheticItem("Y-SYN-RAW-1", "합성 소스 정책");
        ListCollectSourceBinding<SyntheticItem> binding = new ListCollectSourceBinding<>(
                WelfareService.SourceType.YOUTH,
                SyntheticItem::sourceId,
                (items, stats) -> {
                },
                ignored -> WelfareService.builder()
                        .sourceType(WelfareService.SourceType.YOUTH)
                        .sourceId(item.sourceId())
                        .title(item.title())
                        .status(WelfareService.ServiceStatus.ACTIVE)
                        .build(),
                (ignored, entity) -> List.<ServiceRegion>of(),
                (ignored, entity) -> List.<ServiceTag>of(),
                ignored -> NormalizedPolicyAggregate.builder().build()
        );

        boolean saved = rawApiPayloadService.saveList(binding, item);

        assertThat(saved).isTrue();
        verify(rawApiPayloadCommandRepository).upsert(
                org.mockito.Mockito.eq(WelfareService.SourceType.YOUTH),
                org.mockito.Mockito.eq("Y-SYN-RAW-1"),
                org.mockito.Mockito.eq(com.example.welfare.collect.entity.RawApiPayload.ApiCategory.LIST),
                any(String.class),
                any(String.class),
                any(java.time.LocalDateTime.class)
        );
    }

    @Test
    void saveYouthListPreservesReferenceUrlsInRawPayload() {
        YouthApiDto.Item item = new YouthApiDto.Item();
        ListCollectSourceBinding<YouthApiDto.Item> binding = ListCollectSourceBindings.youth(welfareServiceMapper);
        ReflectionTestUtils.setField(item, "plcyNo", "Y-RAW-1");
        ReflectionTestUtils.setField(item, "plcyNm", "청년 센터 운영");
        ReflectionTestUtils.setField(item, "refUrlAddr1", "https://reference-one.example.com");
        ReflectionTestUtils.setField(item, "refUrlAddr2", "https://reference-two.example.com");

        boolean saved = rawApiPayloadService.saveList(binding, item);

        assertThat(saved).isTrue();
        org.mockito.ArgumentCaptor<String> payloadJsonCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(rawApiPayloadCommandRepository).upsert(
                eq(WelfareService.SourceType.YOUTH),
                eq("Y-RAW-1"),
                eq(com.example.welfare.collect.entity.RawApiPayload.ApiCategory.LIST),
                payloadJsonCaptor.capture(),
                any(String.class),
                any(java.time.LocalDateTime.class)
        );
        assertThat(payloadJsonCaptor.getValue()).contains("\"refUrlAddr1\":\"https://reference-one.example.com\"");
        assertThat(payloadJsonCaptor.getValue()).contains("\"refUrlAddr2\":\"https://reference-two.example.com\"");
    }

    @Test
    void saveGov24ListStoresJsonObjectPayload() {
        Gov24ServiceListDto.Item item = new Gov24ServiceListDto.Item();
        ListCollectSourceBinding<Gov24ServiceListDto.Item> binding = CollectSourceRegistry.GOV24.listBinding(welfareServiceMapper);
        ReflectionTestUtils.setField(item, "serviceId", "351050000109");
        ReflectionTestUtils.setField(item, "serviceName", "저소득주민 국민건강보험료 지원");
        ReflectionTestUtils.setField(item, "viewCount", 90430L);

        boolean saved = rawApiPayloadService.saveList(binding, item);

        assertThat(saved).isTrue();
        org.mockito.ArgumentCaptor<String> payloadJsonCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(rawApiPayloadCommandRepository).upsert(
                eq(WelfareService.SourceType.GOV24),
                eq("351050000109"),
                eq(com.example.welfare.collect.entity.RawApiPayload.ApiCategory.LIST),
                payloadJsonCaptor.capture(),
                any(String.class),
                any(java.time.LocalDateTime.class)
        );
        assertThat(payloadJsonCaptor.getValue()).contains("\"서비스ID\":\"351050000109\"");
        assertThat(payloadJsonCaptor.getValue()).contains("\"서비스명\":\"저소득주민 국민건강보험료 지원\"");
        assertThat(payloadJsonCaptor.getValue()).contains("\"조회수\":90430");
    }

    @Test
    void saveGov24DetailStoresJsonObjectPayload() {
        Gov24ServiceDetailDto.Item item = new Gov24ServiceDetailDto.Item();
        ReflectionTestUtils.setField(item, "serviceId", "351050000109");
        ReflectionTestUtils.setField(item, "serviceName", "저소득주민 국민건강보험료 지원");
        ReflectionTestUtils.setField(item, "servicePurpose", "상세 내용");

        boolean saved = rawApiPayloadService.saveGov24Detail("351050000109", item);

        assertThat(saved).isTrue();
        org.mockito.ArgumentCaptor<String> payloadJsonCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(rawApiPayloadCommandRepository).upsert(
                eq(WelfareService.SourceType.GOV24),
                eq("351050000109"),
                eq(com.example.welfare.collect.entity.RawApiPayload.ApiCategory.DETAIL),
                payloadJsonCaptor.capture(),
                any(String.class),
                any(java.time.LocalDateTime.class)
        );
        assertThat(payloadJsonCaptor.getValue()).contains("\"서비스ID\":\"351050000109\"");
        assertThat(payloadJsonCaptor.getValue()).contains("\"서비스명\":\"저소득주민 국민건강보험료 지원\"");
        assertThat(payloadJsonCaptor.getValue()).contains("\"서비스목적\":\"상세 내용\"");
    }

    @Test
    void saveGov24SupportConditionsStoresJsonObjectPayload() {
        Gov24SupportConditionsDto.Item item = new Gov24SupportConditionsDto.Item();
        ReflectionTestUtils.setField(item, "serviceId", "351050000109");
        ReflectionTestUtils.setField(item, "serviceName", "저소득주민 국민건강보험료 지원");
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> conditions = (java.util.Map<String, Object>) ReflectionTestUtils.getField(item, "conditions");
        conditions.put("JA0101", "Y");
        conditions.put("JA0111", 120);

        boolean saved = rawApiPayloadService.saveGov24SupportConditions("351050000109", item);

        assertThat(saved).isTrue();
        org.mockito.ArgumentCaptor<String> payloadJsonCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(rawApiPayloadCommandRepository).upsert(
                eq(WelfareService.SourceType.GOV24),
                eq("351050000109"),
                eq(com.example.welfare.collect.entity.RawApiPayload.ApiCategory.SUPPORT),
                payloadJsonCaptor.capture(),
                any(String.class),
                any(java.time.LocalDateTime.class)
        );
        assertThat(payloadJsonCaptor.getValue()).contains("\"서비스ID\":\"351050000109\"");
        assertThat(payloadJsonCaptor.getValue()).contains("\"서비스명\":\"저소득주민 국민건강보험료 지원\"");
        assertThat(payloadJsonCaptor.getValue()).contains("\"conditions\":{");
        assertThat(payloadJsonCaptor.getValue()).contains("\"JA0101\":\"Y\"");
        assertThat(payloadJsonCaptor.getValue()).contains("\"JA0111\":120");
    }

    @Test
    @DisplayName("upsert 실패는 false 를 반환해 호출자가 partial failure 로 집계할 수 있게 한다")
    void saveReturnsFalseWhenUpsertFails() {
        BokjiroCentralDto.Item item = new BokjiroCentralDto.Item();
        ListCollectSourceBinding<BokjiroCentralDto.Item> binding = ListCollectSourceBindings.bokjiroCentral(welfareServiceMapper);
        ReflectionTestUtils.setField(item, "servId", "WLF00000061");
        ReflectionTestUtils.setField(item, "servNm", "청년적금");
        given(rawApiPayloadCommandRepository.upsert(
                org.mockito.Mockito.eq(WelfareService.SourceType.BOKJIRO_CENTRAL),
                org.mockito.Mockito.eq("WLF00000061"),
                org.mockito.Mockito.eq(com.example.welfare.collect.entity.RawApiPayload.ApiCategory.LIST),
                any(String.class),
                any(String.class),
                any(java.time.LocalDateTime.class)
        )).willThrow(new IllegalStateException("upsert failed"));

        boolean saved = rawApiPayloadService.saveList(binding, item);

        assertThat(saved).isFalse();
    }

    private record SyntheticItem(String sourceId, String title) {
    }
}
