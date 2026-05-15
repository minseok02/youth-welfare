package com.example.welfare.collect.service;

import com.example.welfare.policy.service.PolicyEmbeddingRefreshRequestService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CollectSourceExecutionServiceTest {

    @Mock
    private ApiSyncLogService apiSyncLogService;
    @Mock
    private CollectSourceAdapter youthAdapter;
    @Mock
    private CollectSourceAdapter bokjiroCentralAdapter;
    @Mock
    private CollectSourceAdapter bokjiroLocalAdapter;
    @Mock
    private CollectSourceAdapter gov24Adapter;
    @Mock
    private CollectSourceAdapter gov24DetailAdapter;
    @Mock
    private CollectSourceAdapter gov24SupportConditionsAdapter;
    @Mock
    private CollectSourceAdapter bokjiroDetailAdapter;
    @Mock
    private CollectSourceAdapter bokjiroDetailRefreshAdapter;
    @Mock
    private BokjiroDetailCollectService bokjiroDetailCollectService;
    @Mock
    private Gov24DetailCollectService gov24DetailCollectService;
    @Mock
    private Gov24SupportConditionsCollectService gov24SupportConditionsCollectService;
    @Mock
    private PolicyEmbeddingRefreshRequestService policyEmbeddingRefreshRequestService;

    private CollectSourceExecutionService collectSourceExecutionService;

    @BeforeEach
    void setUp() {
        when(youthAdapter.source()).thenReturn(CollectSource.YOUTH);
        when(bokjiroCentralAdapter.source()).thenReturn(CollectSource.BOKJIRO_CENTRAL);
        when(bokjiroLocalAdapter.source()).thenReturn(CollectSource.BOKJIRO_LOCAL);
        when(gov24Adapter.source()).thenReturn(CollectSource.GOV24);
        when(gov24DetailAdapter.source()).thenReturn(CollectSource.GOV24_DETAIL);
        when(gov24SupportConditionsAdapter.source()).thenReturn(CollectSource.GOV24_SUPPORT_CONDITIONS);
        when(bokjiroDetailAdapter.source()).thenReturn(CollectSource.BOKJIRO_DETAIL);
        when(bokjiroDetailRefreshAdapter.source()).thenReturn(CollectSource.BOKJIRO_DETAIL_REFRESH);

        collectSourceExecutionService = new CollectSourceExecutionService(
                List.of(
                        bokjiroLocalAdapter,
                        gov24Adapter,
                        gov24DetailAdapter,
                        gov24SupportConditionsAdapter,
                        bokjiroDetailAdapter,
                        bokjiroDetailRefreshAdapter,
                        youthAdapter,
                        bokjiroCentralAdapter
                ),
                apiSyncLogService,
                bokjiroDetailCollectService,
                gov24DetailCollectService,
                gov24SupportConditionsCollectService,
                policyEmbeddingRefreshRequestService
        );
    }

    @Test
    @DisplayName("단일 source 수집은 해당 source adapter 와 api_sync_logs 경계를 사용한다")
    void collectSourceUsesRequestedAdapter() throws Exception {
        when(apiSyncLogService.runWithLog(eq("YOUTH"), any())).thenAnswer(invocation -> {
            ApiSyncLogService.CollectTask task = invocation.getArgument(1);
            return task.run();
        });
        when(policyEmbeddingRefreshRequestService.runInBatch(any(java.util.function.Supplier.class))).thenAnswer(invocation -> {
            java.util.function.Supplier<?> supplier = invocation.getArgument(0);
            return supplier.get();
        });
        when(youthAdapter.collect()).thenReturn(CollectResult.of(1, 1, 0, 0, 0));

        CollectResult result = collectSourceExecutionService.collectSource(CollectSource.YOUTH);

        assertThat(result.savedCount()).isEqualTo(1);
        verify(apiSyncLogService).runWithLog(eq("YOUTH"), any());
        verify(policyEmbeddingRefreshRequestService).runInBatch(any(java.util.function.Supplier.class));
        verify(youthAdapter).collect();
    }

    @Test
    @DisplayName("detail gap fill 수집은 전용 api_sync_logs 경계와 detail collect service를 사용한다")
    void collectGapFillUsesDetailCollectService() throws Exception {
        BokjiroDetailCollectService.GapFillResult expected =
                new BokjiroDetailCollectService.GapFillResult(2, 2, 95, 10, 7, 2, 1, false);

        when(apiSyncLogService.runWithLog(eq("BOKJIRO_DETAIL_GAP_FILL"), any())).thenAnswer(invocation -> {
            ApiSyncLogService.CollectTask task = invocation.getArgument(1);
            task.run();
            return CollectResult.of(0, 0, 0, 0, 0);
        });
        when(policyEmbeddingRefreshRequestService.runInBatch(any(java.util.function.Supplier.class))).thenAnswer(invocation -> {
            java.util.function.Supplier<?> supplier = invocation.getArgument(0);
            return supplier.get();
        });
        when(bokjiroDetailCollectService.collectBokjiroDetailGapFillResult(2, 95)).thenReturn(expected);

        BokjiroDetailCollectService.GapFillResult actual =
                collectSourceExecutionService.collectBokjiroDetailGapFill(2, 95);

        assertThat(actual).isEqualTo(expected);
        verify(apiSyncLogService).runWithLog(eq("BOKJIRO_DETAIL_GAP_FILL"), any());
        verify(policyEmbeddingRefreshRequestService).runInBatch(any(java.util.function.Supplier.class));
        verify(bokjiroDetailCollectService).collectBokjiroDetailGapFillResult(2, 95);
    }

    @Test
    @DisplayName("manual-only source 도 adapter requirement 는 명시적으로 검증한다")
    void manualOnlySourceStillRequiresAdapter() {
        assertThatThrownBy(() -> new CollectSourceExecutionService(
                List.of(
                        bokjiroLocalAdapter,
                        gov24Adapter,
                        gov24DetailAdapter,
                        gov24SupportConditionsAdapter,
                        bokjiroDetailAdapter,
                        youthAdapter,
                        bokjiroCentralAdapter
                ),
                apiSyncLogService,
                bokjiroDetailCollectService,
                gov24DetailCollectService,
                gov24SupportConditionsCollectService,
                policyEmbeddingRefreshRequestService
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BOKJIRO_DETAIL_REFRESH");
    }
}
