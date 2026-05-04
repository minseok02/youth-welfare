package com.example.welfare.recommend.repository;

import com.example.welfare.recommend.dto.RecommendationCandidateProjection;
import com.example.welfare.recommend.entity.UserRecommendation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class RecommendationSummaryReadRepositoryImplTest {

    @Mock
    private UserRecommendationRepository userRecommendationRepository;
    @Mock
    private CanonicalRecommendationReadModelRepository canonicalRecommendationReadModelRepository;

    @InjectMocks
    private RecommendationSummaryReadRepositoryImpl recommendationSummaryReadRepository;

    @Test
    @DisplayName("recommendation summary read repository는 최신 북마크 service id 조회를 위임한다")
    void findLatestBookmarkedServiceIdsDelegates() {
        given(userRecommendationRepository.findLatestBookmarkedServiceIdsByUserKey("user-key-1", List.of(10L)))
                .willReturn(List.of(10L));

        assertThat(recommendationSummaryReadRepository.findLatestBookmarkedServiceIds("user-key-1", List.of(10L)))
                .containsExactly(10L);
    }

    @Test
    @DisplayName("recommendation summary read repository는 canonical projection 조회를 위임한다")
    void findCandidateProjectionsDelegates() {
        RecommendationCandidateProjection projection = RecommendationCandidateProjection.builder()
                .serviceId(10L)
                .unifiedCategoryCompat("주거")
                .build();
        given(canonicalRecommendationReadModelRepository.findByServiceIds(List.of(10L)))
                .willReturn(Map.of(10L, projection));

        assertThat(recommendationSummaryReadRepository.findCandidateProjections(List.of(10L)))
                .containsEntry(10L, projection);
    }

    @Test
    @DisplayName("recommendation summary read repository는 최신 북마크 추천 목록 조회를 위임한다")
    void findLatestBookmarkedRecommendationsDelegates() {
        UserRecommendation recommendation = UserRecommendation.builder().id(1L).userKey("user-key-1").build();
        given(userRecommendationRepository.findLatestBookmarkedByUserKey("user-key-1"))
                .willReturn(List.of(recommendation));

        assertThat(recommendationSummaryReadRepository.findLatestBookmarkedRecommendations("user-key-1"))
                .containsExactly(recommendation);
        then(userRecommendationRepository).should().findLatestBookmarkedByUserKey("user-key-1");
    }
}
