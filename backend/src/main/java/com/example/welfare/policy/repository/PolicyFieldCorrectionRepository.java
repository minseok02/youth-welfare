package com.example.welfare.policy.repository;

import com.example.welfare.policy.entity.PolicyFieldCorrection;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PolicyFieldCorrectionRepository extends JpaRepository<PolicyFieldCorrection, Long> {

    List<PolicyFieldCorrection> findAllByOrderByUpdatedAtDesc(Pageable pageable);
}
