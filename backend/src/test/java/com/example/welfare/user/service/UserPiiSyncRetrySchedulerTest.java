package com.example.welfare.user.service;

import com.example.welfare.user.dto.response.UserPiiSyncReplayResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserPiiSyncRetrySchedulerTest {

    @Mock
    private UserPiiSyncReplayService userPiiSyncReplayService;

    @Test
    @DisplayName("자동 retry가 활성화되면 batch size 기준으로 replay를 호출한다")
    void retryQueuedUserPiiSyncCallsReplayWhenEnabled() {
        UserPiiSyncRetryScheduler scheduler = new UserPiiSyncRetryScheduler(userPiiSyncReplayService);
        ReflectionTestUtils.setField(scheduler, "enabled", true);
        ReflectionTestUtils.setField(scheduler, "batchSize", 25);
        when(userPiiSyncReplayService.replay(null, 25))
                .thenReturn(new UserPiiSyncReplayResponse(2, 2, 0, 0));

        scheduler.retryQueuedUserPiiSync();

        verify(userPiiSyncReplayService).replay(null, 25);
    }

    @Test
    @DisplayName("자동 retry가 비활성화되면 replay를 호출하지 않는다")
    void retryQueuedUserPiiSyncSkipsWhenDisabled() {
        UserPiiSyncRetryScheduler scheduler = new UserPiiSyncRetryScheduler(userPiiSyncReplayService);
        ReflectionTestUtils.setField(scheduler, "enabled", false);
        ReflectionTestUtils.setField(scheduler, "batchSize", 25);

        scheduler.retryQueuedUserPiiSync();

        verify(userPiiSyncReplayService, never()).replay(null, 25);
    }
}
