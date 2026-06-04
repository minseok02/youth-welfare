package com.example.welfare.collect.service;

import com.example.welfare.collect.repository.StatusUpdateReadRepository;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.recommend.repository.ClusterAiResultCommandRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class StatusUpdateServiceTest {

    @Mock
    private StatusUpdateReadRepository statusUpdateReadRepository;

    @Mock
    private ClusterAiResultCommandRepository clusterAiResultCommandRepository;

    @InjectMocks
    private StatusUpdateService statusUpdateService;

    @Test
    @DisplayName("status update는 상태 갱신 후 만료된 cluster ai cache 삭제를 command repository로 위임한다")
    void updateStatusesDeletesExpiredClusterAiCache() {
        given(statusUpdateReadRepository.findActiveServices()).willReturn(List.of());
        given(statusUpdateReadRepository.findUpcomingServices()).willReturn(List.of());
        given(statusUpdateReadRepository.findClosedServices()).willReturn(List.of());

        statusUpdateService.updateStatuses();

        then(clusterAiResultCommandRepository).should().deleteExpiredBefore(any(LocalDateTime.class));
    }

    @Test
    @DisplayName("status sync는 만료된 ACTIVE를 CLOSED로 바꾸고 시작된 UPCOMING을 ACTIVE로 바꾼다")
    void runStatusSyncUpdatesStatuses() {
        WelfareService expiredActive = WelfareService.builder()
                .sourceType(WelfareService.SourceType.YOUTH)
                .sourceId("expired")
                .title("expired")
                .status(WelfareService.ServiceStatus.ACTIVE)
                .applyEndDate(LocalDate.now().minusDays(1))
                .build();
        WelfareService upcomingNowActive = WelfareService.builder()
                .sourceType(WelfareService.SourceType.YOUTH)
                .sourceId("upcoming")
                .title("upcoming")
                .status(WelfareService.ServiceStatus.UPCOMING)
                .applyStartDate(LocalDate.now())
                .build();
        WelfareService reopenUpcoming = WelfareService.builder()
                .sourceType(WelfareService.SourceType.YOUTH)
                .sourceId("reopen-upcoming")
                .title("reopen-upcoming")
                .status(WelfareService.ServiceStatus.CLOSED)
                .applyEndDate(LocalDate.now().plusDays(5))
                .startDate(LocalDate.now().plusDays(3))
                .build();
        WelfareService reopenActive = WelfareService.builder()
                .sourceType(WelfareService.SourceType.GOV24)
                .sourceId("reopen-active")
                .title("reopen-active")
                .status(WelfareService.ServiceStatus.CLOSED)
                .applyEndDate(LocalDate.now().plusDays(7))
                .build();
        WelfareService keepClosed = WelfareService.builder()
                .sourceType(WelfareService.SourceType.YOUTH)
                .sourceId("keep-closed")
                .title("keep-closed")
                .status(WelfareService.ServiceStatus.CLOSED)
                .applyEndDate(LocalDate.now().plusDays(30))
                .endDate(LocalDate.now().minusDays(1))
                .build();
        given(statusUpdateReadRepository.findActiveServices()).willReturn(List.of(expiredActive));
        given(statusUpdateReadRepository.findUpcomingServices()).willReturn(List.of(upcomingNowActive));
        given(statusUpdateReadRepository.findClosedServices()).willReturn(List.of(reopenUpcoming, reopenActive, keepClosed));

        StatusUpdateService.StatusSyncResult result = statusUpdateService.runStatusSync();

        assertThat(expiredActive.getStatus()).isEqualTo(WelfareService.ServiceStatus.CLOSED);
        assertThat(upcomingNowActive.getStatus()).isEqualTo(WelfareService.ServiceStatus.ACTIVE);
        assertThat(reopenUpcoming.getStatus()).isEqualTo(WelfareService.ServiceStatus.UPCOMING);
        assertThat(reopenActive.getStatus()).isEqualTo(WelfareService.ServiceStatus.ACTIVE);
        assertThat(keepClosed.getStatus()).isEqualTo(WelfareService.ServiceStatus.CLOSED);
        assertThat(result.closedCount()).isEqualTo(1);
        assertThat(result.activatedCount()).isEqualTo(1);
        assertThat(result.reopenedCount()).isEqualTo(2);
        assertThat(result.clusterAiCacheCleanupExecuted()).isTrue();
        then(clusterAiResultCommandRepository).should().deleteExpiredBefore(any(LocalDateTime.class));
    }
}
