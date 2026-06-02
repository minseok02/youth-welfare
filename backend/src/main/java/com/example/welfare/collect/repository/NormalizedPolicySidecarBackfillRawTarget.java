package com.example.welfare.collect.repository;

public record NormalizedPolicySidecarBackfillRawTarget(
        Long serviceId,
        String sourceId,
        String payloadJson
) {
}
