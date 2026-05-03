package com.example.welfare.user.service;

import com.example.welfare.policy.dto.PolicySummaryResponse;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.recommend.dto.RecommendationCandidateProjection;
import com.example.welfare.recommend.entity.UserRecommendation;
import com.example.welfare.recommend.repository.CanonicalRecommendationReadModelRepository;
import com.example.welfare.recommend.repository.UserRecommendationRepository;
import com.example.welfare.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserBookmarkReadServiceTest {

    @Mock private UserReadService userReadService;
    @Mock private UserRecommendationRepository userRecommendationRepository;
    @Mock private CanonicalRecommendationReadModelRepository canonicalRecommendationReadModelRepository;

    @Test
    @DisplayName("북마크 목록 조회는 최신 북마크 추천을 정책 요약 응답으로 변환한다")
    void getBookmarksReturnsPolicySummaries() {
        UserBookmarkReadService service = new UserBookmarkReadService(
                userReadService,
                userRecommendationRepository,
                canonicalRecommendationReadModelRepository
        );
        User user = User.builder()
                .id(1L)
                .email("user@example.com")
                .passwordHash("hash")
                .build();
        WelfareService policy = WelfareService.builder()
                .id(11L)
                .sourceType(WelfareService.SourceType.YOUTH)
                .sourceId("Y-11")
                .title("청년 월세 지원")
                .description("월세 부담 완화")
                .unifiedCategory("HOUSING")
                .status(WelfareService.ServiceStatus.ACTIVE)
                .build();
        when(userReadService.getActiveUserContext(1L))
                .thenReturn(new UserReadService.ActiveUserContext(user, "user-key-1"));
        when(userRecommendationRepository.findLatestBookmarkedByUserKey("user-key-1"))
                .thenReturn(List.of(UserRecommendation.builder()
                        .id(100L)
                        .userKey("user-key-1")
                        .service(policy)
                        .isBookmarked(true)
                        .build()));
        when(canonicalRecommendationReadModelRepository.findByServiceIds(List.of(11L)))
                .thenReturn(java.util.Map.of(
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

        List<PolicySummaryResponse> response = service.getBookmarks(1L);

        assertThat(response).hasSize(1);
        assertThat(response.get(0).getId()).isEqualTo(11L);
        assertThat(response.get(0).getTitle()).isEqualTo("청년 월세 지원");
        assertThat(response.get(0).getUnifiedCategory()).isEqualTo("주거");
        assertThat(response.get(0).getYouthMajorLabel()).isEqualTo("주거");
        assertThat(response.get(0).getYouthMidLabel()).isEqualTo("전월세 및 주거급여 지원");
        assertThat(response.get(0).getProvisionMethodLabel()).isEqualTo("온라인");
        assertThat(response.get(0).getGov24ServiceFieldLabel()).isEqualTo("보육");
        assertThat(response.get(0).getGov24UserTypeLabel()).isEqualTo("청년");
        assertThat(response.get(0).getGov24BenefitTypeLabel()).isEqualTo("서비스");
        assertThat(response.get(0).isBookmarked()).isTrue();
    }
}
