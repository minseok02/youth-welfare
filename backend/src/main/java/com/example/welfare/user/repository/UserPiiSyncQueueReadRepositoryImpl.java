package com.example.welfare.user.repository;

import com.example.welfare.user.entity.UserPiiSyncQueue;
import com.example.welfare.user.entity.UserPiiSyncQueueStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class UserPiiSyncQueueReadRepositoryImpl implements UserPiiSyncQueueReadRepository {

    private final UserPiiSyncQueueRepository userPiiSyncQueueRepository;

    @Override
    public Optional<UserPiiSyncQueue> findByUserKey(String userKey) {
        return userPiiSyncQueueRepository.findByUserKey(userKey);
    }

    @Override
    public long countByStatus(UserPiiSyncQueueStatus status) {
        return userPiiSyncQueueRepository.countByStatus(status);
    }

    @Override
    public Optional<UserPiiSyncQueue> findOldestPending() {
        return userPiiSyncQueueRepository.findFirstByStatusOrderByLastEnqueuedAtAscIdAsc(UserPiiSyncQueueStatus.PENDING);
    }

    @Override
    public Optional<UserPiiSyncQueue> findOldestFailed() {
        return userPiiSyncQueueRepository.findFirstByStatusOrderByLastAttemptAtAscIdAsc(UserPiiSyncQueueStatus.FAILED);
    }

    @Override
    public Optional<UserPiiSyncQueue> findLatestSynced() {
        return userPiiSyncQueueRepository.findFirstByStatusOrderByLastSyncedAtDescIdDesc(UserPiiSyncQueueStatus.SYNCED);
    }

    @Override
    public List<UserPiiSyncQueue> findFailedSamples(int limit) {
        return userPiiSyncQueueRepository.findByStatusOrderByAttemptCountDescLastAttemptAtDescIdDesc(
                UserPiiSyncQueueStatus.FAILED,
                PageRequest.of(0, limit)
        );
    }

    @Override
    public List<String> findReplayFailedUserKeys(int limit) {
        return userPiiSyncQueueRepository.findByStatusOrderByLastAttemptAtAscIdAsc(
                        UserPiiSyncQueueStatus.FAILED,
                        PageRequest.of(0, limit))
                .stream()
                .map(UserPiiSyncQueue::getUserKey)
                .toList();
    }

    @Override
    public List<String> findReplayPendingUserKeys(int limit) {
        return userPiiSyncQueueRepository.findByStatusOrderByLastEnqueuedAtAscIdAsc(
                        UserPiiSyncQueueStatus.PENDING,
                        PageRequest.of(0, limit))
                .stream()
                .map(UserPiiSyncQueue::getUserKey)
                .toList();
    }
}
