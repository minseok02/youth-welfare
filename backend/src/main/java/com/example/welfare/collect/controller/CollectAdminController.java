package com.example.welfare.collect.controller;

import com.example.welfare.collect.normalization.NormalizedPolicySidecarBackfillService;
import com.example.welfare.collect.service.CollectSource;
import com.example.welfare.collect.service.CollectService;
import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.example.welfare.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;

/**
 * 수집 배치 수동 트리거 — 로컬/개발 환경 전용 (prod 프로파일에서 비활성화)
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/collect")
@RequiredArgsConstructor
public class CollectAdminController {

    private final CollectService collectService;
    private final NormalizedPolicySidecarBackfillService normalizedPolicySidecarBackfillService;

    @PostMapping("/all")
    public ResponseEntity<ApiResponse<String>> collectAll() {
        log.info("[Admin] 전체 수집 수동 트리거");
        collectService.collectAll();
        return ResponseEntity.ok(ApiResponse.success("수집 완료"));
    }

    @PostMapping("/{sourceKey}")
    public ResponseEntity<ApiResponse<String>> collectSource(@PathVariable String sourceKey) {
        CollectSource source = CollectSource.fromPathKey(sourceKey);
        log.info("[Admin] {} 수집 수동 트리거", source.triggerLabel());
        collectService.collect(source);
        return ResponseEntity.ok(ApiResponse.success(source.successMessage()));
    }

    @PostMapping("/bokjiro-sidecars-backfill")
    public ResponseEntity<ApiResponse<SidecarBackfillResponse>> backfillBokjiroSidecars(
            @RequestParam(defaultValue = "all") String scope,
            @RequestParam(defaultValue = "0") int limitPerSource
    ) {
        String normalizedScope = scope.toLowerCase(Locale.ROOT);
        NormalizedPolicySidecarBackfillService.BackfillResult result = switch (normalizedScope) {
            case "all" -> normalizedPolicySidecarBackfillService.backfillBokjiroListSidecars(limitPerSource)
                    .plus(normalizedPolicySidecarBackfillService.backfillBokjiroDetailSidecars(limitPerSource));
            case "list" -> normalizedPolicySidecarBackfillService.backfillBokjiroListSidecars(limitPerSource);
            case "detail" -> normalizedPolicySidecarBackfillService.backfillBokjiroDetailSidecars(limitPerSource);
            default -> throw new CustomException(ErrorCode.INVALID_INPUT);
        };

        log.info("[Admin] 복지로 sidecar backfill 수동 트리거 scope={} limitPerSource={} scanned={} upserted={} missing={} failed={}",
                normalizedScope,
                limitPerSource,
                result.scannedCount(),
                result.upsertedCount(),
                result.missingServiceCount(),
                result.failedCount());
        return ResponseEntity.ok(ApiResponse.success(new SidecarBackfillResponse(
                normalizedScope,
                limitPerSource,
                result.scannedCount(),
                result.upsertedCount(),
                result.missingServiceCount(),
                result.failedCount()
        )));
    }

    @PostMapping("/bokjiro-details-gap-fill")
    public ResponseEntity<ApiResponse<DetailGapFillResponse>> fillBokjiroDetailGaps(
            @RequestParam(defaultValue = "1") int rounds,
            @RequestParam(defaultValue = "190") int maxCallsPerRound
    ) {
        if (rounds <= 0 || maxCallsPerRound <= 0) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }

        GapFillResult result =
                GapFillResult.from(collectService.collectBokjiroDetailGapFill(rounds, maxCallsPerRound));

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
