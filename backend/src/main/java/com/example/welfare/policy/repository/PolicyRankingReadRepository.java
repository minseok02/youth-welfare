package com.example.welfare.policy.repository;

import com.example.welfare.policy.entity.WelfareService;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface PolicyRankingReadRepository {

    List<RankableServiceSnapshot> findRankableSnapshots();

    List<ServiceUniqueViewCount> findUniqueViewCountsSinceForStatuses(List<WelfareService.ServiceStatus> statuses, LocalDateTime cutoff);

    List<WelfareService> findServicesByIds(Collection<Long> serviceIds);

    interface RankableServiceSnapshot {
        Long getId();
        WelfareService.SourceType getSourceType();
        Integer getViewCount();
        Long getApiViewCount();
        java.time.LocalDateTime getCreatedAt();
        java.time.LocalDateTime getRegisteredAt();
        java.time.LocalDateTime getLastModifiedAt();
    }

    interface ServiceUniqueViewCount {
        Long getServiceId();
        Long getUniqueViewCount();
    }
}
