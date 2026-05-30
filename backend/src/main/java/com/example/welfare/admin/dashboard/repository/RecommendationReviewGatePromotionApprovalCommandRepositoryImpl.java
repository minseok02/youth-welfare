package com.example.welfare.admin.dashboard.repository;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;

@Repository
public class RecommendationReviewGatePromotionApprovalCommandRepositoryImpl
        implements RecommendationReviewGatePromotionApprovalCommandRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public RecommendationReviewGatePromotionApprovalCommandRepositoryImpl(
            @Qualifier("recommendationReviewGateCommandNamedParameterJdbcTemplate")
            NamedParameterJdbcTemplate jdbcTemplate
    ) {
        this.jdbcTemplate = jdbcTemplate;
    }

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
                        values (:approvalKey, :approvalStatus, :approvalScope, :approvalNote,
                                :approvedByUserKey, :approvedAt, :createdAt, :updatedAt)
                        on conflict (approval_key) do update
                            set approval_status = excluded.approval_status,
                                approval_scope = excluded.approval_scope,
                                approval_note = excluded.approval_note,
                                approved_by_user_key = excluded.approved_by_user_key,
                                approved_at = excluded.approved_at,
                                updated_at = excluded.updated_at
                        """,
                new MapSqlParameterSource()
                        .addValue("approvalKey", approvalKey)
                        .addValue("approvalStatus", approvalStatus)
                        .addValue("approvalScope", approvalScope)
                        .addValue("approvalNote", approvalNote)
                        .addValue("approvedByUserKey", approvedByUserKey)
                        .addValue("approvedAt", Timestamp.valueOf(approvedAt))
                        .addValue("createdAt", Timestamp.valueOf(updatedAt))
                        .addValue("updatedAt", Timestamp.valueOf(updatedAt))
        );
    }

    @Override
    public void deleteApprovalRecord(String approvalKey) {
        jdbcTemplate.update("""
                        delete
                          from recommendation_review_gate_promotion_approvals
                         where approval_key = :approvalKey
                        """,
                new MapSqlParameterSource("approvalKey", approvalKey)
        );
    }
}
