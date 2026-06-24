package com.example.welfare.policy.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record RecommendationClickRequest(
        @NotNull
        @Min(1)
        Long logId
) {
}
