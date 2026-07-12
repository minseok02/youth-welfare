package com.example.welfare.global.service;

import com.example.welfare.notification.repository.NotificationAttemptLogCommandRepository;
import com.example.welfare.recommend.repository.RecommendationRunLogCommandRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class OperationalLogRetentionService {

    private final RecommendationRunLogCommandRepository recommendationRunLogCommandRepository;
    private final NotificationAttemptLogCommandRepository notificationAttemptLogCommandRepository;
    private final AppSchedulerGate appSchedulerGate;

    @Value("${observability.log-retention.days:90}")
    private int retentionDays;

    @Scheduled(
            cron = "${observability.log-retention.cron:0 40 3 * * *}",
            zone = "${observability.log-retention.zone:Asia/Seoul}"
    )
    public void cleanupOldOperationalLogs() {
        if (!appSchedulerGate.shouldRun("OperationalLogRetentionService.cleanupOldOperationalLogs")) {
            return;
        }
        int effectiveRetentionDays = Math.max(retentionDays, 1);
        LocalDateTime before = LocalDateTime.now().minusDays(effectiveRetentionDays);
        try {
            int recommendationRunDeleted = recommendationRunLogCommandRepository.deleteOlderThan(before);
            int notificationAttemptDeleted = notificationAttemptLogCommandRepository.deleteOlderThan(before);
            log.info("[OperationalLogRetentionService] operational log cleanup completed before={} retentionDays={} recommendationRunDeleted={} notificationAttemptDeleted={}",
                    before, effectiveRetentionDays, recommendationRunDeleted, notificationAttemptDeleted);
        } catch (RuntimeException e) {
            log.warn("[OperationalLogRetentionService] operational log cleanup failed before={} retentionDays={} errorType={}",
                    before, effectiveRetentionDays, e.getClass().getSimpleName());
        }
    }
}
