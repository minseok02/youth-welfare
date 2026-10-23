package com.example.welfare.collect.service;

import com.example.welfare.collect.normalization.NormalizedPolicySidecarBackfillService;
import com.example.welfare.collect.normalization.NormalizedPolicySidecarBackfillService.BackfillResult;
import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class CollectSidecarBackfillApplicationService {

    private final CollectExecutionGuard collectExecutionGuard;
    private final NormalizedPolicySidecarBackfillService backfillService;

    public BackfillResult backfillBokjiro(String scope, int limitPerSource) {
        Supplier<BackfillResult> task = switch (scope) {
            case "all" -> () -> backfillService.backfillBokjiroListSidecars(limitPerSource)
                    .plus(backfillService.backfillBokjiroDetailSidecars(limitPerSource));
            case "list" -> () -> backfillService.backfillBokjiroListSidecars(limitPerSource);
            case "detail" -> () -> backfillService.backfillBokjiroDetailSidecars(limitPerSource);
            default -> throw new CustomException(ErrorCode.INVALID_INPUT);
        };
        return runExclusive("collect-bokjiro-sidecars-backfill", task);
    }

    public BackfillResult backfillGov24(String scope, int limitPerSource, boolean missingOnly) {
        Supplier<BackfillResult> task;
        if (missingOnly) {
            if (!"list".equals(scope)) {
                throw new CustomException(ErrorCode.INVALID_INPUT);
            }
            task = () -> backfillService.backfillGov24MissingListSidecars(limitPerSource);
        } else {
            task = switch (scope) {
                case "list" -> () -> backfillService.backfillGov24ListSidecars(limitPerSource);
                case "regions" -> () -> backfillService.backfillGov24ListRegions(limitPerSource);
                case "support", "support-conditions" ->
                        () -> backfillService.backfillGov24SupportConditionSidecars(limitPerSource);
                case "all" -> () -> backfillService.backfillGov24ListSidecars(limitPerSource)
                        .plus(backfillService.backfillGov24ListRegions(limitPerSource))
                        .plus(backfillService.backfillGov24SupportConditionSidecars(limitPerSource));
                default -> throw new CustomException(ErrorCode.INVALID_INPUT);
            };
        }
        return runExclusive("collect-gov24-sidecars-backfill", task);
    }

    // Keep this orchestration non-transactional: each downstream proxy must finish
    // its commit/rollback before the guard releases the global lock in finally.
    private BackfillResult runExclusive(String jobName, Supplier<BackfillResult> task) {
        AtomicReference<BackfillResult> result = new AtomicReference<>();
        collectExecutionGuard.runExclusive(jobName, () -> result.set(task.get()));
        return result.get();
    }
}
