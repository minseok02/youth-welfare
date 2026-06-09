package com.example.welfare.admin.dashboard.service;

import com.example.welfare.admin.dashboard.repository.RecommendationReviewGatePromotionApprovalCommandRepository;
import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class AdminRecommendationReviewGatePromotionApprovalRecordServiceTest {

    @Mock
    private RecommendationReviewGatePromotionApprovalCommandRepository commandRepository;

    @InjectMocks
    private AdminRecommendationReviewGatePromotionApprovalRecordService service;

    @Test
    @DisplayName("recordApproval 은 bounded promotion approval record upsert를 위임한다")
    void recordApprovalDelegatesToCommandRepository() {
        service.recordApproval("admin-user-key", "bounded review approved");

        ArgumentCaptor<LocalDateTime> approvedAtCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> updatedAtCaptor = ArgumentCaptor.forClass(LocalDateTime.class);

        then(commandRepository).should().upsertApprovalRecord(
                org.mockito.ArgumentMatchers.eq(AdminDashboardQueryPolicy.REVIEW_GATE_PROMOTION_APPROVAL_KEY),
                org.mockito.ArgumentMatchers.eq("APPROVED_FOR_BOUNDED_PROMOTION_REVIEW"),
                org.mockito.ArgumentMatchers.eq(AdminDashboardQueryPolicy.REVIEW_GATE_PROMOTION_APPROVAL_SCOPE),
                org.mockito.ArgumentMatchers.eq("bounded review approved"),
                org.mockito.ArgumentMatchers.eq("admin-user-key"),
                approvedAtCaptor.capture(),
                updatedAtCaptor.capture()
        );
        assertThat(approvedAtCaptor.getValue()).isNotNull();
        assertThat(updatedAtCaptor.getValue()).isNotNull();
    }

    @Test
    @DisplayName("clearApproval 은 bounded promotion approval record delete를 위임한다")
    void clearApprovalDelegatesToCommandRepository() {
        service.clearApproval();

        then(commandRepository).should().deleteApprovalRecord(
                AdminDashboardQueryPolicy.REVIEW_GATE_PROMOTION_APPROVAL_KEY
        );
    }

    @Test
    @DisplayName("recordApproval 은 approvalNote가 1000자를 초과하면 거부한다")
    void recordApprovalRejectsTooLongApprovalNote() {
        String tooLongNote = "a".repeat(1001);

        assertThatThrownBy(() -> service.recordApproval("admin-user-key", tooLongNote))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);

        then(commandRepository).should(never()).upsertApprovalRecord(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }
}
