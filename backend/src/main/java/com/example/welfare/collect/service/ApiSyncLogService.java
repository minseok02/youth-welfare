package com.example.welfare.collect.service;

import com.example.welfare.collect.entity.ApiSyncLog;
import com.example.welfare.collect.repository.ApiSyncLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApiSyncLogService {

    private final ApiSyncLogRepository apiSyncLogRepository;

    public CollectResult runWithLog(String jobName, CollectTask task) {
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

    @FunctionalInterface
    public interface CollectTask {
        CollectResult run() throws Exception;
    }
}
