package com.example.welfare.collect.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CollectServiceTest {

    @Mock
    private CollectExecutionGuard collectExecutionGuard;
    @Mock
    private ApiSyncLogService apiSyncLogService;
    @Mock
    private CollectSourceAdapter youthAdapter;
    @Mock
    private CollectSourceAdapter bokjiroCentralAdapter;
    @Mock
    private CollectSourceAdapter bokjiroLocalAdapter;
    @Mock
    private CollectSourceAdapter bokjiroDetailAdapter;
    @Mock
    private CollectSourceAdapter bokjiroDetailRefreshAdapter;
    @Mock
    private BokjiroDetailCollectService bokjiroDetailCollectService;

    private CollectService collectService;

    @BeforeEach
    void setUp() {
        given(youthAdapter.source()).willReturn(CollectSource.YOUTH);
        given(bokjiroCentralAdapter.source()).willReturn(CollectSource.BOKJIRO_CENTRAL);
        given(bokjiroLocalAdapter.source()).willReturn(CollectSource.BOKJIRO_LOCAL);
        given(bokjiroDetailAdapter.source()).willReturn(CollectSource.BOKJIRO_DETAIL);
        given(bokjiroDetailRefreshAdapter.source()).willReturn(CollectSource.BOKJIRO_DETAIL_REFRESH);

        collectService = new CollectService(
                List.of(bokjiroLocalAdapter, bokjiroDetailAdapter, bokjiroDetailRefreshAdapter, youthAdapter, bokjiroCentralAdapter),
                collectExecutionGuard,
                apiSyncLogService,
                bokjiroDetailCollectService
        );
    }

    @Test
    @DisplayName("collectAll 은 고정된 source 순서대로 실행하고 중간 실패가 있어도 다음 source 를 계속 처리한다")
    void collectAllRunsSourcesInFixedOrder() throws Exception {
        List<String> executedJobs = new ArrayList<>();
        List<String> adapterCalls = new ArrayList<>();

        doAnswer(invocation -> {
            Runnable task = invocation.getArgument(1);
            task.run();
            return null;
        }).when(collectExecutionGuard).runExclusive(eq("collect-all"), any(Runnable.class));

        when(apiSyncLogService.runWithLog(any(), any())).thenAnswer(invocation -> {
            String jobName = invocation.getArgument(0);
            ApiSyncLogService.CollectTask task = invocation.getArgument(1);
            executedJobs.add(jobName);
            return task.run();
        });

        when(youthAdapter.collect()).thenAnswer(invocation -> {
            adapterCalls.add("YOUTH");
            return CollectResult.of(3, 3, 0, 0, 0);
        });
        when(bokjiroCentralAdapter.collect()).thenAnswer(invocation -> {
            adapterCalls.add("BOKJIRO_CENTRAL");
            throw new IllegalStateException("boom");
        });
        when(bokjiroLocalAdapter.collect()).thenAnswer(invocation -> {
            adapterCalls.add("BOKJIRO_LOCAL");
            return CollectResult.of(2, 2, 0, 0, 0);
        });
        when(bokjiroDetailAdapter.collect()).thenAnswer(invocation -> {
            adapterCalls.add("BOKJIRO_DETAIL");
            return CollectResult.of(1, 1, 0, 0, 0);
        });

        collectService.collectAll();

        assertThat(executedJobs).containsExactly("YOUTH", "BOKJIRO_CENTRAL", "BOKJIRO_LOCAL", "BOKJIRO_DETAIL");
        assertThat(adapterCalls).containsExactly("YOUTH", "BOKJIRO_CENTRAL", "BOKJIRO_LOCAL", "BOKJIRO_DETAIL");
    }

    @Test
    @DisplayName("단일 source 수집은 해당 source 의 lock 과 adapter 만 사용한다")
    void collectDelegatesToRequestedSource() throws Exception {
        List<String> executedJobs = new ArrayList<>();

        doAnswer(invocation -> {
            Runnable task = invocation.getArgument(1);
            task.run();
            return null;
        }).when(collectExecutionGuard).runExclusive(eq("collect-youth"), any(Runnable.class));

        when(apiSyncLogService.runWithLog(eq("YOUTH"), any())).thenAnswer(invocation -> {
            executedJobs.add(invocation.getArgument(0));
            ApiSyncLogService.CollectTask task = invocation.getArgument(1);
            return task.run();
        });
        when(youthAdapter.collect()).thenReturn(CollectResult.of(1, 1, 0, 0, 0));

        collectService.collect(CollectSource.YOUTH);

        assertThat(executedJobs).containsExactly("YOUTH");
        verify(collectExecutionGuard).runExclusive(eq("collect-youth"), any(Runnable.class));
        verify(apiSyncLogService).runWithLog(eq("YOUTH"), any());
        verify(youthAdapter).collect();
        verify(apiSyncLogService, never()).runWithLog(eq("BOKJIRO_CENTRAL"), any());
        verify(apiSyncLogService, never()).runWithLog(eq("BOKJIRO_LOCAL"), any());
        verify(apiSyncLogService, never()).runWithLog(eq("BOKJIRO_DETAIL"), any());
        verify(apiSyncLogService, never()).runWithLog(eq("BOKJIRO_DETAIL_REFRESH"), any());
    }

    @Test
    @DisplayName("detail refresh 수집은 refresh source lock 과 adapter 만 사용한다")
    void collectDelegatesToRefreshSource() throws Exception {
        List<String> executedJobs = new ArrayList<>();

        doAnswer(invocation -> {
            Runnable task = invocation.getArgument(1);
            task.run();
            return null;
        }).when(collectExecutionGuard).runExclusive(eq("collect-bokjiro-details-refresh"), any(Runnable.class));

        when(apiSyncLogService.runWithLog(eq("BOKJIRO_DETAIL_REFRESH"), any())).thenAnswer(invocation -> {
            executedJobs.add(invocation.getArgument(0));
            ApiSyncLogService.CollectTask task = invocation.getArgument(1);
            return task.run();
        });
        when(bokjiroDetailRefreshAdapter.collect()).thenReturn(CollectResult.of(1, 1, 0, 0, 0));

        collectService.collect(CollectSource.BOKJIRO_DETAIL_REFRESH);

        assertThat(executedJobs).containsExactly("BOKJIRO_DETAIL_REFRESH");
        verify(collectExecutionGuard).runExclusive(eq("collect-bokjiro-details-refresh"), any(Runnable.class));
        verify(apiSyncLogService).runWithLog(eq("BOKJIRO_DETAIL_REFRESH"), any());
        verify(bokjiroDetailRefreshAdapter).collect();
    }

    @Test
    @DisplayName("detail gap fill 수집은 전용 lock 과 api_sync_logs 경계를 사용한다")
    void collectDetailGapFillUsesExclusiveLockAndLog() throws Exception {
        BokjiroDetailCollectService.GapFillResult expected =
                new BokjiroDetailCollectService.GapFillResult(2, 2, 95, 10, 7, 2, 1, false);

        doAnswer(invocation -> {
            Runnable task = invocation.getArgument(1);
            task.run();
            return null;
        }).when(collectExecutionGuard).runExclusive(eq("collect-bokjiro-details-gap-fill"), any(Runnable.class));

        when(apiSyncLogService.runWithLog(eq("BOKJIRO_DETAIL_GAP_FILL"), any())).thenAnswer(invocation -> {
            ApiSyncLogService.CollectTask task = invocation.getArgument(1);
            return task.run();
        });
        when(bokjiroDetailCollectService.collectBokjiroDetailGapFillResult(2, 95)).thenReturn(expected);

        BokjiroDetailCollectService.GapFillResult actual = collectService.collectBokjiroDetailGapFill(2, 95);

        assertThat(actual).isEqualTo(expected);
        verify(collectExecutionGuard).runExclusive(eq("collect-bokjiro-details-gap-fill"), any(Runnable.class));
        verify(apiSyncLogService).runWithLog(eq("BOKJIRO_DETAIL_GAP_FILL"), any());
        verify(bokjiroDetailCollectService).collectBokjiroDetailGapFillResult(2, 95);
    }

    @Test
    @DisplayName("executionOrder 는 scheduled source 만 포함하고 refresh source 는 수동 실행 대상으로 남긴다")
    void executionOrderIncludesOnlyScheduledSources() {
        assertThat(CollectSource.executionOrder()).containsExactly(
                CollectSource.YOUTH,
                CollectSource.BOKJIRO_CENTRAL,
                CollectSource.BOKJIRO_LOCAL,
                CollectSource.BOKJIRO_DETAIL
        );
        assertThat(CollectSource.BOKJIRO_DETAIL_REFRESH.runsInScheduledBatch()).isFalse();
        assertThat(CollectSource.BOKJIRO_DETAIL_REFRESH.requiresAdapter()).isTrue();
    }

    @Test
    @DisplayName("manual-only source 도 adapter requirement 는 명시적으로 검증한다")
    void manualOnlySourceStillRequiresAdapter() {
        assertThatThrownBy(() -> new CollectService(
                List.of(bokjiroLocalAdapter, bokjiroDetailAdapter, youthAdapter, bokjiroCentralAdapter),
                collectExecutionGuard,
                apiSyncLogService,
                bokjiroDetailCollectService
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BOKJIRO_DETAIL_REFRESH");
    }
}
