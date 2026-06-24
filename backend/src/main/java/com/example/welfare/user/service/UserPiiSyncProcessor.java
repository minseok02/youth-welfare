package com.example.welfare.user.service;

import com.example.welfare.global.util.RedisKeyHash;
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

    private final UserPiiSyncQueueService userPiiSyncQueueService;
    private final UserPiiCommandService userPiiCommandService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UserPiiSyncQueueStatus process(String userKey) {
        UserPiiSyncQueue queue = userPiiSyncQueueService.findOptional(userKey)
                .orElse(null);
        if (queue == null) {
            log.warn("[UserPiiSyncProcessor] queue row not found userKeyHash={}", RedisKeyHash.sha256Hex(userKey));
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
            queue.markFailed(safeFailureMessage(e));
            log.error("[UserPiiSyncProcessor] app_pii sync failed userKeyHash={} errorType={}",
                    RedisKeyHash.sha256Hex(userKey), e.getClass().getSimpleName());
        }
        return queue.getStatus();
    }

    private String safeFailureMessage(RuntimeException e) {
        String errorType = e.getClass().getSimpleName();
        if (errorType == null || errorType.isBlank()) {
            errorType = "RuntimeException";
        }
        return "PII sync failed (" + errorType + ")";
    }
}
