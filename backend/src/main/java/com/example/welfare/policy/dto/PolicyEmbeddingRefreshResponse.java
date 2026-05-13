package com.example.welfare.policy.dto;

public record PolicyEmbeddingRefreshResponse(
        String scope,
        int requestedServiceCount,
        int scannedChunkCount,
        int refreshedChunkCount
) {
}
