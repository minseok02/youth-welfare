package com.example.welfare.user.repository;

public interface UserWithdrawalDataCleanupCommandRepository {

    void cleanupByUserKey(String userKey);
}
