package com.example.welfare.collect.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

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

    public CollectBatchRunResult collectAllNow() {
        AtomicReference<CollectBatchRunResult> resultRef = new AtomicReference<>();
        collectExecutionGuard.runExclusive("collect-all", () -> {
            log.info("[CollectBatchService] 공공API 수집 시작");
            List<CollectBatchRunResult.SourceRunResult> sourceResults = new ArrayList<>();
            CollectSource.executionOrder().forEach(source -> sourceResults.add(runSourceSafely(source)));
            resultRef.set(new CollectBatchRunResult(sourceResults));
            log.info("[CollectBatchService] 공공API 수집 완료");
        });
        return resultRef.get();
    }

    private CollectBatchRunResult.SourceRunResult runSourceSafely(CollectSource source) {
        try {
            CollectResult result = collectSourceExecutionService.collectSource(source);
            return CollectBatchRunResult.SourceRunResult.success(source, result);
        } catch (Exception e) {
            log.warn("[CollectBatchService][{}] 수집 실패 - 다음 source 계속 진행: {}", source.jobName(), e.getMessage(), e);
            return CollectBatchRunResult.SourceRunResult.failure(source, e);
        }
    }
}
