package com.example.welfare.admin.dashboard.dto;

import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record AdminPolicyFieldCorrectionRequest(
        Long policyId,
        Long reportId,
        String correctionType,
        LocalDate applyStartDate,
        LocalDate applyEndDate,
        @Size(max = 1000, message = "detailUrl은 1000자 이하여야 합니다.")
        String detailUrl,
        @Size(max = 4000, message = "eligibilityText는 4000자 이하여야 합니다.")
        String eligibilityText,
        Long duplicateOfPolicyId,
        @Size(max = 1000, message = "correctionNote는 1000자 이하여야 합니다.")
        String correctionNote
) {
}
