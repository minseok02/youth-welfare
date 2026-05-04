package com.example.welfare.collect.service;

import com.example.welfare.collect.entity.ApiSyncLog;
import com.example.welfare.collect.repository.ApiSyncLogCommandRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ApiSyncLogServiceTest {

    @Mock
    private ApiSyncLogCommandRepository apiSyncLogCommandRepository;

    @InjectMocks
    private ApiSyncLogService apiSyncLogService;

    @Test
    @DisplayName("수집 작업이 성공하면 SUCCESS 로그를 저장한다")
    void runWithLogStoresSuccess() {
        given(apiSyncLogCommandRepository.failStaleRunningLogs(anyString(), any(), anyString(), anyString()))
                .willReturn(0);
        given(apiSyncLogCommandRepository.save(any(ApiSyncLog.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        CollectResult result = apiSyncLogService.runWithLog(
                "YOUTH",
                () -> CollectResult.of(10, 8, 2, 0, 0)
        );

        assertThat(result.savedCount()).isEqualTo(8);

        ArgumentCaptor<ApiSyncLog> captor = ArgumentCaptor.forClass(ApiSyncLog.class);
        verify(apiSyncLogCommandRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        List<ApiSyncLog> savedLogs = captor.getAllValues();
        assertThat(savedLogs.get(1).getStatus()).isEqualTo(ApiSyncLog.SyncStatus.SUCCESS);
        assertThat(savedLogs.get(1).getRequestedCount()).isEqualTo(10);
        assertThat(savedLogs.get(1).getSavedCount()).isEqualTo(8);
        assertThat(savedLogs.get(1).getSkippedCount()).isEqualTo(2);
        assertThat(savedLogs.get(1).getFinishedAt()).isNotNull();
    }

    @Test
    @DisplayName("수집 작업에 저장 실패가 있으면 PARTIAL_SUCCESS 로그를 저장한다")
    void runWithLogStoresPartialSuccess() {
        given(apiSyncLogCommandRepository.failStaleRunningLogs(anyString(), any(), anyString(), anyString()))
                .willReturn(0);
        given(apiSyncLogCommandRepository.save(any(ApiSyncLog.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        apiSyncLogService.runWithLog(
                "BOKJIRO_LOCAL",
                () -> CollectResult.of(10, 7, 1, 1, 1)
        );

        ArgumentCaptor<ApiSyncLog> captor = ArgumentCaptor.forClass(ApiSyncLog.class);
        verify(apiSyncLogCommandRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        ApiSyncLog completed = captor.getAllValues().get(1);
        assertThat(completed.getStatus()).isEqualTo(ApiSyncLog.SyncStatus.PARTIAL_SUCCESS);
        assertThat(completed.getFailedCount()).isEqualTo(1);
        assertThat(completed.getFilteredCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("수집 작업이 예외로 중단되면 FAILED 로그를 저장하고 예외를 다시 던진다")
    void runWithLogStoresFailureAndRethrows() {
        given(apiSyncLogCommandRepository.failStaleRunningLogs(anyString(), any(), anyString(), anyString()))
                .willReturn(0);
        given(apiSyncLogCommandRepository.save(any(ApiSyncLog.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        assertThatThrownBy(() -> apiSyncLogService.runWithLog("YOUTH", () -> {
            throw new IllegalStateException("external api failed");
        })).isInstanceOf(IllegalStateException.class);

        ArgumentCaptor<ApiSyncLog> captor = ArgumentCaptor.forClass(ApiSyncLog.class);
        verify(apiSyncLogCommandRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        ApiSyncLog failed = captor.getAllValues().get(1);
        assertThat(failed.getStatus()).isEqualTo(ApiSyncLog.SyncStatus.FAILED);
        assertThat(failed.getErrorCode()).isEqualTo("IllegalStateException");
        assertThat(failed.getErrorMessage()).isEqualTo("external api failed");
        assertThat(failed.getFinishedAt()).isNotNull();
    }

    @Test
    @DisplayName("같은 job의 stale RUNNING 로그가 있으면 새 수집 시작 전에 FAILED로 정리한다")
    void runWithLogClosesStaleRunningLogsBeforeStartingNewLog() {
        given(apiSyncLogCommandRepository.failStaleRunningLogs(anyString(), any(), anyString(), anyString()))
                .willReturn(2);
        given(apiSyncLogCommandRepository.save(any(ApiSyncLog.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        apiSyncLogService.runWithLog(
                "YOUTH",
                () -> CollectResult.of(5, 5, 0, 0, 0)
        );

        verify(apiSyncLogCommandRepository).failStaleRunningLogs(
                eq("YOUTH"),
                any(),
                eq("InterruptedRun"),
                contains("auto-closed")
        );
        verify(apiSyncLogCommandRepository, org.mockito.Mockito.times(2)).save(any(ApiSyncLog.class));
    }
}
