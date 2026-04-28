package com.example.welfare.user.repository;

import com.example.welfare.user.entity.UserPiiSyncQueue;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

import java.util.Optional;

public interface UserPiiSyncQueueRepository extends JpaRepository<UserPiiSyncQueue, Long> {

    Optional<UserPiiSyncQueue> findByUserKey(String userKey);

    @Transactional
    @Modifying
    long deleteByUserKey(String userKey);
}
