package com.example.welfare.admin.dashboard.dto;

import java.time.LocalDateTime;

public record AdminReviewActionResponse(
        Long id,
        String status,
        String reviewNote,
        String reviewedByUserKey,
        LocalDateTime reviewedAt
) {
}
