package com.example.welfare.policy.repository;

import com.example.welfare.policy.entity.ServiceViewLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface ServiceViewLogRepository extends JpaRepository<ServiceViewLog, Long> {

    boolean existsByServiceIdAndUserKeyAndViewedAtAfter(Long serviceId, String userKey, LocalDateTime cutoff);

    boolean existsByServiceIdAndClientFingerprintAndViewedAtAfter(
            Long serviceId, String clientFingerprint, LocalDateTime cutoff
    );

    long countByServiceIdAndUserKey(Long serviceId, String userKey);

    @Modifying
    @Transactional
    void deleteByUserKeyIn(Collection<String> userKeys);

    @Query("""
            SELECT svl.service.id as serviceId,
                   COUNT(DISTINCT CASE
                        WHEN svl.userKey IS NOT NULL THEN CONCAT('K:', svl.userKey)
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

    @Query("""
            SELECT svl
            FROM ServiceViewLog svl
            JOIN FETCH svl.service
            WHERE svl.userKey = :userKey
              AND svl.id = (
                    SELECT MAX(svl2.id)
                    FROM ServiceViewLog svl2
                    WHERE svl2.userKey = :userKey
                      AND svl2.service.id = svl.service.id
                      AND svl2.viewedAt = (
                            SELECT MAX(svl3.viewedAt)
                            FROM ServiceViewLog svl3
                            WHERE svl3.userKey = :userKey
                              AND svl3.service.id = svl.service.id
                      )
              )
            ORDER BY svl.viewedAt DESC, svl.id DESC
            """)
    List<ServiceViewLog> findLatestViewedServicesByUserKey(@Param("userKey") String userKey, Pageable pageable);

    interface ServiceUniqueViewCount {
        Long getServiceId();
        Long getUniqueViewCount();
    }
}
