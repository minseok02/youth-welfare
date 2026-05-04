package com.example.welfare.collect.repository;

import com.example.welfare.collect.entity.RawApiPayload;

public interface RawApiPayloadCommandRepository {

    RawApiPayload save(RawApiPayload rawApiPayload);
}
