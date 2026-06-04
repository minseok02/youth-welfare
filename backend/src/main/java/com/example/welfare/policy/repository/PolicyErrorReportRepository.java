package com.example.welfare.policy.repository;

import com.example.welfare.policy.entity.PolicyErrorReport;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface PolicyErrorReportRepository extends JpaRepository<PolicyErrorReport, Long> {

    long countByStatus(PolicyErrorReport.Status status);

    long countByStatusAndCreatedAtAfter(PolicyErrorReport.Status status, LocalDateTime createdAt);

    List<PolicyErrorReport> findByStatusOrderByCreatedAtDesc(PolicyErrorReport.Status status, Pageable pageable);

    List<PolicyErrorReport> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
