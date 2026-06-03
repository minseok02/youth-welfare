package com.example.welfare.admin.dashboard.service;

import com.example.welfare.admin.dashboard.dto.AdminPolicyErrorReportResponse;
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
        given(policyErrorReportRepository.findByStatusOrderByCreatedAtDesc(any(), any(Pageable.class)))
                .willReturn(List.of(report));

        AdminPolicyErrorReportResponse response = adminPolicyErrorReportService.getRecentReports(5);

        assertThat(response.openCount()).isEqualTo(4L);
        assertThat(response.recentReports()).hasSize(1);
        assertThat(response.recentReports().get(0).policyTitle()).isEqualTo("청년 교통비 지원");
        assertThat(response.recentReports().get(0).reasonLabel()).isEqualTo("링크나 원문이 열리지 않습니다");
    }
}
