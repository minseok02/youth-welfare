package com.example.welfare.user.service;

import com.example.welfare.user.entity.UserPiiSyncQueue;
import com.example.welfare.user.entity.UserPiiSyncQueueStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class UserPiiSyncProcessorTest {

    @Mock
    private UserPiiSyncQueueService userPiiSyncQueueService;

    @Mock
    private UserPiiCommandService userPiiCommandService;

    @Test
    @DisplayName("processor는 queue payload를 app_pii로 upsert하고 synced 상태로 마킹한다")
    void processMarksQueueSynced() {
        UserPiiSyncQueue queue = UserPiiSyncQueue.builder()
                .userKey("user-key-1")
                .build();
        queue.enqueue("enc-email", "enc-name", "enc-birth", "enc-phone");

        given(userPiiSyncQueueService.findOptional("user-key-1")).willReturn(Optional.of(queue));

        UserPiiSyncProcessor processor = new UserPiiSyncProcessor(userPiiSyncQueueService, userPiiCommandService);

        processor.process("user-key-1");

        then(userPiiCommandService).should()
                .upsertUserPii("user-key-1", "enc-email", "enc-name", "enc-birth", "enc-phone");
        assertThat(queue.getStatus()).isEqualTo(UserPiiSyncQueueStatus.SYNCED);
        assertThat(queue.getAttemptCount()).isEqualTo(1);
        assertThat(queue.getLastAttemptAt()).isNotNull();
        assertThat(queue.getLastSyncedAt()).isNotNull();
        assertThat(queue.getLastError()).isNull();
    }

    @Test
    @DisplayName("processor는 app_pii write 실패 시 민감정보 없는 failed 상태를 남긴다")
    void processMarksQueueFailedWhenAppPiiWriteFails() {
        UserPiiSyncQueue queue = UserPiiSyncQueue.builder()
                .userKey("user-key-2")
                .build();
        queue.enqueue("enc-email", "enc-name", "enc-birth", "enc-phone");

        given(userPiiSyncQueueService.findOptional("user-key-2")).willReturn(Optional.of(queue));
        org.mockito.BDDMockito.willThrow(new RuntimeException("insert failed values enc-email user@example.com"))
                .given(userPiiCommandService)
                .upsertUserPii("user-key-2", "enc-email", "enc-name", "enc-birth", "enc-phone");

        UserPiiSyncProcessor processor = new UserPiiSyncProcessor(userPiiSyncQueueService, userPiiCommandService);

        processor.process("user-key-2");

        assertThat(queue.getStatus()).isEqualTo(UserPiiSyncQueueStatus.FAILED);
        assertThat(queue.getAttemptCount()).isEqualTo(1);
        assertThat(queue.getLastAttemptAt()).isNotNull();
        assertThat(queue.getLastSyncedAt()).isNull();
        assertThat(queue.getLastError()).isEqualTo("PII sync failed (RuntimeException)");
        assertThat(queue.getLastError()).doesNotContain("enc-email", "user@example.com");
    }
}
