package com.example.welfare.admin.dashboard.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record AdminRecommendationCandidateDiagnosticRequest(
        @NotBlank(message = "userKey는 필수입니다.")
        @Size(max = 32, message = "userKey는 32자 이하여야 합니다.")
        @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "userKey 형식이 올바르지 않습니다.")
        String userKey,
        @Size(min = 1, max = 20, message = "serviceIds는 1개 이상 20개 이하여야 합니다.")
        List<@Min(value = 1, message = "serviceId는 1 이상이어야 합니다.") Long> serviceIds
) {
}
