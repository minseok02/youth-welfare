package com.example.welfare.policy.service;

import com.example.welfare.policy.dto.PolicySummaryResponse;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.repository.PolicyListReadCondition;
import com.example.welfare.policy.repository.WelfareServiceReadRepository;
import com.example.welfare.recommend.dto.RecommendationCandidateProjection;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PolicyListServiceTest {

    @Mock
    private WelfareServiceReadRepository welfareServiceReadRepository;
    @Mock
    private PolicyPresentationReadService policyPresentationReadService;

    @InjectMocks
    private PolicyListService policyListService;

    @Test
    @DisplayName("정책 목록 조회는 필터와 정렬을 정규화해 저장소에 전달한다")
    void getListNormalizesFiltersAndSort() {
        WelfareService service = WelfareService.builder()
                .id(11L)
                .sourceType(WelfareService.SourceType.YOUTH)
                .sourceId("SRC-11")
                .title("청년 월세 지원")
                .status(WelfareService.ServiceStatus.ACTIVE)
                .build();
        Page<WelfareService> page = new PageImpl<>(List.of(service));

        given(welfareServiceReadRepository.findList(
                eq(new PolicyListReadCondition(
                        "HOUSING",
                        WelfareService.SourceType.YOUTH,
                        WelfareService.ServiceStatus.ACTIVE,
                        "ACTIVE_ONLY",
                        "서울특별시",
                        "강남구",
                        true,
                        "VIEWS",
                        null,
                        null
                )),
                any(PageRequest.class)
        )).willReturn(page);
        given(policyPresentationReadService.buildSummaryPage(eq(null), any(Page.class)))
                .willAnswer(invocation -> invocation.getArgument(1));

        Page<?> result = policyListService.getList(
                null,
                "HOUSING",
                "YOUTH",
                "ACTIVE",
                null,
                "서울특별시",
                "강남구",
                true,
                "views",
                null,
                null,
                PageRequest.of(0, 20)
        );

        assertEquals(1, result.getTotalElements());
        ArgumentCaptor<PageRequest> captor = ArgumentCaptor.forClass(PageRequest.class);
        verify(welfareServiceReadRepository).findList(
                eq(new PolicyListReadCondition(
                        "HOUSING",
                        WelfareService.SourceType.YOUTH,
                        WelfareService.ServiceStatus.ACTIVE,
                        "ACTIVE_ONLY",
                        "서울특별시",
                        "강남구",
                        true,
                        "VIEWS",
                        null,
                        null
                )),
                captor.capture()
        );
        assertEquals(0, captor.getValue().getPageNumber());
        assertEquals(20, captor.getValue().getPageSize());
    }

    @Test
    @DisplayName("정책 목록 조회는 로그인 사용자의 최신 북마크 상태를 응답에 포함한다")
    void getListIncludesBookmarkState() {
        WelfareService service = WelfareService.builder()
                .id(11L)
                .sourceType(WelfareService.SourceType.YOUTH)
                .sourceId("SRC-11")
                .title("청년 월세 지원")
                .status(WelfareService.ServiceStatus.ACTIVE)
                .build();
        Page<WelfareService> page = new PageImpl<>(List.of(service));

        given(welfareServiceReadRepository.findList(
                eq(new PolicyListReadCondition(null, null, null, "ACTIVE_ONLY", null, null, null, "LATEST", null, null)),
                any(PageRequest.class)
        )).willReturn(page);
        given(policyPresentationReadService.buildSummaryPage(eq(7L), any(Page.class)))
                .willReturn(new PageImpl<>(List.of(PolicySummaryResponse.from(
                        service,
                        true,
                        RecommendationCandidateProjection.builder()
                                .serviceId(11L)
                                .unifiedCategoryCompat("주거")
                                .youthMajorLabel("주거")
                                .youthMidLabel("전월세 및 주거급여 지원")
                                .provisionMethodLabel("온라인")
                                .youthIncomeConditionTypeLabel("무관")
                                .youthEmploymentRequirementLabels(List.of("미취업자"))
                                .youthEducationRequirementLabels(List.of("대학 재학"))
                                .youthSpecialRequirementLabels(List.of("지역인재"))
                                .youthMaritalStatusLabel("제한없음")
                                .gov24ServiceFieldLabel("주거·자립")
                                .gov24UserTypeLabel("청년")
                                .gov24BenefitTypeLabel("서비스")
                                .build()
                ))));

        Page<PolicySummaryResponse> result = policyListService.getList(
                7L,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                PageRequest.of(0, 20)
        );

        assertTrue(result.getContent().get(0).isBookmarked());
        assertEquals("주거", result.getContent().get(0).getUnifiedCategory());
        assertEquals("주거", result.getContent().get(0).getYouthMajorLabel());
        assertEquals("전월세 및 주거급여 지원", result.getContent().get(0).getYouthMidLabel());
        assertEquals("온라인", result.getContent().get(0).getProvisionMethodLabel());
        assertEquals("무관", result.getContent().get(0).getYouthIncomeConditionTypeLabel());
        assertEquals(List.of("미취업자"), result.getContent().get(0).getYouthEmploymentRequirementLabels());
        assertEquals(List.of("대학 재학"), result.getContent().get(0).getYouthEducationRequirementLabels());
        assertEquals(List.of("지역인재"), result.getContent().get(0).getYouthSpecialRequirementLabels());
        assertEquals("제한없음", result.getContent().get(0).getYouthMaritalStatusLabel());
        assertEquals("주거·자립", result.getContent().get(0).getGov24ServiceFieldLabel());
        assertEquals("청년", result.getContent().get(0).getGov24UserTypeLabel());
        assertEquals("서비스", result.getContent().get(0).getGov24BenefitTypeLabel());
    }

    @Test
    @DisplayName("정책 목록 조회는 과도한 페이지 크기를 상한으로 제한한다")
    void getListClampsOversizedPageSize() {
        given(welfareServiceReadRepository.findList(
                eq(new PolicyListReadCondition(null, null, null, "ACTIVE_ONLY", null, null, null, "LATEST", null, null)),
                any(PageRequest.class)
        )).willReturn(Page.empty());
        given(policyPresentationReadService.buildSummaryPage(eq(null), any(Page.class)))
                .willReturn(Page.empty());

        policyListService.getList(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                PageRequest.of(0, 10_000)
        );

        ArgumentCaptor<PageRequest> captor = ArgumentCaptor.forClass(PageRequest.class);
        verify(welfareServiceReadRepository).findList(
                eq(new PolicyListReadCondition(null, null, null, "ACTIVE_ONLY", null, null, null, "LATEST", null, null)),
                captor.capture()
        );
        assertEquals(100, captor.getValue().getPageSize());
    }
}
