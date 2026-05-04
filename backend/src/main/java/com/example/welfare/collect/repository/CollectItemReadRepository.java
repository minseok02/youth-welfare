package com.example.welfare.collect.repository;

import com.example.welfare.policy.entity.WelfareService;

import java.util.Optional;

public interface CollectItemReadRepository {

    Optional<WelfareService> findServiceBySourceTypeAndSourceId(WelfareService.SourceType sourceType, String sourceId);
}
