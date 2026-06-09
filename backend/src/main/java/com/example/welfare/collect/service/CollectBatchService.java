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

    public static final String SCHEDULE_CRON = "0 0 2 * * *";
    public static final String SCHEDULE_ZONE = "Asia/Seoul";
    public static final String SCHEDULE_LABEL = "매일 02:00 Asia/Seoul";

    private final CollectExecutionGuard collectExecutionGuard;
    private final CollectSourceExecutionService collectSourceExecutionService;

    @Scheduled(cron = SCHEDULE_CRON, zone = SCHEDULE_ZONE)
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
            log.warn("[CollectBatchService][{}] 수집 실패 - 다음 source 계속 진행 errorType={}",
                    source.jobName(), e.getClass().getSimpleName());
            return CollectBatchRunResult.SourceRunResult.failure(source, e);
        }
    }
}
