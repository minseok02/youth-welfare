package com.example.welfare.policy.repository;

import com.example.welfare.policy.entity.ServiceViewLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface ServiceViewLogRepository extends JpaRepository<ServiceViewLog, Long> {

    boolean existsByServiceIdAndUserIdAndViewedAtAfter(Long serviceId, Long userId, LocalDateTime cutoff);

    boolean existsByServiceIdAndClientFingerprintAndViewedAtAfter(
            Long serviceId, String clientFingerprint, LocalDateTime cutoff
    );

    @Query("""
            SELECT svl.service.id as serviceId,
                   COUNT(DISTINCT CASE
                        WHEN svl.userId IS NOT NULL THEN CONCAT('U:', CAST(svl.userId as string))
                        ELSE CONCAT('F:', svl.clientFingerprint)
                   END) as uniqueViewCount
            FROM ServiceViewLog svl
            WHERE svl.viewedAt >= :cutoff
              AND svl.service.id IN :serviceIds
            GROUP BY svl.service.id
            """)
    List<ServiceUniqueViewCount> findUniqueViewCountsSince(
            @Param("serviceIds") Collection<Long> serviceIds,
            @Param("cutoff") LocalDateTime cutoff
    );

    interface ServiceUniqueViewCount {
        Long getServiceId();
        Long getUniqueViewCount();
    }
}
