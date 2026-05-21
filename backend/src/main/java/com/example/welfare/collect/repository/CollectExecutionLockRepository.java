package com.example.welfare.collect.repository;

import java.time.LocalDateTime;

public interface CollectExecutionLockRepository {

    boolean tryAcquire(String lockName, String ownerToken, LocalDateTime now, LocalDateTime lockedUntil);

    boolean refresh(String lockName, String ownerToken, LocalDateTime now, LocalDateTime lockedUntil);
}
