package com.example.welfare.policy.repository;

import com.example.welfare.policy.entity.WelfareServiceDetail;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WelfareServiceDetailRepository extends JpaRepository<WelfareServiceDetail, Long> {

    Optional<WelfareServiceDetail> findByServiceId(Long serviceId);
}
