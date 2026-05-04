package com.example.welfare.collect.repository;

import com.example.welfare.collect.entity.RawApiPayload;
import com.example.welfare.policy.entity.WelfareService;

public record NormalizedPolicySidecarBackfillTarget(
        RawApiPayload rawApiPayload,
        WelfareService matchedService
) {
}
