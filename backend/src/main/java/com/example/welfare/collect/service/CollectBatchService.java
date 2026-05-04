package com.example.welfare.collect.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class CollectBatchService {

    private final CollectExecutionGuard collectExecutionGuard;
    private final CollectSourceExecutionService collectSourceExecutionService;

    @Scheduled(cron = "0 0 2 * * *", zone = "Asia/Seoul")
    public void collectAll() {
        collectAllNow();
    }

    public void collectAllNow() {
        collectExecutionGuard.runExclusive("collect-all", () -> {
            log.info("[CollectBatchService] 공공API 수집 시작");
            CollectSource.executionOrder().forEach(this::runSourceSafely);
            log.info("[CollectBatchService] 공공API 수집 완료");
        });
    }

    private void runSourceSafely(CollectSource source) {
        try {
            collectSourceExecutionService.collectSource(source);
        } catch (Exception e) {
            log.warn("[CollectBatchService][{}] 수집 실패 - 다음 source 계속 진행: {}", source.jobName(), e.getMessage(), e);
        }
    }
}
