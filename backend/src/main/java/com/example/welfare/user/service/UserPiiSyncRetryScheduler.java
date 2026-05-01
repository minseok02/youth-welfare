package com.example.welfare.user.service;

import com.example.welfare.user.dto.response.UserPiiSyncReplayResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserPiiSyncRetryScheduler {

    private final UserPiiSyncReplayService userPiiSyncReplayService;

    @Value("${user.pii-sync.retry.enabled:true}")
    private boolean enabled;

    @Value("${user.pii-sync.retry.batch-size:100}")
    private int batchSize;

    @Scheduled(
            fixedDelayString = "${user.pii-sync.retry.fixed-delay-ms:300000}",
            initialDelayString = "${user.pii-sync.retry.initial-delay-ms:60000}"
    )
    public void retryQueuedUserPiiSync() {
        if (!enabled) {
            log.debug("[UserPiiSyncRetryScheduler] automatic retry disabled");
            return;
        }

        UserPiiSyncReplayResponse response = userPiiSyncReplayService.replay(null, batchSize);
        if (response.attemptedCount() > 0) {
            log.info("[UserPiiSyncRetryScheduler] automatic replay complete attempted={} synced={} failed={} missing={}",
                    response.attemptedCount(),
                    response.syncedCount(),
                    response.failedCount(),
                    response.missingCount());
        } else {
            log.debug("[UserPiiSyncRetryScheduler] no queued user_pii sync rows to replay");
        }
    }
}
