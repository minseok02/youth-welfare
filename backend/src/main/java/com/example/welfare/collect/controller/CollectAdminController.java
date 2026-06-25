package com.example.welfare.collect.controller;

import com.example.welfare.admin.service.AdminOperationRateLimitService;
import com.example.welfare.collect.normalization.NormalizedPolicySidecarBackfillService;
import com.example.welfare.collect.dto.AsyncCollectStatusResponse;
import com.example.welfare.collect.service.CollectAdminService;
import com.example.welfare.collect.service.CollectAsyncJobService;
import com.example.welfare.collect.service.CollectBatchService;
import com.example.welfare.collect.service.CollectBatchRunResult;
import com.example.welfare.collect.service.CollectResult;
import com.example.welfare.collect.service.CollectSource;
import com.example.welfare.collect.dto.InvertedAgeBackfillResponse;
import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.example.welfare.global.auth.AuthenticatedUser;
import com.example.welfare.global.response.ApiResponse;
import com.example.welfare.policy.entity.WelfareService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;

/**
 * 수집 배치 수동 트리거 — 관리자 권한과 운영 rate limit으로 보호한다.
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/collect")
@RequiredArgsConstructor
@Validated
public class CollectAdminController {

    static final int MAX_CALLS_PER_RUN_LIMIT = 5_000;
    static final int MAX_LIMIT_PER_SOURCE = 1_000;
    static final int MAX_ROUNDS = 10;
    static final int MAX_CALLS_PER_ROUND = 1_000;

    private final CollectBatchService collectBatchService;
    private final CollectAdminService collectAdminService;
    private final CollectAsyncJobService collectAsyncJobService;
    private final NormalizedPolicySidecarBackfillService normalizedPolicySidecarBackfillService;
    private final AdminOperationRateLimitService adminOperationRateLimitService;

    @PostMapping("/all")
    public ResponseEntity<ApiResponse<CollectAllResponse>> collectAll(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser
    ) {
        adminOperationRateLimitService.checkMutationLimit(actorKey(authenticatedUser), "collect:all");
        log.info("[Admin] 전체 수집 수동 트리거");
        CollectBatchRunResult result = collectBatchService.collectAllNow();
        return ResponseEntity.ok(ApiResponse.success(CollectAllResponse.from(result)));
    }

    @PostMapping("/{sourceKey}")
    public ResponseEntity<ApiResponse<String>> collectSource(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable
            @Size(max = 40, message = "sourceKey는 40자 이하여야 합니다.")
            @Pattern(regexp = "^[a-z0-9-]+$", message = "sourceKey 형식이 올바르지 않습니다.")
            String sourceKey,
            @RequestParam(required = false)
            @Min(value = 1, message = "maxCallsPerRun은 1 이상이어야 합니다.")
            @Max(value = MAX_CALLS_PER_RUN_LIMIT, message = "maxCallsPerRun은 5000 이하여야 합니다.")
            Integer maxCallsPerRun,
            @RequestParam(required = false)
            @Size(max = 100, message = "sourceId는 100자 이하여야 합니다.")
            @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "sourceId 형식이 올바르지 않습니다.")
            String sourceId) {
        adminOperationRateLimitService.checkMutationLimit(actorKey(authenticatedUser), "collect:source");
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

    @PostMapping("/{sourceKey}/async")
    public ResponseEntity<ApiResponse<AsyncCollectStatusResponse>> collectSourceAsync(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable
            @Size(max = 40, message = "sourceKey는 40자 이하여야 합니다.")
            @Pattern(regexp = "^[a-z0-9-]+$", message = "sourceKey 형식이 올바르지 않습니다.")
            String sourceKey) {
        adminOperationRateLimitService.checkMutationLimit(actorKey(authenticatedUser), "collect:source-async");
        CollectSource source = CollectSource.fromPathKey(sourceKey);
        log.info("[Admin] {} 비동기 수집 수동 트리거", source.triggerLabel());
        return ResponseEntity.accepted().body(ApiResponse.success(collectAsyncJobService.trigger(source)));
    }

    @GetMapping("/{sourceKey}/async-status")
    public ResponseEntity<ApiResponse<AsyncCollectStatusResponse>> collectSourceAsyncStatus(
            @PathVariable
            @Size(max = 40, message = "sourceKey는 40자 이하여야 합니다.")
            @Pattern(regexp = "^[a-z0-9-]+$", message = "sourceKey 형식이 올바르지 않습니다.")
            String sourceKey) {
        CollectSource source = CollectSource.fromPathKey(sourceKey);
        return ResponseEntity.ok(ApiResponse.success(collectAsyncJobService.getStatus(source)));
    }

    @PostMapping("/youth-details")
    public ResponseEntity<ApiResponse<String>> collectYouthDetails(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser
    ) {
        adminOperationRateLimitService.checkMutationLimit(actorKey(authenticatedUser), "collect:youth-details");
        log.info("[Admin] 온통청년 DETAIL 수집 수동 트리거");
        CollectResult result = collectAdminService.collectYouthDetails();
        return ResponseEntity.ok(ApiResponse.success(
                "온통청년 DETAIL 수집 완료 requested=%d saved=%d skipped=%d failed=%d"
                        .formatted(result.requestedCount(), result.savedCount(), result.skippedCount(), result.failedCount())
        ));
    }

    @PostMapping("/inverted-age-backfill")
    public ResponseEntity<ApiResponse<InvertedAgeBackfillResponse>> backfillInvertedAges(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @RequestParam(required = false)
            @Size(max = 10, message = "sourceType은 10개 이하여야 합니다.")
            List<WelfareService.SourceType> sourceType,
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "limitPerSource는 0 이상이어야 합니다.")
            @Max(value = MAX_LIMIT_PER_SOURCE, message = "limitPerSource는 1000 이하여야 합니다.")
            int limitPerSource
    ) {
        adminOperationRateLimitService.checkMutationLimit(actorKey(authenticatedUser), "collect:inverted-age-backfill");
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
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @RequestParam(defaultValue = "all")
            @Size(max = 20, message = "scope는 20자 이하여야 합니다.")
            @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "scope 형식이 올바르지 않습니다.")
            String scope,
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "limitPerSource는 0 이상이어야 합니다.")
            @Max(value = MAX_LIMIT_PER_SOURCE, message = "limitPerSource는 1000 이하여야 합니다.")
            int limitPerSource
    ) {
        adminOperationRateLimitService.checkMutationLimit(actorKey(authenticatedUser), "collect:bokjiro-sidecars-backfill");
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
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @RequestParam(defaultValue = "list")
            @Size(max = 20, message = "scope는 20자 이하여야 합니다.")
            @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "scope 형식이 올바르지 않습니다.")
            String scope,
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "limitPerSource는 0 이상이어야 합니다.")
            @Max(value = MAX_LIMIT_PER_SOURCE, message = "limitPerSource는 1000 이하여야 합니다.")
            int limitPerSource,
            @RequestParam(defaultValue = "false") boolean missingOnly
    ) {
        adminOperationRateLimitService.checkMutationLimit(actorKey(authenticatedUser), "collect:gov24-sidecars-backfill");
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
                case "regions" -> normalizedPolicySidecarBackfillService.backfillGov24ListRegions(effectiveLimitPerSource);
                case "support", "support-conditions" ->
                        normalizedPolicySidecarBackfillService.backfillGov24SupportConditionSidecars(effectiveLimitPerSource);
                case "all" -> normalizedPolicySidecarBackfillService.backfillGov24ListSidecars(effectiveLimitPerSource)
                        .plus(normalizedPolicySidecarBackfillService.backfillGov24ListRegions(effectiveLimitPerSource))
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
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @RequestParam(defaultValue = "1")
            @Min(value = 1, message = "rounds는 1 이상이어야 합니다.")
            @Max(value = MAX_ROUNDS, message = "rounds는 10 이하여야 합니다.")
            int rounds,
            @RequestParam(defaultValue = "1000")
            @Min(value = 1, message = "maxCallsPerRound는 1 이상이어야 합니다.")
            @Max(value = MAX_CALLS_PER_ROUND, message = "maxCallsPerRound는 1000 이하여야 합니다.")
            int maxCallsPerRound
    ) {
        adminOperationRateLimitService.checkMutationLimit(actorKey(authenticatedUser), "collect:bokjiro-details-gap-fill");
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

    private String actorKey(AuthenticatedUser authenticatedUser) {
        return authenticatedUser != null ? authenticatedUser.userKey() : "unknown";
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
