package com.example.welfare.policy.repository;

import com.example.welfare.policy.entity.ServiceRegion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ServiceRegionRepository extends JpaRepository<ServiceRegion, Long> {

    List<ServiceRegion> findByServiceId(Long serviceId);

    @Modifying(flushAutomatically = false, clearAutomatically = false)
    @Query(value = "DELETE FROM service_regions WHERE service_id = :serviceId", nativeQuery = true)
    void deleteByServiceId(@Param("serviceId") Long serviceId);
}
