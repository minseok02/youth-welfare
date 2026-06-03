package com.example.welfare.policy.repository;

import com.example.welfare.policy.entity.PolicyErrorReport;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PolicyErrorReportRepository extends JpaRepository<PolicyErrorReport, Long> {

    long countByStatus(PolicyErrorReport.Status status);

    List<PolicyErrorReport> findByStatusOrderByCreatedAtDesc(PolicyErrorReport.Status status, Pageable pageable);
}
