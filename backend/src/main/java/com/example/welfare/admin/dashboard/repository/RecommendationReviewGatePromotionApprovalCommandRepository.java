package com.example.welfare.admin.dashboard.repository;

import java.time.LocalDateTime;

public interface RecommendationReviewGatePromotionApprovalCommandRepository {

    void upsertApprovalRecord(
            String approvalKey,
            String approvalStatus,
            String approvalScope,
            String approvalNote,
            String approvedByUserKey,
            LocalDateTime approvedAt,
            LocalDateTime updatedAt
    );

    void deleteApprovalRecord(String approvalKey);
}
