package com.example.welfare.user.repository;

import com.example.welfare.user.entity.UserPiiSyncQueue;

import java.time.LocalDateTime;

public interface UserPiiSyncQueueCommandRepository {

    UserPiiSyncQueue save(UserPiiSyncQueue queue);

    long deleteByUserKey(String userKey);

    long deleteSyncedBefore(LocalDateTime before);
}
