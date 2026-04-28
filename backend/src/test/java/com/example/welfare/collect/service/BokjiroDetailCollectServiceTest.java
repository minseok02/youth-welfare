package com.example.welfare.collect.service;

import com.example.welfare.collect.gateway.BokjiroDetailClient;
import com.example.welfare.policy.entity.ServiceTag;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.entity.WelfareServiceDetail;
import com.example.welfare.policy.repository.ServiceTagRepository;
import com.example.welfare.policy.repository.WelfareServiceDetailRepository;
import com.example.welfare.policy.repository.WelfareServiceRepository;
import com.example.welfare.policy.service.SearchYouthRelevanceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BokjiroDetailCollectServiceTest {

    @Mock
    private WelfareServiceRepository welfareServiceRepository;
    @Mock
    private WelfareServiceDetailRepository detailRepository;
    @Mock
    private ServiceTagRepository serviceTagRepository;
    @Mock
    private BokjiroDetailClient detailClient;
    @Mock
    private RawApiPayloadService rawApiPayloadService;
    @Mock
    private SearchYouthRelevanceService searchYouthRelevanceService;

    private BokjiroDetailCollectService service;

    @BeforeEach
    void setUp() {
        service = new BokjiroDetailCollectService(
                welfareServiceRepository,
                detailRepository,
                serviceTagRepository,
                detailClient,
                rawApiPayloadService,
                searchYouthRelevanceService
        );
        ReflectionTestUtils.setField(service, "maxCallsPerApiPerRun", 95);
        ReflectionTestUtils.setField(service, "requestIntervalMs", 0L);
        ReflectionTestUtils.setField(service, "retryMaxAttempts", 1);
        ReflectionTestUtils.setField(service, "retryBaseBackoffMs", 0L);
        ReflectionTestUtils.setField(service, "maxConsecutiveRateLimitHits", 5);
    }

    @Test
    @DisplayName("기본 상세 수집은 기존 detail row 가 있으면 skip 한다")
    void collectBokjiroDetailsSkipsExistingRows() {
        WelfareService central = welfareService(10L, WelfareService.SourceType.BOKJIRO_CENTRAL, "CENTRAL-1");

        given(welfareServiceRepository.findBySourceType(WelfareService.SourceType.BOKJIRO_CENTRAL))
                .willReturn(List.of(central));
        given(detailRepository.existsByServiceId(10L)).willReturn(true);

        CollectResult result = service.collectBokjiroDetailsResult(1);

        assertThat(result.requestedCount()).isZero();
        assertThat(result.savedCount()).isZero();
        assertThat(result.skippedCount()).isEqualTo(1);
        verify(detailClient, never()).fetchCentralWithStatus(any());
        verify(detailRepository, never()).save(any());
    }

    @Test
    @DisplayName("refresh 상세 수집은 기존 detail row 가 있어도 다시 조회해 같은 row 를 갱신한다")
    void collectBokjiroDetailsRefreshUpdatesExistingRows() {
        WelfareService central = welfareService(11L, WelfareService.SourceType.BOKJIRO_CENTRAL, "CENTRAL-2");
        WelfareServiceDetail existing = WelfareServiceDetail.builder()
                .id(101L)
                .service(central)
                .targetDetail("old-target")
                .supportDetail("old-support")
                .build();
        BokjiroDetailClient.DetailPayload payload = BokjiroDetailClient.DetailPayload.builder()
                .targetDetail("new-target")
                .supportDetail("new-support")
                .applyMethodDetail("online")
                .selectionCriteria("age")
                .contactList("02-123-4567")
                .supportCycle("MONTHLY")
                .provisionType("CASH")
                .build();
        List<ServiceTag> tags = List.of(ServiceTag.builder()
                .service(central)
                .tagType(ServiceTag.TagType.KEYWORD)
                .tagValue("청년")
                .build());

        given(welfareServiceRepository.findBySourceType(WelfareService.SourceType.BOKJIRO_CENTRAL))
                .willReturn(List.of(central));
        given(detailRepository.findByServiceId(11L)).willReturn(Optional.of(existing));
        given(detailClient.fetchCentralWithStatus("CENTRAL-2"))
                .willReturn(BokjiroDetailClient.FetchResult.success(payload));
        given(serviceTagRepository.findByServiceId(11L)).willReturn(tags);

        CollectResult result = service.collectBokjiroDetailsRefreshResult(1);

        assertThat(result.requestedCount()).isEqualTo(1);
        assertThat(result.savedCount()).isEqualTo(1);
        assertThat(result.skippedCount()).isZero();
        assertThat(result.metadataJson()).contains("\"refreshExisting\":true");

        ArgumentCaptor<WelfareServiceDetail> detailCaptor = ArgumentCaptor.forClass(WelfareServiceDetail.class);
        verify(detailRepository).save(detailCaptor.capture());
        WelfareServiceDetail saved = detailCaptor.getValue();
        assertThat(saved.getId()).isEqualTo(101L);
        assertThat(saved.getService()).isEqualTo(central);
        assertThat(saved.getTargetDetail()).isEqualTo("new-target");
        assertThat(saved.getSupportDetail()).isEqualTo("new-support");
        assertThat(saved.getContactList()).isEqualTo("[\"02-123-4567\"]");

        verify(rawApiPayloadService).saveBokjiroDetail(WelfareService.SourceType.BOKJIRO_CENTRAL, "CENTRAL-2", payload);
        verify(searchYouthRelevanceService).refreshForService(eq(central), eq(tags));
    }

    private WelfareService welfareService(Long id, WelfareService.SourceType sourceType, String sourceId) {
        return WelfareService.builder()
                .id(id)
                .sourceType(sourceType)
                .sourceId(sourceId)
                .title("service-" + sourceId)
                .status(WelfareService.ServiceStatus.ACTIVE)
                .build();
    }
}
