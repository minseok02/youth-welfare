package com.example.welfare.admin.dashboard.dto;

import java.time.LocalDateTime;

public record AdminRecommendationReviewGatePromotionApprovalRecordResponse(
        String approvalKey,
        String approvalStatus,
        String approvalScope,
        String approvalNote,
        String approvedByUserKey,
        LocalDateTime approvedAt,
        boolean recorded
) {
}
