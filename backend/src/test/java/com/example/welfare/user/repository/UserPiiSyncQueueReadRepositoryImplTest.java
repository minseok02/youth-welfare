package com.example.welfare.user.repository;

import com.example.welfare.user.entity.UserPiiSyncQueue;
import com.example.welfare.user.entity.UserPiiSyncQueueStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class UserPiiSyncQueueReadRepositoryImplTest {

    @Mock
    private UserPiiSyncQueueRepository userPiiSyncQueueRepository;

    @InjectMocks
    private UserPiiSyncQueueReadRepositoryImpl userPiiSyncQueueReadRepository;

    @Test
    @DisplayName("queue read repository는 userKey 조회를 위임한다")
    void findByUserKeyDelegates() {
        UserPiiSyncQueue queue = UserPiiSyncQueue.builder().userKey("user-key-1").build();
        given(userPiiSyncQueueRepository.findByUserKey("user-key-1")).willReturn(Optional.of(queue));

        assertThat(userPiiSyncQueueReadRepository.findByUserKey("user-key-1")).contains(queue);
    }

    @Test
    @DisplayName("queue read repository는 상태별 count 조회를 위임한다")
    void countByStatusDelegates() {
        given(userPiiSyncQueueRepository.countByStatus(UserPiiSyncQueueStatus.FAILED)).willReturn(3L);

        assertThat(userPiiSyncQueueReadRepository.countByStatus(UserPiiSyncQueueStatus.FAILED)).isEqualTo(3L);
    }

    @Test
    @DisplayName("queue read repository는 failed sample 조회를 위임한다")
    void findFailedSamplesDelegates() {
        UserPiiSyncQueue queue = UserPiiSyncQueue.builder().userKey("failed-user").build();
        given(userPiiSyncQueueRepository.findByStatusOrderByAttemptCountDescLastAttemptAtDescIdDesc(
                UserPiiSyncQueueStatus.FAILED,
                PageRequest.of(0, 5)
        )).willReturn(List.of(queue));

        assertThat(userPiiSyncQueueReadRepository.findFailedSamples(5)).containsExactly(queue);
    }

    @Test
    @DisplayName("queue read repository는 replay 대상 userKey 목록 조회를 위임한다")
    void findReplayUserKeysDelegates() {
        UserPiiSyncQueue failed = UserPiiSyncQueue.builder().userKey("failed-user").build();
        UserPiiSyncQueue pending = UserPiiSyncQueue.builder().userKey("pending-user").build();
        given(userPiiSyncQueueRepository.findByStatusOrderByLastAttemptAtAscIdAsc(
                UserPiiSyncQueueStatus.FAILED,
                PageRequest.of(0, 2)
        )).willReturn(List.of(failed));
        given(userPiiSyncQueueRepository.findByStatusOrderByLastEnqueuedAtAscIdAsc(
                UserPiiSyncQueueStatus.PENDING,
                PageRequest.of(0, 2)
        )).willReturn(List.of(pending));

        assertThat(userPiiSyncQueueReadRepository.findReplayFailedUserKeys(2)).containsExactly("failed-user");
        assertThat(userPiiSyncQueueReadRepository.findReplayPendingUserKeys(2)).containsExactly("pending-user");
    }
}
