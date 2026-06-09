package com.example.welfare.user.repository;

import com.example.welfare.user.entity.UserPiiSyncQueue;

public interface UserPiiSyncQueueCommandRepository {

    UserPiiSyncQueue save(UserPiiSyncQueue queue);

    long deleteByUserKey(String userKey);
}
