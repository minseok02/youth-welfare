package com.example.welfare.admin.dashboard.service;

import com.example.welfare.admin.dashboard.dto.AdminPolicyRegionCorrectionRequest;
import com.example.welfare.admin.dashboard.dto.AdminPolicyRegionCorrectionResponse;
import com.example.welfare.global.exception.CustomException;
import com.example.welfare.policy.entity.PolicyErrorReport;
import com.example.welfare.policy.entity.PolicyRegionCorrection;
import com.example.welfare.policy.entity.ServiceRegion;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.repository.PolicyErrorReportRepository;
import com.example.welfare.policy.repository.PolicyRegionCorrectionRepository;
import com.example.welfare.policy.repository.ServiceRegionRepository;
import com.example.welfare.policy.service.PolicyLookupService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class AdminPolicyRegionCorrectionServiceTest {

    @Mock
    private PolicyLookupService policyLookupService;
    @Mock
    private PolicyRegionCorrectionRepository policyRegionCorrectionRepository;
    @Mock
    private PolicyErrorReportRepository policyErrorReportRepository;
    @Mock
    private ServiceRegionRepository serviceRegionRepository;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private AdminPolicyRegionCorrectionService service;

    @BeforeEach
    void setUp() {
        service = new AdminPolicyRegionCorrectionService(
                policyLookupService,
                policyRegionCorrectionRepository,
                policyErrorReportRepository,
                serviceRegionRepository,
                objectMapper
        );
    }

    @Test
    @DisplayName("지역 보정은 correction row를 저장하고 service_regions를 교체하며 연결 제보를 처리완료한다")
    void applyRegionCorrectionReplacesServiceRegionsAndReviewsReport() {
        WelfareService policy = WelfareService.builder()
                .id(100L)
                .sourceType(WelfareService.SourceType.GOV24)
                .sourceId("GOV-100")
                .title("인천공항 견학 프로그램")
                .build();
        PolicyErrorReport report = PolicyErrorReport.builder()
                .id(10L)
                .policy(policy)
                .reasonCode(PolicyErrorReport.ReasonCode.REGION_MISMATCH)
                .status(PolicyErrorReport.Status.OPEN)
                .build();
        given(policyLookupService.getRequiredService(100L)).willReturn(policy);
        given(policyErrorReportRepository.findById(10L)).willReturn(Optional.of(report));
        given(policyRegionCorrectionRepository.findByServiceId(100L)).willReturn(Optional.empty());
        given(serviceRegionRepository.findByServiceId(100L)).willReturn(List.of());
        given(policyRegionCorrectionRepository.save(any(PolicyRegionCorrection.class))).willAnswer(invocation -> invocation.getArgument(0));

        AdminPolicyRegionCorrectionResponse response = service.applyCorrection(
                new AdminPolicyRegionCorrectionRequest(
                        100L,
                        false,
                        List.of("28110"),
                        10L,
                        "인천공항 시설 정책으로 지역 보정"
                ),
                "admin-key"
        );

        ArgumentCaptor<PolicyRegionCorrection> correctionCaptor = ArgumentCaptor.forClass(PolicyRegionCorrection.class);
        then(policyRegionCorrectionRepository).should().save(correctionCaptor.capture());
        assertThat(correctionCaptor.getValue().getCorrectionScope()).isEqualTo(PolicyRegionCorrection.Scope.REGIONS);
        assertThat(correctionCaptor.getValue().getRegionsJson()).contains("28110", "인천광역시", "중구");
        assertThat(correctionCaptor.getValue().getOriginalRegionsJson()).isEqualTo("[]");
        assertThat(correctionCaptor.getValue().getUpdatedByUserKey()).isEqualTo("admin-key");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ServiceRegion>> regionsCaptor = ArgumentCaptor.forClass(List.class);
        then(serviceRegionRepository).should().deleteByServiceId(100L);
        then(serviceRegionRepository).should().saveAll(regionsCaptor.capture());
        assertThat(regionsCaptor.getValue())
                .singleElement()
                .satisfies(region -> {
                    assertThat(region.getService()).isSameAs(policy);
                    assertThat(region.getRegionCode()).isEqualTo("28110");
                    assertThat(region.getSidoName()).isEqualTo("인천광역시");
                    assertThat(region.getSggName()).isEqualTo("중구");
                });
        assertThat(report.getStatus()).isEqualTo(PolicyErrorReport.Status.REVIEWED);
        assertThat(report.getReviewNote()).isEqualTo("인천공항 시설 정책으로 지역 보정");
        assertThat(response.correctionScope()).isEqualTo("REGIONS");
        assertThat(response.regions()).extracting(AdminPolicyRegionCorrectionResponse.RegionItem::regionCode)
                .containsExactly("28110");
    }

    @Test
    @DisplayName("전국 보정은 correction row를 NATIONWIDE로 저장하고 service_regions를 비운다")
    void applyNationwideCorrectionClearsServiceRegions() {
        WelfareService policy = WelfareService.builder()
                .id(101L)
                .sourceType(WelfareService.SourceType.GOV24)
                .sourceId("GOV-101")
                .title("전국 정책")
                .build();
        given(policyLookupService.getRequiredService(101L)).willReturn(policy);
        given(policyRegionCorrectionRepository.findByServiceId(101L)).willReturn(Optional.empty());
        given(serviceRegionRepository.findByServiceId(101L)).willReturn(List.of());
        given(policyRegionCorrectionRepository.save(any(PolicyRegionCorrection.class))).willAnswer(invocation -> invocation.getArgument(0));

        AdminPolicyRegionCorrectionResponse response = service.applyCorrection(
                new AdminPolicyRegionCorrectionRequest(
                        101L,
                        true,
                        List.of(),
                        null,
                        "기관명 때문에 잘못 지역화되어 전국으로 보정"
                ),
                "admin-key"
        );

        then(serviceRegionRepository).should().deleteByServiceId(101L);
        then(serviceRegionRepository).should(never()).saveAll(any());
        assertThat(response.correctionScope()).isEqualTo("NATIONWIDE");
        assertThat(response.regions()).isEmpty();
    }

    @Test
    @DisplayName("알 수 없는 행정구역코드는 거부한다")
    void applyRegionCorrectionRejectsUnknownRegionCode() {
        WelfareService policy = WelfareService.builder()
                .id(102L)
                .sourceType(WelfareService.SourceType.GOV24)
                .sourceId("GOV-102")
                .title("지역 보정 대상")
                .build();
        given(policyLookupService.getRequiredService(102L)).willReturn(policy);

        assertThatThrownBy(() -> service.applyCorrection(
                new AdminPolicyRegionCorrectionRequest(
                        102L,
                        false,
                        List.of("99999"),
                        null,
                        null
                ),
                "admin-key"
        )).isInstanceOf(CustomException.class);
    }
}
