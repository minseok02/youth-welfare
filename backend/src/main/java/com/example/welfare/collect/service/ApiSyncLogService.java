package com.example.welfare.collect.service;

import com.example.welfare.collect.entity.ApiSyncLog;
import com.example.welfare.collect.repository.ApiSyncLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApiSyncLogService {

    private static final String INTERRUPTED_REASON = "애플리케이션 재시작 또는 비정상 종료로 수집 실행이 중단됨";

    private final ApiSyncLogRepository apiSyncLogRepository;

    @Transactional
    public int recoverInterruptedRuns() {
        int recovered = apiSyncLogRepository.markRunningLogsAsFailed(INTERRUPTED_REASON);
        if (recovered == 0) {
            return 0;
        }
        log.warn("[ApiSyncLogService] 이전 실행에서 남은 RUNNING 수집 로그 {}건을 FAILED로 정리했습니다.", recovered);
        return recovered;
    }

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
