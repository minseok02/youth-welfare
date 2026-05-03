package com.example.welfare.policy.service;

import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.example.welfare.policy.dto.PolicySummaryResponse;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.repository.PolicyListReadCondition;
import com.example.welfare.policy.repository.ServiceRegionRepository;
import com.example.welfare.policy.repository.ServiceTagRepository;
import com.example.welfare.policy.repository.WelfareServiceReadRepository;
import com.example.welfare.policy.repository.WelfareServiceDetailRepository;
import com.example.welfare.policy.repository.WelfareServiceRepository;
import com.example.welfare.recommend.dto.RecommendationCandidateProjection;
import com.example.welfare.recommend.facade.RecommendationReadFacade;
import com.example.welfare.recommend.repository.CanonicalRecommendationReadModelRepository;
import com.example.welfare.recommend.service.RecommendationBookmarkCommandService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PolicyServiceTest {

    @Mock
    private WelfareServiceRepository welfareServiceRepository;
    @Mock
    private WelfareServiceDetailRepository detailRepository;
    @Mock
    private ServiceRegionRepository regionRepository;
    @Mock
    private ServiceTagRepository tagRepository;
    @Mock
    private WelfareServiceReadRepository welfareServiceReadRepository;
    @Mock
    private RecommendationReadFacade recommendationReadFacade;
    @Mock
    private RecommendationBookmarkCommandService recommendationBookmarkCommandService;
    @Mock
    private CanonicalRecommendationReadModelRepository canonicalRecommendationReadModelRepository;

    @InjectMocks
    private PolicyService policyService;

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
                        false,
                        "서울특별시",
                        "강남구",
                        true
                )),
                any(PageRequest.class)
        )).willReturn(page);
        given(canonicalRecommendationReadModelRepository.findByServiceIds(List.of(11L)))
                .willReturn(java.util.Map.of());

        Page<?> result = policyService.getList(
                null,
                "HOUSING",
                "YOUTH",
                "ACTIVE",
                false,
                "서울특별시",
                "강남구",
                true,
                "views",
                PageRequest.of(0, 20)
        );

        assertEquals(1, result.getTotalElements());
        ArgumentCaptor<PageRequest> captor = ArgumentCaptor.forClass(PageRequest.class);
        verify(welfareServiceReadRepository).findList(
                eq(new PolicyListReadCondition(
                        "HOUSING",
                        WelfareService.SourceType.YOUTH,
                        WelfareService.ServiceStatus.ACTIVE,
                        false,
                        "서울특별시",
                        "강남구",
                        true
                )),
                captor.capture()
        );
        assertEquals("viewCount: DESC", captor.getValue().getSort().getOrderFor("viewCount").toString());
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
                eq(new PolicyListReadCondition(null, null, null, false, null, null, null)),
                any(PageRequest.class)
        )).willReturn(page);
        given(canonicalRecommendationReadModelRepository.findByServiceIds(List.of(11L)))
                .willReturn(java.util.Map.of(
                        11L,
                        RecommendationCandidateProjection.builder()
                                .serviceId(11L)
                                .unifiedCategoryCompat("주거")
                                .youthMajorLabel("주거")
                                .youthMidLabel("전월세 및 주거급여 지원")
                                .provisionMethodLabel("온라인")
                                .gov24ServiceFieldLabel("보육")
                                .gov24UserTypeLabel("청년")
                                .gov24BenefitTypeLabel("서비스")
                                .build()
                ));
        given(recommendationReadFacade.findBookmarkedServiceIds(7L, List.of(service)))
                .willReturn(Set.of(11L));

        Page<PolicySummaryResponse> result = policyService.getList(
                7L,
                null,
                null,
                null,
                false,
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
        assertEquals("보육", result.getContent().get(0).getGov24ServiceFieldLabel());
        assertEquals("청년", result.getContent().get(0).getGov24UserTypeLabel());
        assertEquals("서비스", result.getContent().get(0).getGov24BenefitTypeLabel());
    }

    @Test
    @DisplayName("기존 추천 이력이 있으면 북마크 상태를 토글한다")
    void toggleBookmarkOnExistingRecommendation() {
        policyService.toggleBookmark(7L, 11L);

        verify(recommendationBookmarkCommandService).togglePolicyBookmark(7L, 11L);
    }

    @Test
    @DisplayName("추천 이력이 없어도 북마크 요청 시 placeholder 추천을 생성한다")
    void toggleBookmarkCreatesPlaceholderWhenMissing() {
        policyService.toggleBookmark(7L, 11L);

        verify(recommendationBookmarkCommandService).togglePolicyBookmark(7L, 11L);
    }

    @Test
    @DisplayName("북마크가 이미 200건이면 추가 북마크를 막는다")
    void toggleBookmarkRejectsWhenLimitExceeded() {
        org.mockito.BDDMockito.willThrow(new CustomException(ErrorCode.BOOKMARK_LIMIT_EXCEEDED))
                .given(recommendationBookmarkCommandService)
                .togglePolicyBookmark(7L, 11L);

        CustomException exception = assertThrows(CustomException.class, () -> policyService.toggleBookmark(7L, 11L));

        assertEquals(ErrorCode.BOOKMARK_LIMIT_EXCEEDED, exception.getErrorCode());
    }
}
