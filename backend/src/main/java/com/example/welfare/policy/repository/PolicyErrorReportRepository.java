package com.example.welfare.policy.repository;

import com.example.welfare.policy.entity.PolicyErrorReport;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface PolicyErrorReportRepository extends JpaRepository<PolicyErrorReport, Long> {

    long countByStatus(PolicyErrorReport.Status status);

    long countByStatusAndCreatedAtAfter(PolicyErrorReport.Status status, LocalDateTime createdAt);

    List<PolicyErrorReport> findByStatusOrderByCreatedAtDesc(PolicyErrorReport.Status status, Pageable pageable);

    List<PolicyErrorReport> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @Query("""
            SELECT COUNT(report) > 0
              FROM PolicyErrorReport report
             WHERE report.policy.id = :policyId
               AND report.reasonCode = :reasonCode
               AND report.status = :status
               AND report.userKey = :userKey
            """)
    boolean existsOpenSystemRegionAuditReport(
            @Param("policyId") Long policyId,
            @Param("reasonCode") PolicyErrorReport.ReasonCode reasonCode,
            @Param("status") PolicyErrorReport.Status status,
            @Param("userKey") String userKey
    );
}
