package com.example.welfare.policy.service;

import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.example.welfare.policy.dto.PolicyErrorReportCreateRequest;
import com.example.welfare.policy.dto.PolicyErrorReportResponse;
import com.example.welfare.policy.entity.PolicyErrorReport;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.repository.PolicyErrorReportRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PolicyErrorReportCommandServiceTest {

    @Mock
    private PolicyLookupService policyLookupService;
    @Mock
    private PolicyErrorReportRepository policyErrorReportRepository;

    @InjectMocks
    private PolicyErrorReportCommandService policyErrorReportCommandService;

    @Test
    @DisplayName("정책 오류 제보는 policy lookup 후 OPEN 상태로 저장한다")
    void submitCreatesOpenReport() {
        WelfareService policy = WelfareService.builder()
                .id(11L)
                .sourceType(WelfareService.SourceType.GOV24)
                .sourceId("SRC-11")
                .title("청년 월세 지원")
                .build();
        given(policyLookupService.getRequiredService(11L)).willReturn(policy);
        given(policyErrorReportRepository.save(any(PolicyErrorReport.class)))
                .willAnswer(invocation -> {
                    PolicyErrorReport report = invocation.getArgument(0);
                    return PolicyErrorReport.builder()
                            .id(91L)
                            .policy(report.getPolicy())
                            .userId(report.getUserId())
                            .userKey(report.getUserKey())
                            .reasonCode(report.getReasonCode())
                            .note(report.getNote())
                            .status(report.getStatus())
                            .build();
                });

        PolicyErrorReportResponse response = policyErrorReportCommandService.submit(
                7L,
                "user-key-7",
                11L,
                new PolicyErrorReportCreateRequest(PolicyErrorReport.ReasonCode.REGION_MISMATCH, "서울이 아니라 인천입니다.")
        );

        ArgumentCaptor<PolicyErrorReport> captor = ArgumentCaptor.forClass(PolicyErrorReport.class);
        verify(policyErrorReportRepository).save(captor.capture());
        assertEquals(PolicyErrorReport.Status.OPEN, captor.getValue().getStatus());
        assertEquals("user-key-7", captor.getValue().getUserKey());
        assertEquals("청년 월세 지원", response.policyTitle());
        assertEquals("지역 정보가 다릅니다", response.reasonLabel());
    }

    @Test
    @DisplayName("사유 코드가 없으면 입력 오류를 반환한다")
    void submitRejectsMissingReasonCode() {
        CustomException exception = assertThrows(CustomException.class, () -> policyErrorReportCommandService.submit(
                7L,
                "user-key-7",
                11L,
                new PolicyErrorReportCreateRequest(null, "메모")
        ));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT);
    }
}
