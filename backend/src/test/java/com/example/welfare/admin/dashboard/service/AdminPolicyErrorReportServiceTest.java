package com.example.welfare.admin.dashboard.service;

import com.example.welfare.admin.dashboard.dto.AdminPolicyErrorReportResponse;
import com.example.welfare.admin.dashboard.dto.AdminReviewActionResponse;
import com.example.welfare.policy.entity.PolicyErrorReport;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.repository.PolicyErrorReportRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class AdminPolicyErrorReportServiceTest {

    @Mock
    private PolicyErrorReportRepository policyErrorReportRepository;

    @InjectMocks
    private AdminPolicyErrorReportService adminPolicyErrorReportService;

    @Test
    @DisplayName("정책 오류 제보 대시보드는 OPEN count와 최근 목록을 함께 반환한다")
    void getRecentReportsReturnsOpenSummary() {
        WelfareService policy = WelfareService.builder()
                .id(33L)
                .sourceType(WelfareService.SourceType.GOV24)
                .sourceId("SRC-33")
                .title("청년 교통비 지원")
                .build();
        PolicyErrorReport report = PolicyErrorReport.builder()
                .id(9L)
                .policy(policy)
                .userKey("user-key-1")
                .reasonCode(PolicyErrorReport.ReasonCode.BROKEN_LINK)
                .note("원문 링크가 404입니다.")
                .status(PolicyErrorReport.Status.OPEN)
                .build();

        given(policyErrorReportRepository.countByStatus(PolicyErrorReport.Status.OPEN)).willReturn(4L);
        given(policyErrorReportRepository.countByStatusAndCreatedAtAfter(any(), any())).willReturn(2L);
        given(policyErrorReportRepository.findByStatusOrderByCreatedAtDesc(any(), any(Pageable.class)))
                .willReturn(List.of(report));

        AdminPolicyErrorReportResponse response = adminPolicyErrorReportService.getRecentReports(5);

        assertThat(response.openCount()).isEqualTo(4L);
        assertThat(response.recentOpenCount24h()).isEqualTo(2L);
        assertThat(response.recentReports()).hasSize(1);
        assertThat(response.recentReports().get(0).policyTitle()).isEqualTo("청년 교통비 지원");
        assertThat(response.recentReports().get(0).reasonLabel()).isEqualTo("링크나 원문이 열리지 않습니다");
    }

    @Test
    @DisplayName("정책 오류 제보를 REVIEWED로 처리하면 메모와 처리자가 함께 저장된다")
    void markReviewedUpdatesStatusAndReviewMetadata() {
        WelfareService policy = WelfareService.builder()
                .id(34L)
                .sourceType(WelfareService.SourceType.GOV24)
                .sourceId("SRC-34")
                .title("청년 월세 지원")
                .build();
        PolicyErrorReport report = PolicyErrorReport.builder()
                .id(10L)
                .policy(policy)
                .userKey("user-key-2")
                .reasonCode(PolicyErrorReport.ReasonCode.REGION_MISMATCH)
                .note("서울 정책이 인천 사용자에게 보입니다.")
                .status(PolicyErrorReport.Status.OPEN)
                .build();
        given(policyErrorReportRepository.findById(10L)).willReturn(Optional.of(report));

        AdminReviewActionResponse response = adminPolicyErrorReportService.markReviewed(
                10L,
                "admin-user-key",
                "지역 mismatch 재현 후 조치 완료"
        );

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.status()).isEqualTo("REVIEWED");
        assertThat(response.reviewNote()).isEqualTo("지역 mismatch 재현 후 조치 완료");
        assertThat(response.reviewedByUserKey()).isEqualTo("admin-user-key");
        assertThat(response.reviewedAt()).isNotNull();
        assertThat(report.getStatus()).isEqualTo(PolicyErrorReport.Status.REVIEWED);
    }
}
