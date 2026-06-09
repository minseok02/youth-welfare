package com.example.welfare.policy.dto;

import com.example.welfare.policy.entity.PolicyErrorReport;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PolicyErrorReportCreateRequest(
        @NotNull(message = "reasonCode는 필수입니다.")
        PolicyErrorReport.ReasonCode reasonCode,
        @Size(max = 1000, message = "note는 1000자 이하여야 합니다.")
        String note
) {
}
