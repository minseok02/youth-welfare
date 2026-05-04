package com.example.welfare.user.service;

import com.example.welfare.user.entity.UserPiiSyncQueue;
import com.example.welfare.user.entity.UserPiiSyncQueueStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserPiiSyncProcessor {

    private static final int MAX_ERROR_LENGTH = 500;

    private final UserPiiSyncQueueService userPiiSyncQueueService;
    private final UserPiiCommandService userPiiCommandService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UserPiiSyncQueueStatus process(String userKey) {
        UserPiiSyncQueue queue = userPiiSyncQueueService.findOptional(userKey)
                .orElse(null);
        if (queue == null) {
            log.warn("[UserPiiSyncProcessor] queue row not found userKey={}", userKey);
            return null;
        }

        try {
            userPiiCommandService.upsertUserPii(
                    queue.getUserKey(),
                    queue.getEmailEnc(),
                    queue.getNameEnc(),
                    queue.getBirthDateEnc(),
                    queue.getPhoneEnc()
            );
            queue.markSynced();
        } catch (RuntimeException e) {
            queue.markFailed(truncateErrorMessage(e.getMessage()));
            log.error("[UserPiiSyncProcessor] app_pii sync failed userKey={}", userKey, e);
        }
        return queue.getStatus();
    }

    private String truncateErrorMessage(String message) {
        if (message == null || message.isBlank()) {
            return "unknown error";
        }
        return message.length() <= MAX_ERROR_LENGTH
                ? message
                : message.substring(0, MAX_ERROR_LENGTH);
    }
}
