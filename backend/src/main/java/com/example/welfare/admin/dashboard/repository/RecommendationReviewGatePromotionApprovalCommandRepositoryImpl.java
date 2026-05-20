package com.example.welfare.admin.dashboard.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;

@Repository
@RequiredArgsConstructor
public class RecommendationReviewGatePromotionApprovalCommandRepositoryImpl
        implements RecommendationReviewGatePromotionApprovalCommandRepository {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void upsertApprovalRecord(
            String approvalKey,
            String approvalStatus,
            String approvalScope,
            String approvalNote,
            String approvedByUserKey,
            LocalDateTime approvedAt,
            LocalDateTime updatedAt
    ) {
        jdbcTemplate.update("""
                        insert into recommendation_review_gate_promotion_approvals
                            (approval_key, approval_status, approval_scope, approval_note,
                             approved_by_user_key, approved_at, created_at, updated_at)
                        values (?, ?, ?, ?, ?, ?, ?, ?)
                        on conflict (approval_key) do update
                            set approval_status = excluded.approval_status,
                                approval_scope = excluded.approval_scope,
                                approval_note = excluded.approval_note,
                                approved_by_user_key = excluded.approved_by_user_key,
                                approved_at = excluded.approved_at,
                                updated_at = excluded.updated_at
                        """,
                approvalKey,
                approvalStatus,
                approvalScope,
                approvalNote,
                approvedByUserKey,
                Timestamp.valueOf(approvedAt),
                Timestamp.valueOf(updatedAt),
                Timestamp.valueOf(updatedAt)
        );
    }

    @Override
    public void deleteApprovalRecord(String approvalKey) {
        jdbcTemplate.update("""
                        delete
                          from recommendation_review_gate_promotion_approvals
                         where approval_key = ?
                        """,
                approvalKey
        );
    }
}
