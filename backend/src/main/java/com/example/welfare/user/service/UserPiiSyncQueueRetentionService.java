package com.example.welfare.user.service;

import com.example.welfare.global.service.AppSchedulerGate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;

@Slf4j
@Component
public class UserPiiSyncQueueRetentionService {

    private final UserPiiSyncQueueService userPiiSyncQueueService;
    private final AppSchedulerGate appSchedulerGate;
    private final Clock clock;

    @Value("${user.pii-sync.retention.enabled:true}")
    private boolean enabled;

    @Value("${user.pii-sync.retention.synced-days:30}")
    private int syncedRetentionDays;

    @Autowired
    public UserPiiSyncQueueRetentionService(UserPiiSyncQueueService userPiiSyncQueueService,
                                            AppSchedulerGate appSchedulerGate) {
        this(userPiiSyncQueueService, appSchedulerGate, Clock.systemUTC());
    }

    UserPiiSyncQueueRetentionService(UserPiiSyncQueueService userPiiSyncQueueService,
                                     AppSchedulerGate appSchedulerGate,
                                     Clock clock) {
        this.userPiiSyncQueueService = userPiiSyncQueueService;
        this.appSchedulerGate = appSchedulerGate;
        this.clock = clock;
    }

    @Scheduled(
            cron = "${user.pii-sync.retention.cron:0 25 3 * * *}",
            zone = "${user.pii-sync.retention.zone:Asia/Seoul}"
    )
    public void cleanupSyncedRows() {
        if (!appSchedulerGate.shouldRun("UserPiiSyncQueueRetentionService.cleanupSyncedRows")) {
            return;
        }
        if (!enabled) {
            log.debug("[UserPiiSyncQueueRetentionService] synced queue retention disabled");
            return;
        }

        int effectiveRetentionDays = Math.max(syncedRetentionDays, 1);
        LocalDateTime before = LocalDateTime.now(clock).minusDays(effectiveRetentionDays);
        try {
            long deleted = userPiiSyncQueueService.deleteSyncedBefore(before);
            if (deleted > 0) {
                log.info("[UserPiiSyncQueueRetentionService] synced queue cleanup completed before={} retentionDays={} deleted={}",
                        before, effectiveRetentionDays, deleted);
            } else {
                log.debug("[UserPiiSyncQueueRetentionService] no synced queue rows to cleanup before={} retentionDays={}",
                        before, effectiveRetentionDays);
            }
        } catch (RuntimeException e) {
            log.warn("[UserPiiSyncQueueRetentionService] synced queue cleanup failed before={} retentionDays={} errorType={}",
                    before, effectiveRetentionDays, e.getClass().getSimpleName());
        }
    }
}
