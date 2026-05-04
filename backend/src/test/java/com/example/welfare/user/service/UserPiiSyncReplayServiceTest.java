package com.example.welfare.user.service;

import com.example.welfare.user.dto.response.UserPiiSyncReplayResponse;
import com.example.welfare.user.entity.UserPiiSyncQueueStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class UserPiiSyncReplayServiceTest {

    @Mock
    private UserPiiSyncQueueService userPiiSyncQueueService;

    @Mock
    private UserPiiSyncProcessor userPiiSyncProcessor;

    @Test
    @DisplayName("bulk replay는 failed를 우선 재처리하고 남는 limit만큼 pending을 이어서 처리한다")
    void replayBulkPrioritizesFailedThenPending() {
        given(userPiiSyncQueueService.findReplayFailedUserKeys(2))
                .willReturn(List.of("failed-user"));
        given(userPiiSyncQueueService.findReplayPendingUserKeys(1))
                .willReturn(List.of("pending-user"));
        given(userPiiSyncProcessor.process("failed-user")).willReturn(UserPiiSyncQueueStatus.SYNCED);
        given(userPiiSyncProcessor.process("pending-user")).willReturn(UserPiiSyncQueueStatus.FAILED);

        UserPiiSyncReplayService service = new UserPiiSyncReplayService(
                userPiiSyncQueueService,
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
        given(userPiiSyncQueueService.exists("user-key-1")).willReturn(true);
        given(userPiiSyncProcessor.process("user-key-1")).willReturn(UserPiiSyncQueueStatus.SYNCED);

        UserPiiSyncReplayService service = new UserPiiSyncReplayService(
                userPiiSyncQueueService,
                userPiiSyncProcessor
        );

        UserPiiSyncReplayResponse response = service.replay("user-key-1", 10);

        assertThat(response.attemptedCount()).isEqualTo(1);
        assertThat(response.syncedCount()).isEqualTo(1);
        assertThat(response.failedCount()).isZero();
        assertThat(response.missingCount()).isZero();
    }
}
