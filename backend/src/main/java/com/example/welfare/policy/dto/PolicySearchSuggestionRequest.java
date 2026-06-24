package com.example.welfare.policy.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PolicySearchSuggestionRequest(
        @NotBlank(message = "keyword는 필수입니다.")
        @Size(max = 100, message = "keyword는 100자 이하여야 합니다.")
        String keyword,
        @Min(value = 1, message = "limit는 1 이상이어야 합니다.")
        @Max(value = 10, message = "limit는 10 이하여야 합니다.")
        Integer limit
) {
}
