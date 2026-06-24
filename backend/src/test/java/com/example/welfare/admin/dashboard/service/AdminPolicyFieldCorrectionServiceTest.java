package com.example.welfare.admin.dashboard.service;

import com.example.welfare.admin.dashboard.dto.AdminPolicyFieldCorrectionRequest;
import com.example.welfare.global.exception.CustomException;
import com.example.welfare.policy.entity.PolicyErrorReport;
import com.example.welfare.policy.entity.PolicyFieldCorrection;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.repository.PolicyErrorReportRepository;
import com.example.welfare.policy.repository.PolicyFieldCorrectionRepository;
import com.example.welfare.policy.service.PolicyLookupService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class AdminPolicyFieldCorrectionServiceTest {

    @Mock
    private PolicyLookupService policyLookupService;
    @Mock
    private PolicyErrorReportRepository policyErrorReportRepository;
    @Mock
    private PolicyFieldCorrectionRepository policyFieldCorrectionRepository;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private AdminPolicyFieldCorrectionService service;

    @BeforeEach
    void setUp() {
        service = new AdminPolicyFieldCorrectionService(
                policyLookupService,
                policyErrorReportRepository,
                policyFieldCorrectionRepository,
                objectMapper
        );
    }

    @Test
    @DisplayName("신청기간 보정은 정책 기간을 바꾸고 연결된 제보를 처리완료한다")
    void applyApplicationPeriodCorrectionUpdatesPolicyAndReviewsReport() {
        WelfareService policy = WelfareService.builder()
                .id(200L)
                .sourceType(WelfareService.SourceType.GOV24)
                .sourceId("GOV-200")
                .title("기간 보정 대상")
                .applyStartDate(LocalDate.of(2026, 1, 1))
                .applyEndDate(LocalDate.of(2026, 1, 31))
                .build();
        PolicyErrorReport report = PolicyErrorReport.builder()
                .id(20L)
                .policy(policy)
                .reasonCode(PolicyErrorReport.ReasonCode.PERIOD_MISMATCH)
                .status(PolicyErrorReport.Status.OPEN)
                .build();
        given(policyLookupService.getRequiredService(200L)).willReturn(policy);
        given(policyErrorReportRepository.findById(20L)).willReturn(Optional.of(report));
        given(policyFieldCorrectionRepository.save(any(PolicyFieldCorrection.class))).willAnswer(invocation -> invocation.getArgument(0));

        service.applyCorrection(
                new AdminPolicyFieldCorrectionRequest(
                        200L,
                        20L,
                        "APPLICATION_PERIOD",
                        LocalDate.of(2026, 2, 1),
                        LocalDate.of(2026, 2, 28),
                        null,
                        null,
                        null,
                        "상세 공고 기준으로 기간 보정"
                ),
                "admin-key"
        );

        assertThat(policy.getApplyStartDate()).isEqualTo(LocalDate.of(2026, 2, 1));
        assertThat(policy.getApplyEndDate()).isEqualTo(LocalDate.of(2026, 2, 28));
        assertThat(report.getStatus()).isEqualTo(PolicyErrorReport.Status.REVIEWED);
        assertThat(report.getReviewNote()).isEqualTo("상세 공고 기준으로 기간 보정");

        ArgumentCaptor<PolicyFieldCorrection> correctionCaptor = ArgumentCaptor.forClass(PolicyFieldCorrection.class);
        then(policyFieldCorrectionRepository).should().save(correctionCaptor.capture());
        assertThat(correctionCaptor.getValue().getCorrectionType()).isEqualTo(PolicyFieldCorrection.Type.APPLICATION_PERIOD);
        assertThat(correctionCaptor.getValue().getOriginalJson()).contains("2026-01-01", "2026-01-31");
        assertThat(correctionCaptor.getValue().getCorrectionJson()).contains("2026-02-01", "2026-02-28");
        assertThat(correctionCaptor.getValue().getUpdatedByUserKey()).isEqualTo("admin-key");
    }

    @Test
    @DisplayName("링크 보정은 원문 URL을 바꾸고 이력을 저장한다")
    void applyDetailUrlCorrectionUpdatesPolicy() {
        WelfareService policy = WelfareService.builder()
                .id(201L)
                .sourceType(WelfareService.SourceType.GOV24)
                .sourceId("GOV-201")
                .title("링크 보정 대상")
                .detailUrl("https://old.example/policy")
                .build();
        given(policyLookupService.getRequiredService(201L)).willReturn(policy);
        given(policyFieldCorrectionRepository.save(any(PolicyFieldCorrection.class))).willAnswer(invocation -> invocation.getArgument(0));

        service.applyCorrection(
                new AdminPolicyFieldCorrectionRequest(
                        201L,
                        null,
                        "DETAIL_URL",
                        null,
                        null,
                        "https://new.example/policy",
                        null,
                        null,
                        "원문 링크 교체"
                ),
                "admin-key"
        );

        assertThat(policy.getDetailUrl()).isEqualTo("https://new.example/policy");
        ArgumentCaptor<PolicyFieldCorrection> correctionCaptor = ArgumentCaptor.forClass(PolicyFieldCorrection.class);
        then(policyFieldCorrectionRepository).should().save(correctionCaptor.capture());
        assertThat(correctionCaptor.getValue().getCorrectionType()).isEqualTo(PolicyFieldCorrection.Type.DETAIL_URL);
        assertThat(correctionCaptor.getValue().getCorrectionJson()).contains("https://new.example/policy");
    }

    @Test
    @DisplayName("링크 보정은 http/https 외 URL을 거부한다")
    void applyDetailUrlCorrectionRejectsUnsafeScheme() {
        WelfareService policy = WelfareService.builder()
                .id(203L)
                .sourceType(WelfareService.SourceType.GOV24)
                .sourceId("GOV-203")
                .title("위험 링크 보정 대상")
                .build();
        given(policyLookupService.getRequiredService(203L)).willReturn(policy);

        assertThatThrownBy(() -> service.applyCorrection(
                new AdminPolicyFieldCorrectionRequest(
                        203L,
                        null,
                        "DETAIL_URL",
                        null,
                        null,
                        "javascript:alert(1)",
                        null,
                        null,
                        "위험 링크"
                ),
                "admin-key"
        )).isInstanceOf(CustomException.class);
    }

    @Test
    @DisplayName("링크 보정은 user-info가 있는 오인 가능 URL을 거부한다")
    void applyDetailUrlCorrectionRejectsUserInfoUrl() {
        WelfareService policy = WelfareService.builder()
                .id(204L)
                .sourceType(WelfareService.SourceType.GOV24)
                .sourceId("GOV-204")
                .title("위험 링크 보정 대상")
                .build();
        given(policyLookupService.getRequiredService(204L)).willReturn(policy);

        assertThatThrownBy(() -> service.applyCorrection(
                new AdminPolicyFieldCorrectionRequest(
                        204L,
                        null,
                        "DETAIL_URL",
                        null,
                        null,
                        "https://trusted.example.com@evil.example.com/apply",
                        null,
                        null,
                        "위험 링크"
                ),
                "admin-key"
        )).isInstanceOf(CustomException.class);
    }

    @Test
    @DisplayName("중복 정책 보정은 기준 정책 ID가 없으면 거부한다")
    void duplicateCorrectionRequiresDuplicatePolicyId() {
        WelfareService policy = WelfareService.builder()
                .id(202L)
                .sourceType(WelfareService.SourceType.GOV24)
                .sourceId("GOV-202")
                .title("중복 보정 대상")
                .build();
        given(policyLookupService.getRequiredService(202L)).willReturn(policy);

        assertThatThrownBy(() -> service.applyCorrection(
                new AdminPolicyFieldCorrectionRequest(
                        202L,
                        null,
                        "DUPLICATE_POLICY",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                ),
                "admin-key"
        )).isInstanceOf(CustomException.class);
    }
}
