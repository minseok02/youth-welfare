package com.example.welfare.policy.dto;

import com.example.welfare.policy.entity.PolicyErrorReport;

import java.time.LocalDateTime;

public record PolicyErrorReportResponse(
        Long reportId,
        Long policyId,
        String policyTitle,
        String reasonCode,
        String reasonLabel,
        String note,
        LocalDateTime createdAt
) {
    public static PolicyErrorReportResponse from(PolicyErrorReport report) {
        return new PolicyErrorReportResponse(
                report.getId(),
                report.getPolicy().getId(),
                report.getPolicy().getTitle(),
                report.getReasonCode().name(),
                report.getReasonCode().getLabel(),
                report.getNote(),
                report.getCreatedAt()
        );
    }
}
