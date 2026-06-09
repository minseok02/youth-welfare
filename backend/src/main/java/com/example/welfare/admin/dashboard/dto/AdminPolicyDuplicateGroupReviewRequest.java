package com.example.welfare.admin.dashboard.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AdminPolicyDuplicateGroupReviewRequest(
        @NotBlank(message = "sourceType은 필수입니다.")
        @Size(max = 40, message = "sourceType은 40자 이하여야 합니다.")
        @Pattern(regexp = "^[A-Z0-9_]+$", message = "sourceType 형식이 올바르지 않습니다.")
        String sourceType,
        @NotBlank(message = "title은 필수입니다.")
        @Size(max = 255, message = "title은 255자 이하여야 합니다.")
        String title,
        @Size(max = 255, message = "hostOrgKey는 255자 이하여야 합니다.")
        String hostOrgKey,
        @Size(max = 255, message = "hostOrgLabel은 255자 이하여야 합니다.")
        String hostOrgLabel,
        @Size(max = 1000, message = "reviewNote는 1000자 이하여야 합니다.")
        String reviewNote
) {
}
