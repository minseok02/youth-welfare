package com.example.welfare.user.service;

import com.example.welfare.user.dto.response.UserPiiSyncReplayResponse;
import com.example.welfare.user.entity.UserPiiSyncQueue;
import com.example.welfare.user.entity.UserPiiSyncQueueStatus;
import com.example.welfare.user.repository.UserPiiSyncQueueRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class UserPiiSyncReplayServiceTest {

    @Mock
    private UserPiiSyncQueueService userPiiSyncQueueService;

    @Mock
    private UserPiiSyncQueueRepository userPiiSyncQueueRepository;

    @Mock
    private UserPiiSyncProcessor userPiiSyncProcessor;

    @Test
    @DisplayName("bulk replay는 failed를 우선 재처리하고 남는 limit만큼 pending을 이어서 처리한다")
    void replayBulkPrioritizesFailedThenPending() {
        UserPiiSyncQueue failed = UserPiiSyncQueue.builder()
                .userKey("failed-user")
                .status(UserPiiSyncQueueStatus.FAILED)
                .build();
        UserPiiSyncQueue pending = UserPiiSyncQueue.builder()
                .userKey("pending-user")
                .status(UserPiiSyncQueueStatus.PENDING)
                .build();

        given(userPiiSyncQueueRepository.findByStatusOrderByLastAttemptAtAscIdAsc(
                UserPiiSyncQueueStatus.FAILED, Pageable.ofSize(2)))
                .willReturn(List.of(failed));
        given(userPiiSyncQueueRepository.findByStatusOrderByLastEnqueuedAtAscIdAsc(
                UserPiiSyncQueueStatus.PENDING, Pageable.ofSize(1)))
                .willReturn(List.of(pending));
        given(userPiiSyncProcessor.process("failed-user")).willReturn(UserPiiSyncQueueStatus.SYNCED);
        given(userPiiSyncProcessor.process("pending-user")).willReturn(UserPiiSyncQueueStatus.FAILED);

        UserPiiSyncReplayService service = new UserPiiSyncReplayService(
                userPiiSyncQueueService,
                userPiiSyncQueueRepository,
                userPiiSyncProcessor
        );

        UserPiiSyncReplayResponse response = service.replay(null, 2);

        assertThat(response.attemptedCount()).isEqualTo(2);
        assertThat(response.syncedCount()).isEqualTo(1);
        assertThat(response.failedCount()).isEqualTo(1);
        assertThat(response.missingCount()).isZero();
        then(userPiiSyncProcessor).should().process("failed-user");
        then(userPiiSyncProcessor).should().process("pending-user");
    }

    @Test
    @DisplayName("single replay는 없는 user_key면 missing으로 응답한다")
    void replaySingleReturnsMissingWhenQueueDoesNotExist() {
        given(userPiiSyncQueueService.exists("missing-user")).willReturn(false);

        UserPiiSyncReplayService service = new UserPiiSyncReplayService(
                userPiiSyncQueueService,
                userPiiSyncQueueRepository,
                userPiiSyncProcessor
        );

        UserPiiSyncReplayResponse response = service.replay("missing-user", 10);

        assertThat(response.attemptedCount()).isZero();
        assertThat(response.syncedCount()).isZero();
        assertThat(response.failedCount()).isZero();
        assertThat(response.missingCount()).isEqualTo(1);
        then(userPiiSyncProcessor).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("single replay는 processor의 최종 상태를 그대로 집계한다")
    void replaySingleCountsProcessorResult() {
        UserPiiSyncQueue queue = UserPiiSyncQueue.builder()
                .userKey("user-key-1")
                .build();
        given(userPiiSyncQueueService.exists("user-key-1")).willReturn(true);
        given(userPiiSyncProcessor.process("user-key-1")).willReturn(UserPiiSyncQueueStatus.SYNCED);

        UserPiiSyncReplayService service = new UserPiiSyncReplayService(
                userPiiSyncQueueService,
                userPiiSyncQueueRepository,
                userPiiSyncProcessor
        );

        UserPiiSyncReplayResponse response = service.replay("user-key-1", 10);

        assertThat(response.attemptedCount()).isEqualTo(1);
        assertThat(response.syncedCount()).isEqualTo(1);
        assertThat(response.failedCount()).isZero();
        assertThat(response.missingCount()).isZero();
    }
}
