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

    private CollectService collectService;

    @BeforeEach
    void setUp() {
        given(youthAdapter.source()).willReturn(CollectSource.YOUTH);
        given(bokjiroCentralAdapter.source()).willReturn(CollectSource.BOKJIRO_CENTRAL);
        given(bokjiroLocalAdapter.source()).willReturn(CollectSource.BOKJIRO_LOCAL);
        given(bokjiroDetailAdapter.source()).willReturn(CollectSource.BOKJIRO_DETAIL);

        collectService = new CollectService(
                List.of(bokjiroLocalAdapter, bokjiroDetailAdapter, youthAdapter, bokjiroCentralAdapter),
                collectExecutionGuard,
                apiSyncLogService
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
    void collectYouthDelegatesToRequestedSource() throws Exception {
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

        collectService.collectYouth();

        assertThat(executedJobs).containsExactly("YOUTH");
        verify(collectExecutionGuard).runExclusive(eq("collect-youth"), any(Runnable.class));
        verify(apiSyncLogService).runWithLog(eq("YOUTH"), any());
        verify(youthAdapter).collect();
        verify(apiSyncLogService, never()).runWithLog(eq("BOKJIRO_CENTRAL"), any());
        verify(apiSyncLogService, never()).runWithLog(eq("BOKJIRO_LOCAL"), any());
        verify(apiSyncLogService, never()).runWithLog(eq("BOKJIRO_DETAIL"), any());
    }
}
