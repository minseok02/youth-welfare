package com.example.welfare.collect.repository;

import com.example.welfare.collect.entity.RawApiPayload;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class RawApiPayloadCommandRepositoryImpl implements RawApiPayloadCommandRepository {

    private final RawApiPayloadRepository rawApiPayloadRepository;

    @Override
    public RawApiPayload save(RawApiPayload rawApiPayload) {
        return rawApiPayloadRepository.save(rawApiPayload);
    }
}
