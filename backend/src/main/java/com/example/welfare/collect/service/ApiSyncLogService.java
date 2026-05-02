package com.example.welfare.collect.service;

import com.example.welfare.collect.entity.ApiSyncLog;
import com.example.welfare.collect.repository.ApiSyncLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApiSyncLogService {

    private static final String STALE_RUNNING_ERROR_CODE = "InterruptedRun";
    private static final String STALE_RUNNING_ERROR_MESSAGE = "previous running log was auto-closed before a new collect run";

    private final ApiSyncLogRepository apiSyncLogRepository;

    public CollectResult runWithLog(String jobName, CollectTask task) {
        closeStaleRunningLogs(jobName);
        ApiSyncLog syncLog = apiSyncLogRepository.save(ApiSyncLog.start(jobName));
        try {
            CollectResult result = task.run();
            syncLog.complete(result);
            apiSyncLogRepository.save(syncLog);
            log.info("[ApiSyncLogService] 수집 로그 저장 job={} status={} requested={} saved={} skipped={} filtered={} failed={}",
                    jobName,
                    syncLog.getStatus(),
                    result.requestedCount(),
                    result.savedCount(),
                    result.skippedCount(),
                    result.filteredCount(),
                    result.failedCount());
            return result;
        } catch (RuntimeException e) {
            syncLog.fail(e);
            apiSyncLogRepository.save(syncLog);
            throw e;
        } catch (Exception e) {
            syncLog.fail(e);
            apiSyncLogRepository.save(syncLog);
            throw new IllegalStateException(e);
        }
    }

    private void closeStaleRunningLogs(String jobName) {
        int healedCount = apiSyncLogRepository.failStaleRunningLogs(
                jobName,
                LocalDateTime.now(),
                STALE_RUNNING_ERROR_CODE,
                STALE_RUNNING_ERROR_MESSAGE
        );
        if (healedCount > 0) {
            log.warn("[ApiSyncLogService] stale RUNNING collect log auto-closed job={} count={}", jobName, healedCount);
        }
    }

    @FunctionalInterface
    public interface CollectTask {
        CollectResult run() throws Exception;
    }
}
