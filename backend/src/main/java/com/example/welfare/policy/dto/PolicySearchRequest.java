package com.example.welfare.policy.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PolicySearchRequest(
        @NotBlank(message = "keyword는 필수입니다.")
        @Size(max = 100, message = "keyword는 100자 이하여야 합니다.")
        String keyword,
        @Size(max = 20, message = "status는 20자 이하여야 합니다.")
        String status,
        @Size(max = 20, message = "statusFilter는 20자 이하여야 합니다.")
        String statusFilter,
        @Size(max = 100, message = "category는 100자 이하여야 합니다.")
        String category,
        @Size(max = 40, message = "sourceType은 40자 이하여야 합니다.")
        String sourceType,
        Boolean onlineApply,
        @Size(max = 100, message = "sido는 100자 이하여야 합니다.")
        String sido,
        @Size(max = 100, message = "sgg는 100자 이하여야 합니다.")
        String sgg,
        @Size(max = 20, message = "sort는 20자 이하여야 합니다.")
        String sort,
        @Min(value = 1, message = "incomeLevel은 1 이상이어야 합니다.")
        @Max(value = 10, message = "incomeLevel은 10 이하여야 합니다.")
        Integer incomeLevel,
        @Size(max = 100, message = "targetGroup은 100자 이하여야 합니다.")
        String targetGroup,
        @Size(max = 100, message = "gov24ServiceField는 100자 이하여야 합니다.")
        String gov24ServiceField,
        @Size(max = 100, message = "gov24UserType은 100자 이하여야 합니다.")
        String gov24UserType,
        @Size(max = 100, message = "gov24BenefitType은 100자 이하여야 합니다.")
        String gov24BenefitType,
        @Min(value = 0, message = "page는 0 이상이어야 합니다.")
        @Max(value = 1000, message = "page는 1000 이하여야 합니다.")
        Integer page,
        @Min(value = 1, message = "size는 1 이상이어야 합니다.")
        @Max(value = 100, message = "size는 100 이하여야 합니다.")
        Integer size
) {
}
