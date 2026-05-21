package com.example.welfare.collect.repository;

public interface CollectExecutionLockCleanupCommandRepository {

    boolean release(String lockName, String ownerToken);
}
