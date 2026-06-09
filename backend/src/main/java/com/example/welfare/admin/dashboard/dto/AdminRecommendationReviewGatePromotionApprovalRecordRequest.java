package com.example.welfare.admin.dashboard.dto;

import jakarta.validation.constraints.Size;

public record AdminRecommendationReviewGatePromotionApprovalRecordRequest(
        @Size(max = 1000, message = "approvalNote는 1000자 이하여야 합니다.")
        String approvalNote
) {
}
