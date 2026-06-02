package com.example.welfare.collect.repository;

public record NormalizedPolicySidecarBackfillRegionTarget(
        Long serviceId,
        String sourceId,
        String payloadJson
) {
}
