package com.example.welfare.collect.service;

import com.example.welfare.collect.gateway.BokjiroCentralClient;
import com.example.welfare.collect.gateway.BokjiroLocalClient;
import com.example.welfare.collect.gateway.YouthApiClient;
import com.example.welfare.collect.mapper.WelfareServiceMapper;
import com.example.welfare.collect.validation.BokjiroYouthFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CollectServiceTest {

    @Mock
    private YouthApiClient youthApiClient;
    @Mock
    private BokjiroCentralClient bokjiroCentralClient;
    @Mock
    private BokjiroLocalClient bokjiroLocalClient;
    @Mock
    private WelfareServiceMapper mapper;
    @Mock
    private CollectItemSaver saver;
    @Mock
    private BokjiroDetailCollectService bokjiroDetailCollectService;
    @Mock
    private BokjiroYouthFilter bokjiroYouthFilter;
    @Mock
    private RawApiPayloadService rawApiPayloadService;
    @Mock
    private CollectExecutionGuard collectExecutionGuard;
    @Mock
    private ApiSyncLogService apiSyncLogService;

    @InjectMocks
    private CollectService collectService;

    @Test
    @DisplayName("전체 수집은 한 source가 실패해도 다음 source를 계속 실행한다")
    void collectAllContinuesAfterSingleSourceFailure() throws Exception {
        doAnswer(invocation -> {
            Runnable task = invocation.getArgument(1);
            task.run();
            return null;
        }).when(collectExecutionGuard).runExclusive(anyString(), any(Runnable.class));

        given(bokjiroCentralClient.fetchAll()).willReturn(List.of());
        given(bokjiroLocalClient.fetchAll()).willReturn(List.of());
        given(bokjiroDetailCollectService.collectBokjiroDetailsResult())
                .willReturn(CollectResult.withMetadata(0, 0, 0, 0, 0, "{\"maxCalls\":0}"));

        doAnswer(invocation -> {
            String jobName = invocation.getArgument(0);
            ApiSyncLogService.CollectTask task = invocation.getArgument(1);
            if ("YOUTH".equals(jobName)) {
                throw new IllegalStateException("temporary youth failure");
            }
            return task.run();
        }).when(apiSyncLogService).runWithLog(anyString(), any(ApiSyncLogService.CollectTask.class));

        assertThatCode(() -> collectService.collectAll()).doesNotThrowAnyException();

        InOrder inOrder = inOrder(apiSyncLogService);
        inOrder.verify(apiSyncLogService).runWithLog(org.mockito.ArgumentMatchers.eq("YOUTH"), any(ApiSyncLogService.CollectTask.class));
        inOrder.verify(apiSyncLogService).runWithLog(org.mockito.ArgumentMatchers.eq("BOKJIRO_CENTRAL"), any(ApiSyncLogService.CollectTask.class));
        inOrder.verify(apiSyncLogService).runWithLog(org.mockito.ArgumentMatchers.eq("BOKJIRO_LOCAL"), any(ApiSyncLogService.CollectTask.class));
        inOrder.verify(apiSyncLogService).runWithLog(org.mockito.ArgumentMatchers.eq("BOKJIRO_DETAIL"), any(ApiSyncLogService.CollectTask.class));
        verify(bokjiroCentralClient).fetchAll();
        verify(bokjiroLocalClient).fetchAll();
        verify(bokjiroDetailCollectService).collectBokjiroDetailsResult();
    }
}
