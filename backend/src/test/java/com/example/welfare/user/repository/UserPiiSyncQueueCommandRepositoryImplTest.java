package com.example.welfare.user.repository;

import com.example.welfare.user.entity.UserPiiSyncQueue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class UserPiiSyncQueueCommandRepositoryImplTest {

    @Mock
    private UserPiiSyncQueueRepository userPiiSyncQueueRepository;

    @InjectMocks
    private UserPiiSyncQueueCommandRepositoryImpl userPiiSyncQueueCommandRepository;

    @Test
    @DisplayName("queue command repository는 queue 저장을 위임한다")
    void saveDelegates() {
        UserPiiSyncQueue queue = UserPiiSyncQueue.builder().userKey("user-key-1").build();

        userPiiSyncQueueCommandRepository.save(queue);

        then(userPiiSyncQueueRepository).should().save(queue);
    }
}
