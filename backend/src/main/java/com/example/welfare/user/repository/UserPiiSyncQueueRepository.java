package com.example.welfare.user.repository;

import com.example.welfare.user.entity.UserPiiSyncQueue;
import com.example.welfare.user.entity.UserPiiSyncQueueStatus;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

import java.util.List;
import java.util.Optional;

public interface UserPiiSyncQueueRepository extends JpaRepository<UserPiiSyncQueue, Long> {

    Optional<UserPiiSyncQueue> findByUserKey(String userKey);

    List<UserPiiSyncQueue> findByStatusOrderByLastEnqueuedAtAscIdAsc(
            UserPiiSyncQueueStatus status,
            Pageable pageable
    );

    List<UserPiiSyncQueue> findByStatusOrderByLastAttemptAtAscIdAsc(
            UserPiiSyncQueueStatus status,
            Pageable pageable
    );

    @Transactional
    @Modifying
    long deleteByUserKey(String userKey);
}
