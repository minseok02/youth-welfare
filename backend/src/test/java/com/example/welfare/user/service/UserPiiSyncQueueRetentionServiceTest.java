package com.example.welfare.user.service;

import com.example.welfare.global.service.AppSchedulerGate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserPiiSyncQueueRetentionServiceTest {

    @Mock
    private UserPiiSyncQueueService userPiiSyncQueueService;

    @Mock
    private AppSchedulerGate appSchedulerGate;

    @BeforeEach
    void setUpSchedulerGate() {
        lenient().when(appSchedulerGate.shouldRun("UserPiiSyncQueueRetentionService.cleanupSyncedRows"))
                .thenReturn(true);
    }

    @Test
    @DisplayName("retention은 설정 일수보다 오래된 SYNCED queue row를 삭제한다")
    void cleanupSyncedRowsDeletesRowsOlderThanRetention() {
        UserPiiSyncQueueRetentionService service = retentionService();
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "syncedRetentionDays", 30);

        LocalDateTime before = LocalDateTime.of(2026, 5, 26, 0, 0);
        given(userPiiSyncQueueService.deleteSyncedBefore(before)).willReturn(3L);

        service.cleanupSyncedRows();

        then(userPiiSyncQueueService).should().deleteSyncedBefore(before);
    }

    @Test
    @DisplayName("retention이 꺼져 있으면 삭제를 시도하지 않는다")
    void cleanupSyncedRowsSkipsWhenDisabled() {
        UserPiiSyncQueueRetentionService service = retentionService();
        ReflectionTestUtils.setField(service, "enabled", false);

        service.cleanupSyncedRows();

        then(userPiiSyncQueueService).should(never()).deleteSyncedBefore(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("scheduler가 비활성화된 노드에서는 PII queue retention을 실행하지 않는다")
    void cleanupSyncedRowsSkipsWhenSchedulerDisabled() {
        UserPiiSyncQueueRetentionService service = retentionService();
        ReflectionTestUtils.setField(service, "enabled", true);
        when(appSchedulerGate.shouldRun("UserPiiSyncQueueRetentionService.cleanupSyncedRows"))
                .thenReturn(false);

        service.cleanupSyncedRows();

        then(userPiiSyncQueueService).should(never()).deleteSyncedBefore(org.mockito.ArgumentMatchers.any());
    }


    @Test
    @DisplayName("retention 일수는 최소 1일로 보정한다")
    void cleanupSyncedRowsClampsRetentionDays() {
        UserPiiSyncQueueRetentionService service = retentionService();
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "syncedRetentionDays", 0);

        service.cleanupSyncedRows();

        then(userPiiSyncQueueService).should()
                .deleteSyncedBefore(LocalDateTime.of(2026, 6, 24, 0, 0));
    }

    @Test
    @DisplayName("retention 삭제 실패는 스케줄러 밖으로 전파하지 않는다")
    void cleanupSyncedRowsSuppressesRuntimeFailure() {
        UserPiiSyncQueueRetentionService service = retentionService();
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "syncedRetentionDays", 30);
        given(userPiiSyncQueueService.deleteSyncedBefore(LocalDateTime.of(2026, 5, 26, 0, 0)))
                .willThrow(new RuntimeException("db timeout"));

        service.cleanupSyncedRows();

        then(userPiiSyncQueueService).should()
                .deleteSyncedBefore(LocalDateTime.of(2026, 5, 26, 0, 0));
    }

    private UserPiiSyncQueueRetentionService retentionService() {
        Clock clock = Clock.fixed(Instant.parse("2026-06-25T00:00:00Z"), ZoneOffset.UTC);
        return new UserPiiSyncQueueRetentionService(userPiiSyncQueueService, appSchedulerGate, clock);
    }
}
