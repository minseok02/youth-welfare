package com.example.welfare.collect.repository;

import com.example.welfare.collect.entity.RawApiPayload;
import com.example.welfare.policy.entity.WelfareService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
@RequiredArgsConstructor
public class RawApiPayloadCommandRepositoryImpl implements RawApiPayloadCommandRepository {

    private final RawApiPayloadRepository rawApiPayloadRepository;
    private final RawApiPayloadReadRepository rawApiPayloadReadRepository;

    @Override
    public RawApiPayload save(RawApiPayload rawApiPayload) {
        return rawApiPayloadRepository.save(rawApiPayload);
    }

    @Override
    public RawApiPayload upsert(WelfareService.SourceType sourceType,
                                String sourceId,
                                RawApiPayload.ApiCategory apiCategory,
                                String payloadJson,
                                String payloadHash,
                                LocalDateTime fetchedAt) {
        RawApiPayload entity = rawApiPayloadReadRepository
                .findBySourceTypeAndSourceIdAndApiCategory(sourceType, sourceId, apiCategory)
                .orElseGet(() -> RawApiPayload.builder()
                        .sourceType(sourceType)
                        .sourceId(sourceId)
                        .apiCategory(apiCategory)
                        .payloadJson(payloadJson)
                        .payloadHash(payloadHash)
                        .fetchedAt(fetchedAt)
                        .build());
        entity.updatePayload(payloadJson, payloadHash, fetchedAt);
        return rawApiPayloadRepository.save(entity);
    }
}
