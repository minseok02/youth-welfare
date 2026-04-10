package com.example.welfare.policy.repository;

import com.example.welfare.policy.entity.ServiceRegion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ServiceRegionRepository extends JpaRepository<ServiceRegion, Long> {

    List<ServiceRegion> findByServiceId(Long serviceId);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional
    void deleteByServiceId(Long serviceId);
}
