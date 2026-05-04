package com.example.welfare.collect.repository;

import com.example.welfare.collect.entity.ApiSyncLog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class ApiSyncLogCommandRepositoryImplTest {

    @Mock
    private ApiSyncLogRepository apiSyncLogRepository;

    @InjectMocks
    private ApiSyncLogCommandRepositoryImpl apiSyncLogCommandRepository;

    @Test
    @DisplayName("api sync log command repository는 save를 위임한다")
    void saveDelegates() {
        ApiSyncLog syncLog = ApiSyncLog.start("YOUTH");
        given(apiSyncLogRepository.save(syncLog)).willReturn(syncLog);

        assertThat(apiSyncLogCommandRepository.save(syncLog)).isSameAs(syncLog);
    }

    @Test
    @DisplayName("api sync log command repository는 stale running log failure 처리도 위임한다")
    void failStaleRunningLogsDelegates() {
        LocalDateTime finishedAt = LocalDateTime.of(2026, 5, 4, 16, 0);
        given(apiSyncLogRepository.failStaleRunningLogs(
                "YOUTH",
                finishedAt,
                "InterruptedRun",
                "auto-closed"
        )).willReturn(2);

        assertThat(apiSyncLogCommandRepository.failStaleRunningLogs(
                "YOUTH",
                finishedAt,
                "InterruptedRun",
                "auto-closed"
        )).isEqualTo(2);
        then(apiSyncLogRepository).should().failStaleRunningLogs(
                "YOUTH",
                finishedAt,
                "InterruptedRun",
                "auto-closed"
        );
    }
}
