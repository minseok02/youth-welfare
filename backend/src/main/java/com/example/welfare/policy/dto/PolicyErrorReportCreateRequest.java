package com.example.welfare.policy.dto;

import com.example.welfare.policy.entity.PolicyErrorReport;

public record PolicyErrorReportCreateRequest(
        PolicyErrorReport.ReasonCode reasonCode,
        String note
) {
}
