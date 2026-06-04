package com.example.welfare.admin.dashboard.dto;

public record AdminPolicyDuplicateGroupReviewRequest(
        String sourceType,
        String title,
        String hostOrgKey,
        String hostOrgLabel,
        String reviewNote
) {
}
