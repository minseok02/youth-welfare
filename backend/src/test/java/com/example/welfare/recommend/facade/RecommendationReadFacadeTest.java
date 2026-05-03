package com.example.welfare.recommend.facade;

import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.recommend.dto.RecommendationCandidateProjection;
import com.example.welfare.recommend.entity.UserRecommendation;
import com.example.welfare.recommend.repository.CanonicalRecommendationReadModelRepository;
import com.example.welfare.recommend.repository.UserRecommendationRepository;
import com.example.welfare.user.service.UserKeyLookupService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecommendationReadFacadeTest {

    @Mock private UserRecommendationRepository userRecommendationRepository;
    @Mock private CanonicalRecommendationReadModelRepository canonicalRecommendationReadModelRepository;
    @Mock private UserKeyLookupService userKeyLookupService;

    @Test
    @DisplayName("북마크 서비스 id 조회는 nullable userKey와 최신 북마크 기준으로 반환한다")
    void findBookmarkedServiceIdsReturnsLatestBookmarks() {
        RecommendationReadFacade facade = new RecommendationReadFacade(
                userRecommendationRepository,
                canonicalRecommendationReadModelRepository,
                userKeyLookupService
        );
        WelfareService service = WelfareService.builder()
                .id(11L)
                .sourceType(WelfareService.SourceType.YOUTH)
                .sourceId("Y-11")
                .title("청년 월세 지원")
                .build();

        when(userKeyLookupService.findNullable(1L)).thenReturn("user-key-1");
        when(userRecommendationRepository.findLatestBookmarkedServiceIdsByUserKey("user-key-1", List.of(11L)))
                .thenReturn(List.of(11L));

        Set<Long> bookmarkedIds = facade.findBookmarkedServiceIds(1L, List.of(service));

        assertThat(bookmarkedIds).containsExactly(11L);
    }

    @Test
    @DisplayName("추천 projection 조회는 recommendation 목록의 service id를 canonical read model에 위임한다")
    void findCandidateProjectionsDelegatesToCanonicalReadModelRepository() {
        RecommendationReadFacade facade = new RecommendationReadFacade(
                userRecommendationRepository,
                canonicalRecommendationReadModelRepository,
                userKeyLookupService
        );
        WelfareService service = WelfareService.builder()
                .id(11L)
                .sourceType(WelfareService.SourceType.YOUTH)
                .sourceId("Y-11")
                .title("청년 월세 지원")
                .build();
        UserRecommendation recommendation = UserRecommendation.builder()
                .id(100L)
                .userKey("user-key-1")
                .service(service)
                .build();
        RecommendationCandidateProjection projection = RecommendationCandidateProjection.builder()
                .serviceId(11L)
                .unifiedCategoryCompat("주거")
                .build();

        when(canonicalRecommendationReadModelRepository.findByServiceIds(List.of(11L)))
                .thenReturn(Map.of(11L, projection));

        Map<Long, RecommendationCandidateProjection> result =
                facade.findCandidateProjections(List.of(recommendation));

        assertThat(result).containsEntry(11L, projection);
        verify(canonicalRecommendationReadModelRepository).findByServiceIds(List.of(11L));
    }
}
