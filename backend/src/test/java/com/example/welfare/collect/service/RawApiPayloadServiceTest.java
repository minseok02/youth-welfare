package com.example.welfare.collect.service;

import com.example.welfare.collect.dto.BokjiroCentralDto;
import com.example.welfare.collect.mapper.WelfareServiceMapper;
import com.example.welfare.collect.repository.RawApiPayloadCommandRepository;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RawApiPayloadServiceTest {

    @Mock
    private RawApiPayloadCommandRepository rawApiPayloadCommandRepository;
    @Mock
    private WelfareServiceMapper welfareServiceMapper;

    private RawApiPayloadService rawApiPayloadService;

    @BeforeEach
    void setUp() {
        rawApiPayloadService = new RawApiPayloadService(
                rawApiPayloadCommandRepository,
                new ObjectMapper()
        );
    }

    @Test
    void saveBokjiroCentralListCreatesOrUpdatesRawPayload() {
        BokjiroCentralDto.Item item = new BokjiroCentralDto.Item();
        ListCollectSourceBinding<BokjiroCentralDto.Item> binding = ListCollectSourceBindings.bokjiroCentral(welfareServiceMapper);
        ReflectionTestUtils.setField(item, "servId", "WLF00000060");
        ReflectionTestUtils.setField(item, "servNm", "청년내일저축계좌");

        rawApiPayloadService.saveList(binding, item);

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

        rawApiPayloadService.saveList(binding, item);

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

        rawApiPayloadService.saveList(binding, item);

        verify(rawApiPayloadCommandRepository).upsert(
                org.mockito.Mockito.eq(WelfareService.SourceType.YOUTH),
                org.mockito.Mockito.eq("Y-SYN-RAW-1"),
                org.mockito.Mockito.eq(com.example.welfare.collect.entity.RawApiPayload.ApiCategory.LIST),
                any(String.class),
                any(String.class),
                any(java.time.LocalDateTime.class)
        );
    }

    private record SyntheticItem(String sourceId, String title) {
    }
}
