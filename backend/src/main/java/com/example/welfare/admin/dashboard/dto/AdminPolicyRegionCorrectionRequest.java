package com.example.welfare.admin.dashboard.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record AdminPolicyRegionCorrectionRequest(
        @NotNull(message = "policyId는 필수입니다.")
        @Min(value = 1, message = "policyId는 1 이상이어야 합니다.")
        Long policyId,
        Boolean nationwide,
        @Size(max = 200, message = "regionCodes는 200개 이하여야 합니다.")
        List<@Size(min = 5, max = 5, message = "regionCode는 5자리여야 합니다.") String> regionCodes,
        @Min(value = 1, message = "reportId는 1 이상이어야 합니다.")
        Long reportId,
        @Size(max = 1000, message = "correctionNote는 1000자 이하여야 합니다.")
        String correctionNote
) {
}
