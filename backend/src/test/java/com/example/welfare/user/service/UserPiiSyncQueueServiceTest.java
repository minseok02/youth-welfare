package com.example.welfare.user.service;

import com.example.welfare.user.entity.UserPiiSyncQueue;
import com.example.welfare.user.entity.UserPiiSyncQueueStatus;
import com.example.welfare.user.repository.UserPiiSyncQueueCommandRepository;
import com.example.welfare.user.repository.UserPiiSyncQueueReadRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class UserPiiSyncQueueServiceTest {

    @Mock
    private UserPiiSyncQueueReadRepository userPiiSyncQueueReadRepository;

    @Mock
    private UserPiiSyncQueueCommandRepository userPiiSyncQueueCommandRepository;

    @InjectMocks
    private UserPiiSyncQueueService userPiiSyncQueueService;

    @Test
    @DisplayName("enqueue는 기존 queue row를 갱신한 뒤 command repository로 저장한다")
    void enqueueUpdatesExistingQueue() {
        UserPiiSyncQueue queue = UserPiiSyncQueue.builder().userKey("user-key-1").build();
        given(userPiiSyncQueueReadRepository.findByUserKey("user-key-1")).willReturn(Optional.of(queue));

        userPiiSyncQueueService.enqueue("user-key-1", "enc-email", "enc-name", "enc-birth", "enc-phone");

        then(userPiiSyncQueueCommandRepository).should().save(queue);
        assertThat(queue.getEmailEnc()).isEqualTo("enc-email");
        assertThat(queue.getStatus()).isEqualTo(UserPiiSyncQueueStatus.PENDING);
    }

    @Test
    @DisplayName("enqueue는 queue row가 없으면 새 row를 만들어 저장한다")
    void enqueueCreatesQueueWhenMissing() {
        given(userPiiSyncQueueReadRepository.findByUserKey("user-key-2")).willReturn(Optional.empty());
        ArgumentCaptor<UserPiiSyncQueue> queueCaptor = ArgumentCaptor.forClass(UserPiiSyncQueue.class);

        userPiiSyncQueueService.enqueue("user-key-2", "enc-email", "enc-name", "enc-birth", null);

        then(userPiiSyncQueueCommandRepository).should().save(queueCaptor.capture());
        UserPiiSyncQueue savedQueue = queueCaptor.getValue();
        assertThat(savedQueue.getUserKey()).isEqualTo("user-key-2");
        assertThat(savedQueue.getEmailEnc()).isEqualTo("enc-email");
        assertThat(savedQueue.getStatus()).isEqualTo(UserPiiSyncQueueStatus.PENDING);
    }

    @Test
    @DisplayName("queue read API는 read repository에 위임한다")
    void readApisDelegate() {
        UserPiiSyncQueue queue = UserPiiSyncQueue.builder().userKey("failed-user").build();
        given(userPiiSyncQueueReadRepository.findByUserKey("user-key-3")).willReturn(Optional.of(queue));
        given(userPiiSyncQueueReadRepository.countByStatus(UserPiiSyncQueueStatus.FAILED)).willReturn(1L);
        given(userPiiSyncQueueReadRepository.findFailedSamples(5)).willReturn(List.of(queue));

        assertThat(userPiiSyncQueueService.findOptional("user-key-3")).contains(queue);
        assertThat(userPiiSyncQueueService.exists("user-key-3")).isTrue();
        assertThat(userPiiSyncQueueService.countByStatus(UserPiiSyncQueueStatus.FAILED)).isEqualTo(1L);
        assertThat(userPiiSyncQueueService.findFailedSamples(5)).containsExactly(queue);
    }

    @Test
    @DisplayName("deleteByUserKey는 blank userKey를 무시하고 정상 userKey만 command repository에 위임한다")
    void deleteByUserKeyIgnoresBlank() {
        assertThat(userPiiSyncQueueService.deleteByUserKey(" ")).isZero();

        then(userPiiSyncQueueCommandRepository).shouldHaveNoInteractions();

        given(userPiiSyncQueueCommandRepository.deleteByUserKey("user-key-4")).willReturn(1L);

        assertThat(userPiiSyncQueueService.deleteByUserKey("user-key-4")).isEqualTo(1L);
        then(userPiiSyncQueueCommandRepository).should().deleteByUserKey("user-key-4");
    }
}
