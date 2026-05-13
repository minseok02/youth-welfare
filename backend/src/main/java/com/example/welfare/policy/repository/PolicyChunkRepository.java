package com.example.welfare.policy.repository;

import com.example.welfare.policy.entity.PolicyChunk;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PolicyChunkRepository extends JpaRepository<PolicyChunk, Long> {

    boolean existsByServiceId(Long serviceId);

    List<PolicyChunk> findByServiceIdInOrderByServiceIdAscChunkOrderAsc(List<Long> serviceIds);

    List<PolicyChunk> findByServiceIdIn(List<Long> serviceIds);

    @Query("SELECT DISTINCT pc.service.id FROM PolicyChunk pc WHERE pc.service.id IN :serviceIds")
    List<Long> findExistingServiceIds(List<Long> serviceIds);
}
