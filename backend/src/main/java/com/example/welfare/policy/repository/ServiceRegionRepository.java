package com.example.welfare.policy.repository;

import com.example.welfare.policy.entity.ServiceRegion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ServiceRegionRepository extends JpaRepository<ServiceRegion, Long> {

    List<ServiceRegion> findByServiceId(Long serviceId);

    // 목록/상세 API 지역 라벨 선택용: service_id당 region label 후보 반환 — Object[]{serviceId, regionLabel}
    @Query(value = """
            SELECT DISTINCT service_id,
                   CASE
                     WHEN sgg_name IS NOT NULL AND TRIM(sgg_name) <> '' THEN CONCAT(sido_name, ' ', sgg_name)
                     ELSE sido_name
                   END
            FROM service_regions
            WHERE service_id IN (:serviceIds)
              AND sido_name IS NOT NULL
            ORDER BY service_id, 2
            """, nativeQuery = true)
    List<Object[]> findRegionLabelsByServiceIds(@Param("serviceIds") List<Long> serviceIds);

    // 목록 API 지역 라벨 보정용: code-only row까지 포함한 후보 반환 — Object[]{serviceId, regionLabel, regionCode}
    @Query(value = """
            SELECT DISTINCT service_id,
                   CASE
                     WHEN sgg_name IS NOT NULL AND TRIM(sgg_name) <> '' THEN CONCAT(sido_name, ' ', sgg_name)
                     ELSE sido_name
                   END,
                   region_code
            FROM service_regions
            WHERE service_id IN (:serviceIds)
            ORDER BY service_id, 2 NULLS LAST, 3
            """, nativeQuery = true)
    List<Object[]> findRegionLabelCandidatesByServiceIds(@Param("serviceIds") List<Long> serviceIds);

    @Modifying(flushAutomatically = false, clearAutomatically = false)
    @Query(value = "DELETE FROM service_regions WHERE service_id = :serviceId", nativeQuery = true)
    void deleteByServiceId(@Param("serviceId") Long serviceId);
}
