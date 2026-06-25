package com.example.welfare.user.repository;

import com.example.welfare.user.entity.UserPiiSyncQueue;
import com.example.welfare.user.entity.UserPiiSyncQueueStatus;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserPiiSyncQueueRepository extends JpaRepository<UserPiiSyncQueue, Long> {

    Optional<UserPiiSyncQueue> findByUserKey(String userKey);

    long countByStatus(UserPiiSyncQueueStatus status);

    Optional<UserPiiSyncQueue> findFirstByStatusOrderByLastEnqueuedAtAscIdAsc(UserPiiSyncQueueStatus status);

    Optional<UserPiiSyncQueue> findFirstByStatusOrderByLastAttemptAtAscIdAsc(UserPiiSyncQueueStatus status);

    Optional<UserPiiSyncQueue> findFirstByStatusOrderByLastSyncedAtDescIdDesc(UserPiiSyncQueueStatus status);

    List<UserPiiSyncQueue> findByStatusOrderByLastEnqueuedAtAscIdAsc(
            UserPiiSyncQueueStatus status,
            Pageable pageable
    );

    List<UserPiiSyncQueue> findByStatusOrderByLastAttemptAtAscIdAsc(
            UserPiiSyncQueueStatus status,
            Pageable pageable
    );

    List<UserPiiSyncQueue> findByStatusOrderByAttemptCountDescLastAttemptAtDescIdDesc(
            UserPiiSyncQueueStatus status,
            Pageable pageable
    );

    @Query("""
            select q
            from UserPiiSyncQueue q
            where (q.emailEnc is not null and q.emailEnc <> '' and q.emailEnc not like 'v2:%')
               or (q.nameEnc is not null and q.nameEnc <> '' and q.nameEnc not like 'v2:%')
               or (q.birthDateEnc is not null and q.birthDateEnc <> '' and q.birthDateEnc not like 'v2:%')
               or (q.phoneEnc is not null and q.phoneEnc <> '' and q.phoneEnc not like 'v2:%')
            order by q.userKey asc
            """)
    List<UserPiiSyncQueue> findLegacyEncryptedPayloads();

    @Transactional
    @Modifying
    long deleteByUserKey(String userKey);

    @Transactional
    @Modifying
    long deleteByStatusAndLastSyncedAtBefore(UserPiiSyncQueueStatus status, LocalDateTime before);
}
