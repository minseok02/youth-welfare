package com.example.welfare.user.service;

import com.example.welfare.user.entity.UserPiiSyncQueue;
import com.example.welfare.user.repository.UserPiiSyncQueueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserPiiSyncQueueService {

    private final UserPiiSyncQueueRepository userPiiSyncQueueRepository;

    public void enqueue(String userKey, String emailEnc, String nameEnc, String birthDateEnc, String phoneEnc) {
        UserPiiSyncQueue queue = userPiiSyncQueueRepository.findByUserKey(userKey)
                .orElse(UserPiiSyncQueue.builder()
                        .userKey(userKey)
                        .build());
        queue.enqueue(emailEnc, nameEnc, birthDateEnc, phoneEnc);
        userPiiSyncQueueRepository.save(queue);
    }
}
