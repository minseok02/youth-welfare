package com.example.welfare.collect.service;

import com.example.welfare.global.exception.CustomException;

import java.util.List;

public record CollectBatchRunResult(
        List<SourceRunResult> sourceResults,
        List<ListDiffRunResult> listDiffResults
) {

    public CollectBatchRunResult {
        sourceResults = List.copyOf(sourceResults);
        listDiffResults = List.copyOf(listDiffResults);
    }

    public CollectBatchRunResult(List<SourceRunResult> sourceResults) {
        this(sourceResults, List.of());
    }

    public int requestedSourceCount() {
        return sourceResults.size();
    }

    public int succeededSourceCount() {
        return (int) sourceResults.stream()
                .filter(SourceRunResult::success)
                .count();
    }

    public int failedSourceCount() {
        return (int) sourceResults.stream()
                .filter(result -> result.status() == Status.FAILED)
                .count();
    }

    public int partialSourceCount() {
        return (int) sourceResults.stream()
                .filter(result -> result.status() == Status.PARTIAL_SUCCESS)
                .count();
    }

    public int failedListDiffCount() {
        return (int) listDiffResults.stream()
                .filter(result -> result.status() == Status.FAILED)
                .count();
    }

    public boolean completedWithFailures() {
        return failedSourceCount() > 0 || partialSourceCount() > 0 || failedListDiffCount() > 0;
    }

    public Status status() {
        boolean hasSuccessfulWork = succeededSourceCount() > 0 || partialSourceCount() > 0
                || listDiffResults.stream().anyMatch(result -> result.status() == Status.SUCCESS);
        if (completedWithFailures()) {
            return hasSuccessfulWork ? Status.PARTIAL_SUCCESS : Status.FAILED;
        }
        return hasSuccessfulWork ? Status.SUCCESS : Status.SKIPPED;
    }

    public enum Status {
        SUCCESS, PARTIAL_SUCCESS, FAILED, SKIPPED
    }

    public record SourceRunResult(
            CollectSource source,
            boolean success,
            CollectResult result,
            String errorCode,
            String errorMessage
    ) {
        public SourceRunResult {
            success = success && result.failedCount() == 0;
        }

        public Status status() {
            if (success) {
                return Status.SUCCESS;
            }
            if (result.failedCount() > 0
                    && (result.savedCount() > 0 || result.skippedCount() > 0 || result.filteredCount() > 0)) {
                return Status.PARTIAL_SUCCESS;
            }
            return Status.FAILED;
        }

        public static SourceRunResult completed(CollectSource source, CollectResult result) {
            return new SourceRunResult(source, true, result, null, null);
        }

        public static SourceRunResult failure(CollectSource source, Exception exception) {
            String errorCode = safeErrorCode(exception);
            return new SourceRunResult(
                    source,
                    false,
                    CollectResult.of(0, 0, 0, 0, 0),
                    errorCode,
                    "collect source failed; see application logs with errorCode=" + errorCode
            );
        }
    }

    public record ListDiffRunResult(
            CollectSource source,
            Status status,
            String reason,
            String errorCode,
            String errorMessage
    ) {
        public static ListDiffRunResult success(CollectSource source, String reason) {
            return new ListDiffRunResult(source, Status.SUCCESS, reason, null, null);
        }

        public static ListDiffRunResult skipped(CollectSource source, String reason) {
            return new ListDiffRunResult(source, Status.SKIPPED, reason, null, null);
        }

        public static ListDiffRunResult failure(CollectSource source, Exception exception) {
            String errorCode = safeErrorCode(exception);
            return new ListDiffRunResult(source, Status.FAILED, "LIST_DIFF_FAILED", errorCode,
                    "list diff failed; see application logs with errorCode=" + errorCode);
        }
    }

    private static String safeErrorCode(Exception exception) {
        return exception instanceof CustomException customException
                ? customException.getErrorCode().getCode()
                : exception.getClass().getSimpleName();
    }
}
