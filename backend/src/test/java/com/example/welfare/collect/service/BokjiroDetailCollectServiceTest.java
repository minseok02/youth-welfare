package com.example.welfare.collect.service;

import com.example.welfare.collect.gateway.BokjiroDetailClient;
import com.example.welfare.collect.mapper.WelfareServiceMapper;
import com.example.welfare.collect.normalization.NormalizedPolicyAggregate;
import com.example.welfare.collect.normalization.NormalizedPolicySidecarWriter;
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
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

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
    @Mock
    private NormalizedPolicySidecarWriter normalizedPolicySidecarWriter;

    private final WelfareServiceMapper welfareServiceMapper = new WelfareServiceMapper();
    private BokjiroDetailCollectService service;

    @BeforeEach
    void setUp() {
        service = new BokjiroDetailCollectService(
                welfareServiceRepository,
                detailRepository,
                serviceTagRepository,
                detailClient,
                rawApiPayloadService,
                searchYouthRelevanceService,
                welfareServiceMapper,
                normalizedPolicySidecarWriter
        );
        ReflectionTestUtils.setField(service, "maxCallsPerApiPerRun", 95);
        ReflectionTestUtils.setField(service, "requestIntervalMs", 0L);
        ReflectionTestUtils.setField(service, "retryMaxAttempts", 1);
        ReflectionTestUtils.setField(service, "retryBaseBackoffMs", 0L);
        ReflectionTestUtils.setField(service, "maxConsecutiveRateLimitHits", 5);
        lenient().when(welfareServiceRepository.findBySourceType(WelfareService.SourceType.BOKJIRO_CENTRAL))
                .thenReturn(List.of());
        lenient().when(welfareServiceRepository.findBySourceType(WelfareService.SourceType.BOKJIRO_LOCAL))
                .thenReturn(List.of());
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
                .targetDetail("만 18세 이상 39세 이하 미취업 청년")
                .supportDetail("new-support")
                .applyMethodDetail("온라인 신청, 2026.12.31 까지 접수")
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
        assertThat(saved.getTargetDetail()).isEqualTo("만 18세 이상 39세 이하 미취업 청년");
        assertThat(saved.getSupportDetail()).isEqualTo("new-support");
        assertThat(saved.getContactList()).isEqualTo("[\"02-123-4567\"]");
        assertThat(central.getMinAge()).isEqualTo(18);
        assertThat(central.getMaxAge()).isEqualTo(39);
        assertThat(central.getApplyEndDate()).isEqualTo(java.time.LocalDate.of(2026, 12, 31));
        assertThat(central.getIsOnlineApply()).isTrue();

        verify(rawApiPayloadService).saveBokjiroDetail(WelfareService.SourceType.BOKJIRO_CENTRAL, "CENTRAL-2", payload);
        verify(normalizedPolicySidecarWriter).upsert(eq(central), argThat(aggregate ->
                aggregate != null
                        && aggregate.core() != null
                        && "CENTRAL-2".equals(aggregate.core().sourceId())
                        && aggregate.facts().stream().anyMatch(fact -> "BK_AGE_ELIGIBILITY".equals(fact.factMergeKey()))
                        && aggregate.facts().stream().anyMatch(fact -> "BK_APPLY_END_DATE".equals(fact.factMergeKey()))
        ));
        verify(searchYouthRelevanceService).refreshForService(eq(central), eq(tags));
    }

    @Test
    @DisplayName("low maxCalls 에서 central target 이 없으면 local source 가 전체 budget 을 가져간다")
    void collectBokjiroDetailsUsesLocalBudgetWhenCentralTargetsAreMissing() {
        WelfareService local = welfareService(12L, WelfareService.SourceType.BOKJIRO_LOCAL, "LOCAL-1");
        BokjiroDetailClient.DetailPayload payload = BokjiroDetailClient.DetailPayload.builder()
                .supportDetail("local support")
                .applyMethodDetail("온라인 신청")
                .build();

        given(welfareServiceRepository.findBySourceType(WelfareService.SourceType.BOKJIRO_CENTRAL))
                .willReturn(List.of());
        given(welfareServiceRepository.findBySourceType(WelfareService.SourceType.BOKJIRO_LOCAL))
                .willReturn(List.of(local));
        given(detailRepository.existsByServiceId(12L)).willReturn(false);
        given(detailClient.fetchLocalWithStatus("LOCAL-1"))
                .willReturn(BokjiroDetailClient.FetchResult.success(payload));
        given(serviceTagRepository.findByServiceId(12L)).willReturn(List.of());

        CollectResult result = service.collectBokjiroDetailsResult(1);

        assertThat(result.requestedCount()).isEqualTo(1);
        assertThat(result.savedCount()).isEqualTo(1);
        assertThat(result.metadataJson()).contains("\"centralBudget\":0");
        assertThat(result.metadataJson()).contains("\"localBudget\":1");
        verify(detailClient, never()).fetchCentralWithStatus(any());
        verify(detailClient).fetchLocalWithStatus("LOCAL-1");
    }

    @Test
    @DisplayName("low maxCalls 에서 local backlog 비중이 더 크면 local source 도 budget 을 받는다")
    void collectBokjiroDetailsAllocatesBudgetToLocalWhenLocalBacklogIsLarger() {
        WelfareService central = welfareService(13L, WelfareService.SourceType.BOKJIRO_CENTRAL, "CENTRAL-3");
        WelfareService local1 = welfareService(14L, WelfareService.SourceType.BOKJIRO_LOCAL, "LOCAL-2");
        WelfareService local2 = welfareService(15L, WelfareService.SourceType.BOKJIRO_LOCAL, "LOCAL-3");
        WelfareService local3 = welfareService(16L, WelfareService.SourceType.BOKJIRO_LOCAL, "LOCAL-4");
        BokjiroDetailClient.DetailPayload payload = BokjiroDetailClient.DetailPayload.builder()
                .supportDetail("local support")
                .applyMethodDetail("온라인 신청")
                .build();

        given(welfareServiceRepository.findBySourceType(WelfareService.SourceType.BOKJIRO_CENTRAL))
                .willReturn(List.of(central));
        given(welfareServiceRepository.findBySourceType(WelfareService.SourceType.BOKJIRO_LOCAL))
                .willReturn(List.of(local1, local2, local3));
        given(detailRepository.existsByServiceId(14L)).willReturn(false);
        given(detailClient.fetchLocalWithStatus("LOCAL-2"))
                .willReturn(BokjiroDetailClient.FetchResult.success(payload));
        given(serviceTagRepository.findByServiceId(14L)).willReturn(List.of());

        CollectResult result = service.collectBokjiroDetailsResult(1);

        assertThat(result.requestedCount()).isEqualTo(1);
        assertThat(result.savedCount()).isEqualTo(1);
        assertThat(result.metadataJson()).contains("\"centralBudget\":0");
        assertThat(result.metadataJson()).contains("\"localBudget\":1");
        verify(detailClient, never()).fetchCentralWithStatus(any());
        verify(detailClient).fetchLocalWithStatus("LOCAL-2");
    }

    @Test
    @DisplayName("detail gap fill 은 여러 라운드를 돌며 missing detail backlog 를 순차적으로 메운다")
    void collectBokjiroDetailGapFillAdvancesAcrossRounds() {
        WelfareService first = welfareService(17L, WelfareService.SourceType.BOKJIRO_LOCAL, "LOCAL-17");
        WelfareService second = welfareService(18L, WelfareService.SourceType.BOKJIRO_LOCAL, "LOCAL-18");
        BokjiroDetailClient.DetailPayload payload = BokjiroDetailClient.DetailPayload.builder()
                .supportDetail("지원 내용")
                .applyMethodDetail("온라인 신청")
                .build();

        given(welfareServiceRepository.findBySourceType(WelfareService.SourceType.BOKJIRO_CENTRAL))
                .willReturn(List.of(), List.of(), List.of());
        given(welfareServiceRepository.findBySourceType(WelfareService.SourceType.BOKJIRO_LOCAL))
                .willReturn(List.of(first, second), List.of(first, second), List.of(first, second));
        given(detailRepository.existsByServiceId(17L)).willReturn(false, true, true);
        given(detailRepository.existsByServiceId(18L)).willReturn(false, true);
        given(detailClient.fetchLocalWithStatus("LOCAL-17"))
                .willReturn(BokjiroDetailClient.FetchResult.success(payload));
        given(detailClient.fetchLocalWithStatus("LOCAL-18"))
                .willReturn(BokjiroDetailClient.FetchResult.success(payload));
        given(serviceTagRepository.findByServiceId(17L)).willReturn(List.of());
        given(serviceTagRepository.findByServiceId(18L)).willReturn(List.of());

        BokjiroDetailCollectService.GapFillResult result = service.collectBokjiroDetailGapFillResult(3, 1);

        assertThat(result.roundsRequested()).isEqualTo(3);
        assertThat(result.roundsExecuted()).isEqualTo(3);
        assertThat(result.requestedCount()).isEqualTo(2);
        assertThat(result.savedCount()).isEqualTo(2);
        assertThat(result.skippedCount()).isEqualTo(3);
        assertThat(result.failedCount()).isZero();
        assertThat(result.stoppedAfterNoSaves()).isTrue();
        verify(detailClient).fetchLocalWithStatus("LOCAL-17");
        verify(detailClient).fetchLocalWithStatus("LOCAL-18");
    }

    @Test
    @DisplayName("상세 수집은 연속 429 가 임계치를 넘으면 현재 source 처리를 중단한다")
    void collectBokjiroDetailsStopsAfterConsecutiveRateLimits() {
        WelfareService first = welfareService(21L, WelfareService.SourceType.BOKJIRO_CENTRAL, "CENTRAL-21");
        WelfareService second = welfareService(22L, WelfareService.SourceType.BOKJIRO_CENTRAL, "CENTRAL-22");
        WelfareService third = welfareService(23L, WelfareService.SourceType.BOKJIRO_CENTRAL, "CENTRAL-23");

        ReflectionTestUtils.setField(service, "maxConsecutiveRateLimitHits", 2);
        given(welfareServiceRepository.findBySourceType(WelfareService.SourceType.BOKJIRO_CENTRAL))
                .willReturn(List.of(first, second, third));
        given(detailRepository.existsByServiceId(any())).willReturn(false);
        given(detailClient.fetchCentralWithStatus("CENTRAL-21"))
                .willReturn(BokjiroDetailClient.FetchResult.failure(false, true, 429));
        given(detailClient.fetchCentralWithStatus("CENTRAL-22"))
                .willReturn(BokjiroDetailClient.FetchResult.failure(false, true, 429));

        CollectResult result = service.collectBokjiroDetailsResult(3);

        assertThat(result.requestedCount()).isEqualTo(2);
        assertThat(result.savedCount()).isZero();
        assertThat(result.skippedCount()).isZero();
        assertThat(result.failedCount()).isZero();
        verify(detailClient, never()).fetchCentralWithStatus("CENTRAL-23");
        verify(detailRepository, never()).save(any());
    }

    @Test
    @DisplayName("상세 수집은 빈 payload 를 실패로 세지 않고 저장 없이 다음 정책으로 진행한다")
    void collectBokjiroDetailsSkipsEmptyPayloadWithoutFailure() {
        WelfareService emptyPayloadService = welfareService(31L, WelfareService.SourceType.BOKJIRO_CENTRAL, "CENTRAL-31");
        WelfareService savedService = welfareService(32L, WelfareService.SourceType.BOKJIRO_CENTRAL, "CENTRAL-32");
        BokjiroDetailClient.DetailPayload emptyPayload = BokjiroDetailClient.DetailPayload.builder().build();
        BokjiroDetailClient.DetailPayload validPayload = BokjiroDetailClient.DetailPayload.builder()
                .supportDetail("지원 내용")
                .applyMethodDetail("온라인 신청")
                .build();

        given(welfareServiceRepository.findBySourceType(WelfareService.SourceType.BOKJIRO_CENTRAL))
                .willReturn(List.of(emptyPayloadService, savedService));
        given(detailRepository.existsByServiceId(any())).willReturn(false);
        given(detailClient.fetchCentralWithStatus("CENTRAL-31"))
                .willReturn(BokjiroDetailClient.FetchResult.success(emptyPayload));
        given(detailClient.fetchCentralWithStatus("CENTRAL-32"))
                .willReturn(BokjiroDetailClient.FetchResult.success(validPayload));
        given(serviceTagRepository.findByServiceId(32L)).willReturn(List.of());

        CollectResult result = service.collectBokjiroDetailsResult(2);

        assertThat(result.requestedCount()).isEqualTo(2);
        assertThat(result.savedCount()).isEqualTo(1);
        assertThat(result.failedCount()).isZero();
        verify(rawApiPayloadService, never())
                .saveBokjiroDetail(WelfareService.SourceType.BOKJIRO_CENTRAL, "CENTRAL-31", emptyPayload);
        verify(rawApiPayloadService)
                .saveBokjiroDetail(WelfareService.SourceType.BOKJIRO_CENTRAL, "CENTRAL-32", validPayload);
        verify(detailRepository, times(1)).save(any(WelfareServiceDetail.class));
    }

    @Test
    @DisplayName("상세 저장이 일부 실패해도 다음 정책은 계속 처리해 partial success 집계를 남긴다")
    void collectBokjiroDetailsContinuesAfterSaveFailure() {
        WelfareService failingService = welfareService(41L, WelfareService.SourceType.BOKJIRO_CENTRAL, "CENTRAL-41");
        WelfareService succeedingService = welfareService(42L, WelfareService.SourceType.BOKJIRO_CENTRAL, "CENTRAL-42");
        BokjiroDetailClient.DetailPayload failingPayload = BokjiroDetailClient.DetailPayload.builder()
                .supportDetail("실패 payload")
                .build();
        BokjiroDetailClient.DetailPayload succeedingPayload = BokjiroDetailClient.DetailPayload.builder()
                .supportDetail("성공 payload")
                .build();

        given(welfareServiceRepository.findBySourceType(WelfareService.SourceType.BOKJIRO_CENTRAL))
                .willReturn(List.of(failingService, succeedingService));
        given(detailRepository.existsByServiceId(any())).willReturn(false);
        given(detailClient.fetchCentralWithStatus("CENTRAL-41"))
                .willReturn(BokjiroDetailClient.FetchResult.success(failingPayload));
        given(detailClient.fetchCentralWithStatus("CENTRAL-42"))
                .willReturn(BokjiroDetailClient.FetchResult.success(succeedingPayload));
        willThrow(new IllegalStateException("save failed"))
                .given(detailRepository)
                .save(argThat(detail -> detail.getService().equals(failingService)));
        given(serviceTagRepository.findByServiceId(42L)).willReturn(List.of());

        CollectResult result = service.collectBokjiroDetailsResult(2);

        assertThat(result.requestedCount()).isEqualTo(2);
        assertThat(result.savedCount()).isEqualTo(1);
        assertThat(result.failedCount()).isEqualTo(1);
        verify(rawApiPayloadService)
                .saveBokjiroDetail(WelfareService.SourceType.BOKJIRO_CENTRAL, "CENTRAL-41", failingPayload);
        verify(rawApiPayloadService)
                .saveBokjiroDetail(WelfareService.SourceType.BOKJIRO_CENTRAL, "CENTRAL-42", succeedingPayload);
        verify(searchYouthRelevanceService, never()).refreshForService(eq(failingService), any());
        verify(searchYouthRelevanceService).refreshForService(eq(succeedingService), eq(List.of()));
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
