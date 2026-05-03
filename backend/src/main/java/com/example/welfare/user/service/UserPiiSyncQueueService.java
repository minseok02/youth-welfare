package com.example.welfare.user.service;

import com.example.welfare.user.entity.UserPiiSyncQueue;
import com.example.welfare.user.repository.UserPiiSyncQueueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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
}
