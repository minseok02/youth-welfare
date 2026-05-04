package com.example.welfare.collect.repository;

import com.example.welfare.collect.entity.RawApiPayload;
import com.example.welfare.policy.entity.WelfareService;

import java.time.LocalDateTime;

public interface RawApiPayloadCommandRepository {

    RawApiPayload save(RawApiPayload rawApiPayload);

    RawApiPayload upsert(
            WelfareService.SourceType sourceType,
            String sourceId,
            RawApiPayload.ApiCategory apiCategory,
            String payloadJson,
            String payloadHash,
            LocalDateTime fetchedAt
    );
}
