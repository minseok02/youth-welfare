package com.example.welfare.collect.service;

import com.example.welfare.global.exception.CustomException;

import java.util.List;

public record CollectBatchRunResult(
        List<SourceRunResult> sourceResults
) {

    public CollectBatchRunResult {
        sourceResults = List.copyOf(sourceResults);
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
        return requestedSourceCount() - succeededSourceCount();
    }

    public boolean completedWithFailures() {
        return failedSourceCount() > 0;
    }

    public record SourceRunResult(
            CollectSource source,
            boolean success,
            CollectResult result,
            String errorCode,
            String errorMessage
    ) {
        public static SourceRunResult success(CollectSource source, CollectResult result) {
            return new SourceRunResult(source, true, result, null, null);
        }

        public static SourceRunResult failure(CollectSource source, Exception exception) {
            String errorCode = exception instanceof CustomException customException
                    ? customException.getErrorCode().getCode()
                    : exception.getClass().getSimpleName();
            return new SourceRunResult(
                    source,
                    false,
                    CollectResult.of(0, 0, 0, 0, 0),
                    errorCode,
                    "collect source failed; see application logs with errorCode=" + errorCode
            );
        }
    }
}
