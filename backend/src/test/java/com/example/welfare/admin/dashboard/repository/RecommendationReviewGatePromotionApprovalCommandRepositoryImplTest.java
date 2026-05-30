package com.example.welfare.admin.dashboard.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class RecommendationReviewGatePromotionApprovalCommandRepositoryImplTest {

    @Mock
    private NamedParameterJdbcTemplate recommendationReviewGateCommandNamedParameterJdbcTemplate;

    @InjectMocks
    private RecommendationReviewGatePromotionApprovalCommandRepositoryImpl repository;

    @Test
    @DisplayName("promotion approval upsert는 전용 command jdbc로 위임한다")
    void upsertApprovalRecordDelegatesToCommandJdbc() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 30, 12, 0, 0);

        repository.upsertApprovalRecord(
                "approval-key",
                "APPROVED",
                "scope-a",
                "note-a",
                "admin-key",
                now,
                now
        );

        ArgumentCaptor<SqlParameterSource> parameterCaptor = ArgumentCaptor.forClass(SqlParameterSource.class);
        then(recommendationReviewGateCommandNamedParameterJdbcTemplate).should().update(
                eq("""
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
                        """),
                parameterCaptor.capture()
        );
        assertThat(parameterCaptor.getValue().getValue("approvalKey")).isEqualTo("approval-key");
        assertThat(parameterCaptor.getValue().getValue("approvedByUserKey")).isEqualTo("admin-key");
    }

    @Test
    @DisplayName("promotion approval delete는 전용 command jdbc로 위임한다")
    void deleteApprovalRecordDelegatesToCommandJdbc() {
        repository.deleteApprovalRecord("approval-key");

        ArgumentCaptor<SqlParameterSource> parameterCaptor = ArgumentCaptor.forClass(SqlParameterSource.class);
        then(recommendationReviewGateCommandNamedParameterJdbcTemplate).should().update(
                eq("""
                        delete
                          from recommendation_review_gate_promotion_approvals
                         where approval_key = :approvalKey
                        """),
                parameterCaptor.capture()
        );
        assertThat(parameterCaptor.getValue().getValue("approvalKey")).isEqualTo("approval-key");
    }
}
