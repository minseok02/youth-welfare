package com.example.welfare.user.repository;

import com.example.welfare.user.entity.UserPiiSyncQueue;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class UserPiiSyncQueueCommandRepositoryImpl implements UserPiiSyncQueueCommandRepository {

    private final UserPiiSyncQueueRepository userPiiSyncQueueRepository;

    @Override
    public UserPiiSyncQueue save(UserPiiSyncQueue queue) {
        return userPiiSyncQueueRepository.save(queue);
    }
}
