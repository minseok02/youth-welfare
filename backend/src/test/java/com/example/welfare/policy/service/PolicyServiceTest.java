package com.example.welfare.policy.service;

import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.example.welfare.policy.dto.PolicySummaryResponse;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.repository.ServiceRegionRepository;
import com.example.welfare.policy.repository.ServiceTagRepository;
import com.example.welfare.policy.repository.WelfareServiceDetailRepository;
import com.example.welfare.policy.repository.WelfareServiceRepository;
import com.example.welfare.recommend.dto.RecommendationCandidateProjection;
import com.example.welfare.recommend.entity.UserRecommendation;
import com.example.welfare.recommend.repository.CanonicalRecommendationReadModelRepository;
import com.example.welfare.recommend.repository.UserRecommendationRepository;
import com.example.welfare.user.entity.User;
import com.example.welfare.user.repository.UserRepository;
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

import java.util.Optional;
import java.util.List;

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
    private UserRecommendationRepository userRecommendationRepository;
    @Mock
    private UserRepository userRepository;
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

        given(welfareServiceRepository.findListWithFilters(
                eq("HOUSING"),
                eq(WelfareService.SourceType.YOUTH),
                eq(WelfareService.ServiceStatus.ACTIVE),
                eq(false),
                eq("서울특별시"),
                eq("강남구"),
                eq(true),
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
        verify(welfareServiceRepository).findListWithFilters(
                eq("HOUSING"),
                eq(WelfareService.SourceType.YOUTH),
                eq(WelfareService.ServiceStatus.ACTIVE),
                eq(false),
                eq("서울특별시"),
                eq("강남구"),
                eq(true),
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

        given(welfareServiceRepository.findListWithFilters(
                eq(null),
                eq(null),
                eq(null),
                eq(false),
                eq(null),
                eq(null),
                eq(null),
                any(PageRequest.class)
        )).willReturn(page);
        given(canonicalRecommendationReadModelRepository.findByServiceIds(List.of(11L)))
                .willReturn(java.util.Map.of(
                        11L,
                        RecommendationCandidateProjection.builder()
                                .serviceId(11L)
                                .unifiedCategoryCompat("주거")
                                .build()
                ));
        given(userRepository.findUserKeyById(7L)).willReturn(Optional.of("user-key-7"));
        given(userRecommendationRepository.findLatestBookmarkedServiceIdsByUserKey("user-key-7", List.of(11L)))
                .willReturn(List.of(11L));

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
    }

    @Test
    @DisplayName("기존 추천 이력이 있으면 북마크 상태를 토글한다")
    void toggleBookmarkOnExistingRecommendation() {
        UserRecommendation recommendation = UserRecommendation.builder()
                .id(1L)
                .userKey("user-key-7")
                .isBookmarked(false)
                .build();
        given(userRepository.findUserKeyById(7L)).willReturn(Optional.of("user-key-7"));
        given(userRecommendationRepository.findTopByUserKeyAndServiceIdOrderByRecommendedAtDesc("user-key-7", 11L))
                .willReturn(Optional.of(recommendation));

        policyService.toggleBookmark(7L, 11L);

        assertTrue(recommendation.isBookmarked());
    }

    @Test
    @DisplayName("추천 이력이 없어도 북마크 요청 시 placeholder 추천을 생성한다")
    void toggleBookmarkCreatesPlaceholderWhenMissing() {
        WelfareService service = WelfareService.builder()
                .id(11L)
                .sourceType(WelfareService.SourceType.YOUTH)
                .sourceId("SRC-11")
                .title("청년 정책")
                .build();

        given(userRepository.findUserKeyById(7L)).willReturn(Optional.of("user-key-7"));
        given(userRecommendationRepository.findTopByUserKeyAndServiceIdOrderByRecommendedAtDesc("user-key-7", 11L))
                .willReturn(Optional.empty());
        given(welfareServiceRepository.findById(11L)).willReturn(Optional.of(service));
        given(userRecommendationRepository.save(any(UserRecommendation.class)))
                .willAnswer(invocation -> invocation.getArgument(0, UserRecommendation.class));

        policyService.toggleBookmark(7L, 11L);

        ArgumentCaptor<UserRecommendation> captor = ArgumentCaptor.forClass(UserRecommendation.class);
        verify(userRecommendationRepository).save(captor.capture());
        assertTrue(captor.getValue().isBookmarked());
        assertEquals("user-key-7", captor.getValue().getUserKey());
    }

    @Test
    @DisplayName("북마크가 이미 200건이면 추가 북마크를 막는다")
    void toggleBookmarkRejectsWhenLimitExceeded() {
        UserRecommendation recommendation = UserRecommendation.builder()
                .id(1L)
                .userKey("user-key-7")
                .isBookmarked(false)
                .build();
        given(userRepository.findUserKeyById(7L)).willReturn(Optional.of("user-key-7"));
        given(userRecommendationRepository.findTopByUserKeyAndServiceIdOrderByRecommendedAtDesc("user-key-7", 11L))
                .willReturn(Optional.of(recommendation));
        given(userRecommendationRepository.countByUserKeyAndIsBookmarkedTrue("user-key-7")).willReturn(200L);

        CustomException exception = assertThrows(CustomException.class, () -> policyService.toggleBookmark(7L, 11L));

        assertEquals(ErrorCode.BOOKMARK_LIMIT_EXCEEDED, exception.getErrorCode());
    }
}
