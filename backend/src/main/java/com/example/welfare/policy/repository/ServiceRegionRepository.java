package com.example.welfare.policy.repository;

import com.example.welfare.policy.entity.ServiceRegion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ServiceRegionRepository extends JpaRepository<ServiceRegion, Long> {

    List<ServiceRegion> findByServiceId(Long serviceId);

    // 목록 API 카드의 source 필드 표시용: hostOrg가 없는 복지로 지자체 정책에 sido를 표시하기 위해
    // sido_name이 있는 행만 대상으로, service_id당 하나(MIN)만 반환 — Object[]{serviceId, sidoName}
    @Query(value = "SELECT service_id, MIN(sido_name) FROM service_regions WHERE service_id IN (:serviceIds) AND sido_name IS NOT NULL GROUP BY service_id", nativeQuery = true)
    List<Object[]> findFirstSidoByServiceIds(@Param("serviceIds") List<Long> serviceIds);

    @Modifying(flushAutomatically = false, clearAutomatically = false)
    @Query(value = "DELETE FROM service_regions WHERE service_id = :serviceId", nativeQuery = true)
    void deleteByServiceId(@Param("serviceId") Long serviceId);
}
