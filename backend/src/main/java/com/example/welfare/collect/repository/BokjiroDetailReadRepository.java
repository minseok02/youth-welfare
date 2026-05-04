package com.example.welfare.collect.repository;

import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.entity.WelfareServiceDetail;

import java.util.List;
import java.util.Optional;

public interface BokjiroDetailReadRepository {

    List<WelfareService> findTargetsBySourceType(WelfareService.SourceType sourceType);

    boolean existsDetailByServiceId(Long serviceId);

    Optional<WelfareServiceDetail> findDetailByServiceId(Long serviceId);
}
