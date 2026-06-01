package com.example.welfare.collect.controller;

import com.example.welfare.collect.normalization.NormalizedPolicySidecarBackfillService;
import com.example.welfare.collect.service.CollectAdminService;
import com.example.welfare.collect.service.CollectBatchService;
import com.example.welfare.collect.service.CollectBatchRunResult;
import com.example.welfare.collect.service.CollectResult;
import com.example.welfare.collect.service.CollectSource;
import com.example.welfare.collect.dto.InvertedAgeBackfillResponse;
import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.example.welfare.global.response.ApiResponse;
import com.example.welfare.policy.entity.WelfareService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;

/**
 * 수집 배치 수동 트리거 — 로컬/개발 환경 전용 (prod 프로파일에서 비활성화)
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/collect")
@RequiredArgsConstructor
public class CollectAdminController {

    static final int MAX_CALLS_PER_RUN_LIMIT = 5_000;
    static final int MAX_LIMIT_PER_SOURCE = 1_000;
    static final int MAX_ROUNDS = 10;
    static final int MAX_CALLS_PER_ROUND = 1_000;

    private final CollectBatchService collectBatchService;
    private final CollectAdminService collectAdminService;
    private final NormalizedPolicySidecarBackfillService normalizedPolicySidecarBackfillService;

    @PostMapping("/all")
    public ResponseEntity<ApiResponse<CollectAllResponse>> collectAll() {
        log.info("[Admin] 전체 수집 수동 트리거");
        CollectBatchRunResult result = collectBatchService.collectAllNow();
        return ResponseEntity.ok(ApiResponse.success(CollectAllResponse.from(result)));
    }

    @PostMapping("/{sourceKey}")
    public ResponseEntity<ApiResponse<String>> collectSource(@PathVariable String sourceKey,
                                                             @RequestParam(required = false) Integer maxCallsPerRun,
                                                             @RequestParam(required = false) String sourceId) {
        CollectSource source = CollectSource.fromPathKey(sourceKey);
        log.info("[Admin] {} 수집 수동 트리거", source.triggerLabel());
        if (sourceId != null && !sourceId.isBlank()) {
            CollectResult result = collectAdminService.collect(source, sourceId);
            return ResponseEntity.ok(ApiResponse.success(
                    "%s requested=%d saved=%d skipped=%d failed=%d"
                            .formatted(
                                    source.successMessage(),
                                    result.requestedCount(),
                                    result.savedCount(),
                                    result.skippedCount(),
                                    result.failedCount()
                            )
            ));
        }
        if (maxCallsPerRun != null) {
            if (maxCallsPerRun <= 0 || maxCallsPerRun > MAX_CALLS_PER_RUN_LIMIT) {
                throw new CustomException(ErrorCode.INVALID_INPUT);
            }
            CollectResult result = collectAdminService.collect(source, maxCallsPerRun);
            return ResponseEntity.ok(ApiResponse.success(
                    "%s requested=%d saved=%d skipped=%d failed=%d"
                            .formatted(
                                    source.successMessage(),
                                    result.requestedCount(),
                                    result.savedCount(),
                                    result.skippedCount(),
                                    result.failedCount()
                            )
            ));
        }

        collectAdminService.collect(source);
        return ResponseEntity.ok(ApiResponse.success(source.successMessage()));
    }

    @PostMapping("/youth-details")
    public ResponseEntity<ApiResponse<String>> collectYouthDetails() {
        log.info("[Admin] 온통청년 DETAIL 수집 수동 트리거");
        CollectResult result = collectAdminService.collectYouthDetails();
        return ResponseEntity.ok(ApiResponse.success(
                "온통청년 DETAIL 수집 완료 requested=%d saved=%d skipped=%d failed=%d"
                        .formatted(result.requestedCount(), result.savedCount(), result.skippedCount(), result.failedCount())
        ));
    }

    @PostMapping("/inverted-age-backfill")
    public ResponseEntity<ApiResponse<InvertedAgeBackfillResponse>> backfillInvertedAges(
            @RequestParam(required = false) List<WelfareService.SourceType> sourceType,
            @RequestParam(defaultValue = "0") int limitPerSource
    ) {
        int effectiveLimitPerSource = normalizeLimitPerSource(limitPerSource);
        InvertedAgeBackfillResponse response =
                collectAdminService.backfillInvertedAgeRanges(sourceType, effectiveLimitPerSource);
        log.info("[Admin] inverted age backfill 수동 트리거 scope={} sourceTypes={} limitPerSource={} scanned={} repaired={} missingRawPayload={} unrepaired={} failed={}",
                response.scope(),
                response.sourceTypes(),
                effectiveLimitPerSource,
                response.scannedCount(),
                response.repairedCount(),
                response.missingRawPayloadCount(),
                response.unrepairedCount(),
                response.failedCount());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/bokjiro-sidecars-backfill")
    public ResponseEntity<ApiResponse<SidecarBackfillResponse>> backfillBokjiroSidecars(
            @RequestParam(defaultValue = "all") String scope,
            @RequestParam(defaultValue = "0") int limitPerSource
    ) {
        int effectiveLimitPerSource = normalizeSidecarBackfillLimitPerSource(limitPerSource);
        String normalizedScope = scope.toLowerCase(Locale.ROOT);
        NormalizedPolicySidecarBackfillService.BackfillResult result = switch (normalizedScope) {
            case "all" -> normalizedPolicySidecarBackfillService.backfillBokjiroListSidecars(effectiveLimitPerSource)
                    .plus(normalizedPolicySidecarBackfillService.backfillBokjiroDetailSidecars(effectiveLimitPerSource));
            case "list" -> normalizedPolicySidecarBackfillService.backfillBokjiroListSidecars(effectiveLimitPerSource);
            case "detail" -> normalizedPolicySidecarBackfillService.backfillBokjiroDetailSidecars(effectiveLimitPerSource);
            default -> throw new CustomException(ErrorCode.INVALID_INPUT);
        };

        log.info("[Admin] 복지로 sidecar backfill 수동 트리거 scope={} limitPerSource={} scanned={} upserted={} missing={} failed={}",
                normalizedScope,
                effectiveLimitPerSource,
                result.scannedCount(),
                result.upsertedCount(),
                result.missingServiceCount(),
                result.failedCount());
        return ResponseEntity.ok(ApiResponse.success(new SidecarBackfillResponse(
                normalizedScope,
                effectiveLimitPerSource,
                result.scannedCount(),
                result.upsertedCount(),
                result.missingServiceCount(),
                result.failedCount()
        )));
    }

    @PostMapping("/gov24-sidecars-backfill")
    public ResponseEntity<ApiResponse<SidecarBackfillResponse>> backfillGov24Sidecars(
            @RequestParam(defaultValue = "list") String scope,
            @RequestParam(defaultValue = "0") int limitPerSource,
            @RequestParam(defaultValue = "false") boolean missingOnly
    ) {
        int effectiveLimitPerSource = normalizeSidecarBackfillLimitPerSource(limitPerSource);
        String normalizedScope = scope.toLowerCase(Locale.ROOT);
        NormalizedPolicySidecarBackfillService.BackfillResult result;
        String responseScope = "gov24-" + normalizedScope;
        if (missingOnly) {
            if (!"list".equals(normalizedScope)) {
                throw new CustomException(ErrorCode.INVALID_INPUT);
            }
            responseScope = "gov24-list-missing";
            result = normalizedPolicySidecarBackfillService.backfillGov24MissingListSidecars(effectiveLimitPerSource);
        } else {
            result = switch (normalizedScope) {
                case "list" -> normalizedPolicySidecarBackfillService.backfillGov24ListSidecars(effectiveLimitPerSource);
                case "support", "support-conditions" ->
                        normalizedPolicySidecarBackfillService.backfillGov24SupportConditionSidecars(effectiveLimitPerSource);
                case "all" -> normalizedPolicySidecarBackfillService.backfillGov24ListSidecars(effectiveLimitPerSource)
                        .plus(normalizedPolicySidecarBackfillService.backfillGov24SupportConditionSidecars(effectiveLimitPerSource));
                default -> throw new CustomException(ErrorCode.INVALID_INPUT);
            };
        }

        log.info("[Admin] Gov24 sidecar backfill 수동 트리거 scope={} missingOnly={} limitPerSource={} scanned={} upserted={} missing={} failed={}",
                normalizedScope,
                missingOnly,
                effectiveLimitPerSource,
                result.scannedCount(),
                result.upsertedCount(),
                result.missingServiceCount(),
                result.failedCount());
        return ResponseEntity.ok(ApiResponse.success(new SidecarBackfillResponse(
                responseScope,
                effectiveLimitPerSource,
                result.scannedCount(),
                result.upsertedCount(),
                result.missingServiceCount(),
                result.failedCount()
        )));
    }

    @PostMapping("/bokjiro-details-gap-fill")
    public ResponseEntity<ApiResponse<DetailGapFillResponse>> fillBokjiroDetailGaps(
            @RequestParam(defaultValue = "1") int rounds,
            @RequestParam(defaultValue = "1000") int maxCallsPerRound
    ) {
        if (rounds <= 0
                || rounds > MAX_ROUNDS
                || maxCallsPerRound <= 0
                || maxCallsPerRound > MAX_CALLS_PER_ROUND) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }

        GapFillResult result =
                GapFillResult.from(collectAdminService.collectBokjiroDetailGapFill(rounds, maxCallsPerRound));

        log.info("[Admin] 복지로 detail gap fill 수동 트리거 rounds={} maxCallsPerRound={} roundsExecuted={} requested={} saved={} skipped={} failed={} stoppedAfterNoSaves={}",
                rounds,
                maxCallsPerRound,
                result.roundsExecuted(),
                result.requestedCount(),
                result.savedCount(),
                result.skippedCount(),
                result.failedCount(),
                result.stoppedAfterNoSaves());

        return ResponseEntity.ok(ApiResponse.success(new DetailGapFillResponse(
                result.roundsRequested(),
                result.roundsExecuted(),
                result.maxCallsPerRound(),
                result.requestedCount(),
                result.savedCount(),
                result.skippedCount(),
                result.failedCount(),
                result.stoppedAfterNoSaves()
        )));
    }

    private int normalizeLimitPerSource(int limitPerSource) {
        if (limitPerSource < 0) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        if (limitPerSource == 0) {
            return MAX_LIMIT_PER_SOURCE;
        }
        if (limitPerSource > MAX_LIMIT_PER_SOURCE) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        return limitPerSource;
    }

    private int normalizeSidecarBackfillLimitPerSource(int limitPerSource) {
        if (limitPerSource < 0) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        if (limitPerSource > MAX_LIMIT_PER_SOURCE) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        return limitPerSource;
    }

    public record SidecarBackfillResponse(
            String scope,
            int limitPerSource,
            int scannedCount,
            int upsertedCount,
            int missingServiceCount,
            int failedCount
    ) {
    }

    public record DetailGapFillResponse(
            int roundsRequested,
            int roundsExecuted,
            int maxCallsPerRound,
            int requestedCount,
            int savedCount,
            int skippedCount,
            int failedCount,
            boolean stoppedAfterNoSaves
    ) {
    }

    public record CollectAllResponse(
            boolean completedWithFailures,
            int requestedSourceCount,
            int succeededSourceCount,
            int failedSourceCount,
            java.util.List<CollectSourceRunResponse> sourceResults
    ) {
        static CollectAllResponse from(CollectBatchRunResult result) {
            return new CollectAllResponse(
                    result.completedWithFailures(),
                    result.requestedSourceCount(),
                    result.succeededSourceCount(),
                    result.failedSourceCount(),
                    result.sourceResults().stream()
                            .map(CollectSourceRunResponse::from)
                            .toList()
            );
        }
    }

    public record CollectSourceRunResponse(
            String sourceKey,
            String jobName,
            String triggerLabel,
            boolean success,
            int requestedCount,
            int savedCount,
            int skippedCount,
            int filteredCount,
            int failedCount,
            String errorCode,
            String errorMessage
    ) {
        static CollectSourceRunResponse from(CollectBatchRunResult.SourceRunResult result) {
            return new CollectSourceRunResponse(
                    result.source().pathKey(),
                    result.source().jobName(),
                    result.source().triggerLabel(),
                    result.success(),
                    result.result().requestedCount(),
                    result.result().savedCount(),
                    result.result().skippedCount(),
                    result.result().filteredCount(),
                    result.result().failedCount(),
                    result.errorCode(),
                    result.errorMessage()
            );
        }
    }

    private record GapFillResult(
            int roundsRequested,
            int roundsExecuted,
            int maxCallsPerRound,
            int requestedCount,
            int savedCount,
            int skippedCount,
            int failedCount,
            boolean stoppedAfterNoSaves
    ) {
        private static GapFillResult from(com.example.welfare.collect.service.BokjiroDetailCollectService.GapFillResult result) {
            return new GapFillResult(
                    result.roundsRequested(),
                    result.roundsExecuted(),
                    result.maxCallsPerRound(),
                    result.requestedCount(),
                    result.savedCount(),
                    result.skippedCount(),
                    result.failedCount(),
                    result.stoppedAfterNoSaves()
            );
        }
    }
}
