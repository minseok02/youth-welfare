package com.example.welfare.collect.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
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
    private final CollectListDiffService collectListDiffService;
    private final CollectListChangePolicy collectListChangePolicy;

    @Value("${collect.list.rotation.bokjiro-detail-max-calls-per-run:100}")
    private int bokjiroDetailRotationMaxCalls;
    @Value("${collect.list.rotation.bokjiro-refresh-max-calls-per-run:50}")
    private int bokjiroRefreshRotationMaxCalls;
    @Value("${collect.list.rotation.gov24-detail-max-calls-per-run:50}")
    private int gov24DetailRotationMaxCalls;
    @Value("${collect.list.rotation.gov24-support-conditions-max-calls-per-run:50}")
    private int gov24SupportConditionsRotationMaxCalls;
    @Value("${collect.list.rotation.enabled:true}")
    private boolean rotationEnabled;

    @Scheduled(cron = SCHEDULE_CRON, zone = SCHEDULE_ZONE)
    public void collectAll() {
        collectAllNow();
    }

    public CollectBatchRunResult collectAllNow() {
        AtomicReference<CollectBatchRunResult> resultRef = new AtomicReference<>();
        collectExecutionGuard.runExclusive("collect-all", () -> {
            log.info("[CollectBatchService] 공공API daily list 수집 시작");
            List<CollectBatchRunResult.SourceRunResult> sourceResults = new ArrayList<>();
            List<ForcedDetailPlan> forcedDetailPlans = new ArrayList<>();
            CollectSource.executionOrder().forEach(source -> {
                CollectBatchRunResult.SourceRunResult sourceRunResult = runSourceSafely(source);
                sourceResults.add(sourceRunResult);
                collectForcedDetailPlan(sourceRunResult).ifPresent(forcedDetailPlans::add);
            });
            forcedDetailPlans.forEach(plan -> sourceResults.addAll(runForcedDetailsSafely(plan)));
            runRotationDetailSafely().forEach(sourceResults::add);
            resultRef.set(new CollectBatchRunResult(sourceResults));
            log.info("[CollectBatchService] 공공API daily list/detail rotation 수집 완료");
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

    private java.util.Optional<ForcedDetailPlan> collectForcedDetailPlan(CollectBatchRunResult.SourceRunResult sourceRunResult) {
        if (!sourceRunResult.success() || !sourceRunResult.source().isListSource()) {
            return java.util.Optional.empty();
        }
        if (sourceRunResult.result().failedCount() > 0) {
            log.warn("[CollectBatchService] list diff snapshot skipped after partial list collect source={} failed={}",
                    sourceRunResult.source().jobName(), sourceRunResult.result().failedCount());
            return java.util.Optional.empty();
        }
        try {
            CollectListDiffService.CollectListDiff diff =
                    collectListDiffService.recordSnapshot(sourceRunResult.source(), sourceRunResult.result());
            CollectListChangePolicy.Decision decision = collectListChangePolicy.decide(diff);
            if (decision.guardedCountDrop()) {
                log.warn("[CollectBatchService] list diff count drop guarded source={} snapshotId={} reason={}",
                        diff.source().jobName(), diff.snapshotId(), decision.reason());
                return java.util.Optional.empty();
            }
            if (!decision.forceDetail()) {
                log.info("[CollectBatchService] list diff below force threshold source={} snapshotId={} reason={}",
                        diff.source().jobName(), diff.snapshotId(), decision.reason());
                return java.util.Optional.empty();
            }
            log.warn("[CollectBatchService] list diff forced detail source={} snapshotId={} reason={}",
                    diff.source().jobName(), diff.snapshotId(), decision.reason());
            return java.util.Optional.of(new ForcedDetailPlan(
                    diff.source(),
                    decision.detailCandidateSourceIds(),
                    decision.reason()
            ));
        } catch (Exception e) {
            log.warn("[CollectBatchService] list diff snapshot failed source={} errorType={}",
                    sourceRunResult.source().jobName(), e.getClass().getSimpleName());
            return java.util.Optional.empty();
        }
    }

    private List<CollectBatchRunResult.SourceRunResult> runForcedDetailsSafely(ForcedDetailPlan plan) {
        try {
            return runForcedDetails(plan);
        } catch (Exception e) {
            CollectSource failureSource = forcedDetailResultSource(plan.listSource());
            log.warn("[CollectBatchService][{}] forced detail 실패 reason={} errorType={}",
                    failureSource.jobName(), plan.reason(), e.getClass().getSimpleName());
            return List.of(CollectBatchRunResult.SourceRunResult.failure(failureSource, e));
        }
    }

    private List<CollectBatchRunResult.SourceRunResult> runForcedDetails(ForcedDetailPlan plan) {
        List<String> candidates = plan.candidateSourceIds();
        if (candidates.isEmpty()) {
            return List.of();
        }
        return switch (plan.listSource()) {
            case YOUTH -> List.of(CollectBatchRunResult.SourceRunResult.success(
                    CollectSource.YOUTH,
                    aggregate(candidates.stream()
                            .map(collectSourceExecutionService::collectYouthDetailsForSourceId)
                            .toList())
            ));
            case BOKJIRO_CENTRAL, BOKJIRO_LOCAL -> List.of(CollectBatchRunResult.SourceRunResult.success(
                    CollectSource.BOKJIRO_DETAIL_REFRESH,
                    aggregate(candidates.stream()
                            .map(collectSourceExecutionService::collectBokjiroDetailsRefreshForSourceId)
                            .toList())
            ));
            case GOV24 -> List.of(
                    CollectBatchRunResult.SourceRunResult.success(
                            CollectSource.GOV24_DETAIL,
                            aggregate(candidates.stream()
                                    .map(collectSourceExecutionService::collectGov24DetailsForSourceId)
                                    .toList())
                    ),
                    CollectBatchRunResult.SourceRunResult.success(
                            CollectSource.GOV24_SUPPORT_CONDITIONS,
                            aggregate(candidates.stream()
                                    .map(collectSourceExecutionService::collectGov24SupportConditionsForSourceId)
                                    .toList())
                    )
            );
            case GOV24_DETAIL, GOV24_SUPPORT_CONDITIONS, BOKJIRO_DETAIL, BOKJIRO_DETAIL_GAP_FILL, BOKJIRO_DETAIL_REFRESH ->
                    List.of();
        };
    }

    private List<CollectBatchRunResult.SourceRunResult> runRotationDetailSafely() {
        if (!rotationEnabled) {
            return List.of();
        }
        DayOfWeek dayOfWeek = LocalDate.now(ZoneId.of(SCHEDULE_ZONE)).getDayOfWeek();
        try {
            return switch (dayOfWeek) {
                case MONDAY -> List.of(CollectBatchRunResult.SourceRunResult.success(
                        CollectSource.BOKJIRO_DETAIL,
                        collectSourceExecutionService.collectBokjiroDetails(bokjiroDetailRotationMaxCalls)
                ));
                case TUESDAY -> List.of(CollectBatchRunResult.SourceRunResult.success(
                        CollectSource.GOV24_DETAIL,
                        collectSourceExecutionService.collectGov24Details(gov24DetailRotationMaxCalls)
                ));
                case WEDNESDAY -> List.of(CollectBatchRunResult.SourceRunResult.success(
                        CollectSource.GOV24_SUPPORT_CONDITIONS,
                        collectSourceExecutionService.collectGov24SupportConditions(gov24SupportConditionsRotationMaxCalls)
                ));
                case THURSDAY -> List.of(CollectBatchRunResult.SourceRunResult.success(
                        CollectSource.BOKJIRO_DETAIL_REFRESH,
                        collectSourceExecutionService.collectBokjiroDetailsRefresh(bokjiroRefreshRotationMaxCalls)
                ));
                case FRIDAY -> List.of(CollectBatchRunResult.SourceRunResult.success(
                        CollectSource.YOUTH,
                        collectSourceExecutionService.collectYouthDetails()
                ));
                case SATURDAY, SUNDAY -> List.of();
            };
        } catch (Exception e) {
            CollectSource failureSource = rotationFailureSource(dayOfWeek);
            log.warn("[CollectBatchService][{}] rotation detail 실패 dayOfWeek={} errorType={}",
                    failureSource.jobName(), dayOfWeek, e.getClass().getSimpleName());
            return List.of(CollectBatchRunResult.SourceRunResult.failure(failureSource, e));
        }
    }

    private CollectResult aggregate(List<CollectResult> results) {
        int requested = 0;
        int saved = 0;
        int skipped = 0;
        int filtered = 0;
        int failed = 0;
        for (CollectResult result : results) {
            requested += result.requestedCount();
            saved += result.savedCount();
            skipped += result.skippedCount();
            filtered += result.filteredCount();
            failed += result.failedCount();
        }
        return CollectResult.of(requested, saved, skipped, filtered, failed);
    }

    private CollectSource forcedDetailResultSource(CollectSource listSource) {
        return switch (listSource) {
            case YOUTH -> CollectSource.YOUTH;
            case BOKJIRO_CENTRAL, BOKJIRO_LOCAL -> CollectSource.BOKJIRO_DETAIL_REFRESH;
            case GOV24 -> CollectSource.GOV24_DETAIL;
            case GOV24_DETAIL, GOV24_SUPPORT_CONDITIONS, BOKJIRO_DETAIL, BOKJIRO_DETAIL_GAP_FILL, BOKJIRO_DETAIL_REFRESH ->
                    listSource;
        };
    }

    private CollectSource rotationFailureSource(DayOfWeek dayOfWeek) {
        return switch (dayOfWeek) {
            case MONDAY -> CollectSource.BOKJIRO_DETAIL;
            case TUESDAY -> CollectSource.GOV24_DETAIL;
            case WEDNESDAY -> CollectSource.GOV24_SUPPORT_CONDITIONS;
            case THURSDAY -> CollectSource.BOKJIRO_DETAIL_REFRESH;
            case FRIDAY, SATURDAY, SUNDAY -> CollectSource.YOUTH;
        };
    }

    private record ForcedDetailPlan(
            CollectSource listSource,
            List<String> candidateSourceIds,
            String reason
    ) {
        private ForcedDetailPlan {
            candidateSourceIds = List.copyOf(candidateSourceIds);
        }
    }
}
