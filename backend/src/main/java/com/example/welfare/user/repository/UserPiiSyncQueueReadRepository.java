package com.example.welfare.user.repository;

import com.example.welfare.user.entity.UserPiiSyncQueue;
import com.example.welfare.user.entity.UserPiiSyncQueueStatus;

import java.util.List;
import java.util.Optional;

public interface UserPiiSyncQueueReadRepository {

    Optional<UserPiiSyncQueue> findByUserKey(String userKey);

    long countByStatus(UserPiiSyncQueueStatus status);

    Optional<UserPiiSyncQueue> findOldestPending();

    Optional<UserPiiSyncQueue> findOldestFailed();

    Optional<UserPiiSyncQueue> findLatestSynced();

    List<UserPiiSyncQueue> findFailedSamples(int limit);

    List<String> findReplayFailedUserKeys(int limit);

    List<String> findReplayPendingUserKeys(int limit);
}
