package com.example.welfare.notification.repository;

public interface WebPushSubscriptionCleanupCommandRepository {

    void deleteByIdAndUserKey(Long subscriptionId, String userKey);
}
