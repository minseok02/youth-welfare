package com.example.welfare.user.service;

import com.example.welfare.user.entity.UserPiiSyncQueue;
import com.example.welfare.user.entity.UserPiiSyncQueueStatus;
import com.example.welfare.user.repository.UserPiiSyncQueueCommandRepository;
import com.example.welfare.user.repository.UserPiiSyncQueueReadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserPiiSyncQueueService {

    private final UserPiiSyncQueueReadRepository userPiiSyncQueueReadRepository;
    private final UserPiiSyncQueueCommandRepository userPiiSyncQueueCommandRepository;

    public void enqueue(String userKey, String emailEnc, String nameEnc, String birthDateEnc, String phoneEnc) {
        UserPiiSyncQueue queue = findOptional(userKey)
                .orElse(UserPiiSyncQueue.builder()
                        .userKey(userKey)
                        .build());
        queue.enqueue(emailEnc, nameEnc, birthDateEnc, phoneEnc);
        userPiiSyncQueueCommandRepository.save(queue);
    }

    public Optional<UserPiiSyncQueue> findOptional(String userKey) {
        return userPiiSyncQueueReadRepository.findByUserKey(userKey);
    }

    public boolean exists(String userKey) {
        return findOptional(userKey).isPresent();
    }

    public long countByStatus(UserPiiSyncQueueStatus status) {
        return userPiiSyncQueueReadRepository.countByStatus(status);
    }

    public Optional<UserPiiSyncQueue> findOldestPending() {
        return userPiiSyncQueueReadRepository.findOldestPending();
    }

    public Optional<UserPiiSyncQueue> findOldestFailed() {
        return userPiiSyncQueueReadRepository.findOldestFailed();
    }

    public Optional<UserPiiSyncQueue> findLatestSynced() {
        return userPiiSyncQueueReadRepository.findLatestSynced();
    }

    public List<UserPiiSyncQueue> findFailedSamples(int limit) {
        return userPiiSyncQueueReadRepository.findFailedSamples(limit);
    }

    public List<String> findReplayFailedUserKeys(int limit) {
        return userPiiSyncQueueReadRepository.findReplayFailedUserKeys(limit);
    }

    public List<String> findReplayPendingUserKeys(int limit) {
        return userPiiSyncQueueReadRepository.findReplayPendingUserKeys(limit);
    }
}
