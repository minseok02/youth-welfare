package com.example.welfare.user.repository;

import com.example.welfare.user.entity.UserPiiSyncQueue;
import com.example.welfare.user.entity.UserPiiSyncQueueStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
@RequiredArgsConstructor
public class UserPiiSyncQueueCommandRepositoryImpl implements UserPiiSyncQueueCommandRepository {

    private final UserPiiSyncQueueRepository userPiiSyncQueueRepository;

    @Override
    public UserPiiSyncQueue save(UserPiiSyncQueue queue) {
        return userPiiSyncQueueRepository.save(queue);
    }

    @Override
    public long deleteByUserKey(String userKey) {
        return userPiiSyncQueueRepository.deleteByUserKey(userKey);
    }

    @Override
    public long deleteSyncedBefore(LocalDateTime before) {
        return userPiiSyncQueueRepository.deleteByStatusAndLastSyncedAtBefore(UserPiiSyncQueueStatus.SYNCED, before);
    }
}
