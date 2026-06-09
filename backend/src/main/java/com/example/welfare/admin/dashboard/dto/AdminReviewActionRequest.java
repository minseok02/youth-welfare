package com.example.welfare.admin.dashboard.dto;

import jakarta.validation.constraints.Size;

public record AdminReviewActionRequest(
        @Size(max = 1000, message = "reviewNote는 1000자 이하여야 합니다.")
        String reviewNote
) {
}
