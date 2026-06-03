package com.example.welfare.policy.repository;

import com.example.welfare.policy.entity.ServiceRegion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ServiceRegionRepository extends JpaRepository<ServiceRegion, Long> {

    List<ServiceRegion> findByServiceId(Long serviceId);

    // 목록/상세 API 대표 지역 표시용: service_id당 하나의 region label 반환 — Object[]{serviceId, regionLabel}
    @Query(value = """
            SELECT service_id,
                   MIN(CASE
                         WHEN sgg_name IS NOT NULL AND TRIM(sgg_name) <> '' THEN CONCAT(sido_name, ' ', sgg_name)
                         ELSE sido_name
                       END)
            FROM service_regions
            WHERE service_id IN (:serviceIds)
              AND sido_name IS NOT NULL
            GROUP BY service_id
            """, nativeQuery = true)
    List<Object[]> findFirstRegionLabelByServiceIds(@Param("serviceIds") List<Long> serviceIds);

    @Modifying(flushAutomatically = false, clearAutomatically = false)
    @Query(value = "DELETE FROM service_regions WHERE service_id = :serviceId", nativeQuery = true)
    void deleteByServiceId(@Param("serviceId") Long serviceId);
}
