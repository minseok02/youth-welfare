package com.example.welfare.policy.repository;

import com.example.welfare.policy.entity.PolicyRegionCorrection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PolicyRegionCorrectionRepository extends JpaRepository<PolicyRegionCorrection, Long> {

    Optional<PolicyRegionCorrection> findByServiceIdAndActiveTrue(Long serviceId);

    Optional<PolicyRegionCorrection> findByServiceId(Long serviceId);

    List<PolicyRegionCorrection> findByActiveTrueOrderByUpdatedAtDesc(Pageable pageable);

    List<PolicyRegionCorrection> findAllByOrderByUpdatedAtDesc(Pageable pageable);

    boolean existsByServiceIdAndActiveTrue(Long serviceId);

    @Query("select correction.service.id from PolicyRegionCorrection correction " +
            "where correction.active = true and correction.service.id in :serviceIds")
    List<Long> findActiveServiceIds(@Param("serviceIds") Collection<Long> serviceIds);
}
