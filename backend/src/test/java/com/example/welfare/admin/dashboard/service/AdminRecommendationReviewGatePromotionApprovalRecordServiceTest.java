package com.example.welfare.admin.dashboard.service;

import com.example.welfare.admin.dashboard.repository.RecommendationReviewGatePromotionApprovalCommandRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.then;

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
}
