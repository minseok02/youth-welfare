package com.example.welfare.user.service;

import com.example.welfare.user.entity.UserPiiSyncQueue;
import com.example.welfare.user.entity.UserPiiSyncQueueStatus;
import com.example.welfare.user.repository.UserPiiSyncQueueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserPiiSyncQueueService {

    private final UserPiiSyncQueueRepository userPiiSyncQueueRepository;

    public void enqueue(String userKey, String emailEnc, String nameEnc, String birthDateEnc, String phoneEnc) {
        UserPiiSyncQueue queue = findOptional(userKey)
                .orElse(UserPiiSyncQueue.builder()
                        .userKey(userKey)
                        .build());
        queue.enqueue(emailEnc, nameEnc, birthDateEnc, phoneEnc);
        userPiiSyncQueueRepository.save(queue);
    }

    public Optional<UserPiiSyncQueue> findOptional(String userKey) {
        return userPiiSyncQueueRepository.findByUserKey(userKey);
    }

    public boolean exists(String userKey) {
        return findOptional(userKey).isPresent();
    }

    public long countByStatus(UserPiiSyncQueueStatus status) {
        return userPiiSyncQueueRepository.countByStatus(status);
    }

    public Optional<UserPiiSyncQueue> findOldestPending() {
        return userPiiSyncQueueRepository.findFirstByStatusOrderByLastEnqueuedAtAscIdAsc(UserPiiSyncQueueStatus.PENDING);
    }

    public Optional<UserPiiSyncQueue> findOldestFailed() {
        return userPiiSyncQueueRepository.findFirstByStatusOrderByLastAttemptAtAscIdAsc(UserPiiSyncQueueStatus.FAILED);
    }

    public Optional<UserPiiSyncQueue> findLatestSynced() {
        return userPiiSyncQueueRepository.findFirstByStatusOrderByLastSyncedAtDescIdDesc(UserPiiSyncQueueStatus.SYNCED);
    }

    public List<UserPiiSyncQueue> findFailedSamples(int limit) {
        return userPiiSyncQueueRepository.findByStatusOrderByAttemptCountDescLastAttemptAtDescIdDesc(
                UserPiiSyncQueueStatus.FAILED,
                PageRequest.of(0, limit)
        );
    }

    public List<String> findReplayFailedUserKeys(int limit) {
        return userPiiSyncQueueRepository.findByStatusOrderByLastAttemptAtAscIdAsc(
                        UserPiiSyncQueueStatus.FAILED,
                        PageRequest.of(0, limit))
                .stream()
                .map(UserPiiSyncQueue::getUserKey)
                .toList();
    }

    public List<String> findReplayPendingUserKeys(int limit) {
        return userPiiSyncQueueRepository.findByStatusOrderByLastEnqueuedAtAscIdAsc(
                        UserPiiSyncQueueStatus.PENDING,
                        PageRequest.of(0, limit))
                .stream()
                .map(UserPiiSyncQueue::getUserKey)
                .toList();
    }
}
