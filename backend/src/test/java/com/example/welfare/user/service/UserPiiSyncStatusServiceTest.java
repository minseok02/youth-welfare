package com.example.welfare.user.service;

import com.example.welfare.user.dto.response.UserPiiSyncStatusResponse;
import com.example.welfare.user.entity.UserPiiSyncQueue;
import com.example.welfare.user.entity.UserPiiSyncQueueStatus;
import com.example.welfare.user.repository.UserPiiSyncQueueRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class UserPiiSyncStatusServiceTest {

    @Mock
    private UserPiiSyncQueueRepository userPiiSyncQueueRepository;

    @Test
    @DisplayName("queue status는 count와 oldest/latest snapshot, failed sample을 함께 반환한다")
    void getStatusBuildsQueueSnapshot() {
        LocalDateTime now = LocalDateTime.of(2026, 4, 28, 20, 30, 0);
        UserPiiSyncQueue pending = UserPiiSyncQueue.builder()
                .userKey("pending-user")
                .status(UserPiiSyncQueueStatus.PENDING)
                .lastEnqueuedAt(now.minusMinutes(20))
                .build();
        UserPiiSyncQueue failed = UserPiiSyncQueue.builder()
                .userKey("failed-user")
                .status(UserPiiSyncQueueStatus.FAILED)
                .attemptCount(3)
                .lastAttemptAt(now.minusMinutes(10))
                .lastError("app_pii timeout")
                .build();
        UserPiiSyncQueue synced = UserPiiSyncQueue.builder()
                .userKey("synced-user")
                .status(UserPiiSyncQueueStatus.SYNCED)
                .lastSyncedAt(now.minusMinutes(1))
                .build();

        given(userPiiSyncQueueRepository.countByStatus(UserPiiSyncQueueStatus.PENDING)).willReturn(2L);
        given(userPiiSyncQueueRepository.countByStatus(UserPiiSyncQueueStatus.FAILED)).willReturn(1L);
        given(userPiiSyncQueueRepository.countByStatus(UserPiiSyncQueueStatus.SYNCED)).willReturn(7L);
        given(userPiiSyncQueueRepository.findFirstByStatusOrderByLastEnqueuedAtAscIdAsc(UserPiiSyncQueueStatus.PENDING))
                .willReturn(Optional.of(pending));
        given(userPiiSyncQueueRepository.findFirstByStatusOrderByLastAttemptAtAscIdAsc(UserPiiSyncQueueStatus.FAILED))
                .willReturn(Optional.of(failed));
        given(userPiiSyncQueueRepository.findFirstByStatusOrderByLastSyncedAtDescIdDesc(UserPiiSyncQueueStatus.SYNCED))
                .willReturn(Optional.of(synced));
        given(userPiiSyncQueueRepository.findByStatusOrderByAttemptCountDescLastAttemptAtDescIdDesc(
                any(), any(Pageable.class)))
                .willReturn(List.of(failed));

        UserPiiSyncStatusService service = new UserPiiSyncStatusService(userPiiSyncQueueRepository);

        UserPiiSyncStatusResponse response = service.getStatus(5);

        assertThat(response.pendingCount()).isEqualTo(2);
        assertThat(response.failedCount()).isEqualTo(1);
        assertThat(response.syncedCount()).isEqualTo(7);
        assertThat(response.oldestPendingUserKey()).isEqualTo("pending-user");
        assertThat(response.oldestPendingEnqueuedAt()).isEqualTo(now.minusMinutes(20));
        assertThat(response.oldestFailedUserKey()).isEqualTo("failed-user");
        assertThat(response.oldestFailedAttemptAt()).isEqualTo(now.minusMinutes(10));
        assertThat(response.latestSyncedAt()).isEqualTo(now.minusMinutes(1));
        assertThat(response.failedSamples()).hasSize(1);
        assertThat(response.failedSamples().get(0).userKey()).isEqualTo("failed-user");
        assertThat(response.failedSamples().get(0).attemptCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("failed sample limit은 과도한 조회를 막기 위해 1 이상 20 이하로 고정한다")
    void getStatusClampsFailedSampleLimit() {
        given(userPiiSyncQueueRepository.countByStatus(any(UserPiiSyncQueueStatus.class))).willReturn(0L);
        given(userPiiSyncQueueRepository.findFirstByStatusOrderByLastEnqueuedAtAscIdAsc(any())).willReturn(Optional.empty());
        given(userPiiSyncQueueRepository.findFirstByStatusOrderByLastAttemptAtAscIdAsc(any())).willReturn(Optional.empty());
        given(userPiiSyncQueueRepository.findFirstByStatusOrderByLastSyncedAtDescIdDesc(any())).willReturn(Optional.empty());
        given(userPiiSyncQueueRepository.findByStatusOrderByAttemptCountDescLastAttemptAtDescIdDesc(
                any(), any(Pageable.class)))
                .willReturn(List.of());

        UserPiiSyncStatusService service = new UserPiiSyncStatusService(userPiiSyncQueueRepository);
        service.getStatus(0);
        service.getStatus(999);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        then(userPiiSyncQueueRepository).should(times(2))
                .findByStatusOrderByAttemptCountDescLastAttemptAtDescIdDesc(
                        eq(UserPiiSyncQueueStatus.FAILED),
                        pageableCaptor.capture()
                );

        assertThat(pageableCaptor.getAllValues()).extracting(Pageable::getPageSize)
                .containsExactly(1, 20);
    }
}
