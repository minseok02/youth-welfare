package com.example.welfare.admin.dashboard.service;

import com.example.welfare.admin.dashboard.dto.AdminRecommendationReviewGatePromotionApprovalRecordClearResponse;
import com.example.welfare.admin.dashboard.dto.AdminRecommendationReviewGatePromotionApprovalRecordResponse;
import com.example.welfare.admin.dashboard.repository.RecommendationReviewGatePromotionApprovalCommandRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AdminRecommendationReviewGatePromotionApprovalRecordService {

    private final RecommendationReviewGatePromotionApprovalCommandRepository commandRepository;

    @Transactional
    public AdminRecommendationReviewGatePromotionApprovalRecordResponse recordApproval(
            String approvedByUserKey,
            String approvalNote
    ) {
        LocalDateTime now = LocalDateTime.now();
        String actorKey = StringUtils.hasText(approvedByUserKey) ? approvedByUserKey.trim() : "admin";
        String note = StringUtils.hasText(approvalNote) ? approvalNote.trim() : null;

        commandRepository.upsertApprovalRecord(
                AdminDashboardQueryPolicy.REVIEW_GATE_PROMOTION_APPROVAL_KEY,
                "APPROVED_FOR_BOUNDED_PROMOTION_REVIEW",
                AdminDashboardQueryPolicy.REVIEW_GATE_PROMOTION_APPROVAL_SCOPE,
                note,
                actorKey,
                now,
                now
        );

        return new AdminRecommendationReviewGatePromotionApprovalRecordResponse(
                AdminDashboardQueryPolicy.REVIEW_GATE_PROMOTION_APPROVAL_KEY,
                "APPROVED_FOR_BOUNDED_PROMOTION_REVIEW",
                AdminDashboardQueryPolicy.REVIEW_GATE_PROMOTION_APPROVAL_SCOPE,
                note,
                actorKey,
                now,
                true
        );
    }

    @Transactional
    public AdminRecommendationReviewGatePromotionApprovalRecordClearResponse clearApproval() {
        commandRepository.deleteApprovalRecord(AdminDashboardQueryPolicy.REVIEW_GATE_PROMOTION_APPROVAL_KEY);
        return new AdminRecommendationReviewGatePromotionApprovalRecordClearResponse(
                AdminDashboardQueryPolicy.REVIEW_GATE_PROMOTION_APPROVAL_KEY,
                true
        );
    }
}
