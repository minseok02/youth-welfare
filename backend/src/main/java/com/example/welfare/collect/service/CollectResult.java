package com.example.welfare.collect.service;

public record CollectResult(
        int requestedCount,
        int savedCount,
        int skippedCount,
        int filteredCount,
        int failedCount,
        String metadataJson
) {

    public static CollectResult of(int requestedCount, int savedCount, int skippedCount, int filteredCount, int failedCount) {
        return new CollectResult(requestedCount, savedCount, skippedCount, filteredCount, failedCount, null);
    }

    public static CollectResult withMetadata(int requestedCount,
                                             int savedCount,
                                             int skippedCount,
                                             int filteredCount,
                                             int failedCount,
                                             String metadataJson) {
        return new CollectResult(requestedCount, savedCount, skippedCount, filteredCount, failedCount, metadataJson);
    }
}
