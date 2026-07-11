package com.example.welfare.policy.service;

import com.example.welfare.policy.dto.PolicyDetailResponse;
import com.example.welfare.policy.entity.ServiceRegion;
import com.example.welfare.policy.entity.ServiceTag;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.entity.WelfareServiceDetail;
import com.example.welfare.recommend.dto.RecommendationCandidateProjection;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PolicyDetailServiceTest {

    @Mock
    private PolicyLookupService policyLookupService;
    @Mock
    private PolicyDetailReadService policyDetailReadService;
    @Mock
    private PolicyPresentationReadService policyPresentationReadService;

    @InjectMocks
    private PolicyDetailService policyDetailService;

    @Test
    @DisplayName("정책 상세 조회는 lookup/read 경계를 통해 상세 aggregate를 조립한다")
    void getDetailUsesLookupAndDetailReadBoundary() {
        LocalDate applyStartDate = LocalDate.now().minusDays(10);
        LocalDate applyEndDate = LocalDate.now().plusDays(30);
        WelfareService service = WelfareService.builder()
                .id(11L)
                .sourceType(WelfareService.SourceType.YOUTH)
                .sourceId("SRC-11")
                .title("청년 월세 지원")
                .status(WelfareService.ServiceStatus.ACTIVE)
                .hostOrg("서울시")
                .operatingOrg("서울청년센터")
                .applyStartDate(applyStartDate)
                .applyEndDate(applyEndDate)
                .viewCount(7)
                .build();
        WelfareServiceDetail detail = WelfareServiceDetail.builder()
                .service(service)
                .targetDetail("청년")
                .supportDetail("월세")
                .applyMethodDetail("온라인 접수")
                .selectionCriteria("소득 심사")
                .supportCycle("분기별")
                .provisionType("바우처")
                .homepageUrl("https://apply.example.com")
                .relatedLaw("청년기본법")
                .formFiles("신청서, 주민등록등본")
                .referenceUrlsJson("[{\"url\":\"https://apply.example.com\",\"type\":\"APPLY\"}]")
                .build();
        ServiceRegion region = ServiceRegion.builder()
                .service(service)
                .sidoName("서울특별시")
                .sggName("강남구")
                .build();
        ServiceTag tag = ServiceTag.builder()
                .service(service)
                .tagType(ServiceTag.TagType.KEYWORD)
                .tagValue("주거")
                .build();

        given(policyLookupService.getRequiredService(11L)).willReturn(service);
        given(policyDetailReadService.getAggregate(11L))
                .willReturn(new PolicyDetailReadService.PolicyDetailAggregate(detail, List.of(region), List.of(tag)));
        given(policyPresentationReadService.buildDetailPresentation(7L, service))
                .willReturn(new PolicyPresentationReadService.PolicyDetailPresentation(
                        true,
                        RecommendationCandidateProjection.builder()
                                .serviceId(11L)
                                .unifiedCategoryCompat("주거")
                                .youthIncomeConditionTypeLabel("무관")
                                .youthEmploymentRequirementLabels(List.of("미취업자"))
                                .youthEducationRequirementLabels(List.of("대학 재학"))
                                .youthSpecialRequirementLabels(List.of("지역인재"))
                                .youthMaritalStatusLabel("제한없음")
                                .build()
                ));

        PolicyDetailResponse response = policyDetailService.getDetail(7L, 11L, true);

        assertEquals(11L, response.getId());
        assertTrue(response.isBookmarked());
        assertEquals("주거", response.getUnifiedCategory());
        assertEquals(8, response.getViewCount());
        assertEquals("서울시", response.getProviderName());
        assertEquals("서울특별시 강남구", response.getRegionLabel());
        assertEquals(applyStartDate + " ~ " + applyEndDate, response.getApplicationPeriod());
        assertEquals("진행중", response.getStatusLabel());
        assertEquals("소득 심사", response.getSelectionCriteria());
        assertEquals("분기별", response.getSupportCycle());
        assertEquals("바우처", response.getProvisionType());
        assertEquals("https://apply.example.com", response.getHomepageUrl());
        assertEquals("청년기본법", response.getRelatedLaw());
        assertEquals("신청서, 주민등록등본", response.getFormFiles());
        assertEquals("[{\"url\":\"https://apply.example.com\",\"type\":\"APPLY\"}]", response.getReferenceUrlsJson());
        assertEquals(List.of("서울특별시 강남구"), response.getRegions());
        assertEquals("무관", response.getYouthIncomeConditionTypeLabel());
        assertEquals(List.of("미취업자"), response.getYouthEmploymentRequirementLabels());
        assertEquals(List.of("대학 재학"), response.getYouthEducationRequirementLabels());
        assertEquals(List.of("지역인재"), response.getYouthSpecialRequirementLabels());
        assertEquals("제한없음", response.getYouthMaritalStatusLabel());
        verify(policyLookupService).getRequiredService(11L);
        verify(policyDetailReadService).getAggregate(11L);
    }

    @Test
    @DisplayName("정책 상세 region label은 기관 힌트와 맞는 지역을 우선한다")
    void getDetailPrefersOrganizationRegionHint() {
        WelfareService service = WelfareService.builder()
                .id(9344L)
                .sourceType(WelfareService.SourceType.GOV24)
                .sourceId("SRC-9344")
                .title("청년 월세 지원")
                .status(WelfareService.ServiceStatus.ACTIVE)
                .hostOrg("충청북도 옥천군")
                .operatingOrg("성장정책과")
                .build();
        WelfareServiceDetail detail = WelfareServiceDetail.builder()
                .service(service)
                .build();
        ServiceRegion wrong = ServiceRegion.builder()
                .service(service)
                .sidoName("전북특별자치도")
                .sggName("무주군")
                .build();
        ServiceRegion correct = ServiceRegion.builder()
                .service(service)
                .sidoName("충청북도")
                .sggName("옥천군")
                .build();

        given(policyLookupService.getRequiredService(9344L)).willReturn(service);
        given(policyDetailReadService.getAggregate(9344L))
                .willReturn(new PolicyDetailReadService.PolicyDetailAggregate(detail, List.of(wrong, correct), List.of()));
        given(policyPresentationReadService.buildDetailPresentation(null, service))
                .willReturn(new PolicyPresentationReadService.PolicyDetailPresentation(false, null));

        PolicyDetailResponse response = policyDetailService.getDetail(null, 9344L, false);

        assertEquals("충청북도 옥천군", response.getRegionLabel());
        assertEquals(List.of("전북특별자치도 무주군", "충청북도 옥천군"), response.getRegions());
    }

    @Test
    @DisplayName("정책 상세 region label은 code-only local row를 행정구역 코드에서 복구한다")
    void getDetailResolvesCodeOnlyLocalRegion() {
        WelfareService service = WelfareService.builder()
                .id(196L)
                .sourceType(WelfareService.SourceType.GOV24)
                .sourceId("SRC-196")
                .title("청년구직자 면접비 지원사업")
                .status(WelfareService.ServiceStatus.ACTIVE)
                .hostOrg("경제과")
                .build();
        WelfareServiceDetail detail = WelfareServiceDetail.builder()
                .service(service)
                .build();
        ServiceRegion codeOnly = ServiceRegion.builder()
                .service(service)
                .regionCode("44150")
                .build();

        given(policyLookupService.getRequiredService(196L)).willReturn(service);
        given(policyDetailReadService.getAggregate(196L))
                .willReturn(new PolicyDetailReadService.PolicyDetailAggregate(detail, List.of(codeOnly), List.of()));
        given(policyPresentationReadService.buildDetailPresentation(null, service))
                .willReturn(new PolicyPresentationReadService.PolicyDetailPresentation(false, null));

        PolicyDetailResponse response = policyDetailService.getDetail(null, 196L, false);

        assertEquals("충청남도 공주시", response.getRegionLabel());
        assertEquals(List.of("충청남도 공주시"), response.getRegions());
    }
}
