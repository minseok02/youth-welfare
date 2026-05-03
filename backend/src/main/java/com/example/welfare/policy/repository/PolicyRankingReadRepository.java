package com.example.welfare.policy.repository;

import com.example.welfare.policy.entity.WelfareService;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface PolicyRankingReadRepository {

    List<WelfareService> findRankableServices();

    List<ServiceUniqueViewCount> findUniqueViewCountsSince(Collection<Long> serviceIds, LocalDateTime cutoff);

    interface ServiceUniqueViewCount {
        Long getServiceId();
        Long getUniqueViewCount();
    }
}
