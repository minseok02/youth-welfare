package com.example.welfare.recommend.service;

import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.recommend.dto.ScoredCandidate;
import com.example.welfare.recommend.entity.ScoreWeight;
import com.example.welfare.recommend.entity.UserRecommendation;
import com.example.welfare.recommend.repository.RecommendationPersistenceCommandRepository;
import com.example.welfare.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecommendationPersistenceServiceTest {

    @Mock
    private RecommendationPersistenceCommandRepository recommendationPersistenceCommandRepository;
    @Mock
    private RecommendationBookmarkStateReadService recommendationBookmarkStateReadService;

    @InjectMocks
    private RecommendationPersistenceService recommendationPersistenceService;

    @Test
    @DisplayName("저장 전 사용자 추천 전체를 삭제하고 새 추천을 저장한다")
    void saveDeletesAllPreviousRecommendations() {
        User user = User.builder().id(7L).userKey("user-key-7").build();
        WelfareService service = WelfareService.builder()
                .id(11L)
                .sourceType(WelfareService.SourceType.YOUTH)
                .sourceId("Y1")
                .title("청년 정책")
                .status(WelfareService.ServiceStatus.ACTIVE)
                .build();
        ScoredCandidate candidate = ScoredCandidate.builder()
                .service(service)
                .ruleBaseScore(10.0)
                .ruleWeightedScore(12.0)
                .aiScore(88.0)
                .aiReason("reason")
                .finalScore(0.73)
                .build();

        ScoreWeight weight = ScoreWeight.builder()
                .weightKey("COLD_START")
                .ruleWeight(BigDecimal.valueOf(0.8))
                .aiWeight(BigDecimal.valueOf(0.2))
                .minLogCount(0)
                .isActive(true)
                .build();

        when(recommendationBookmarkStateReadService.findLatestBookmarkStateByServiceId("user-key-7"))
                .thenReturn(java.util.Map.of());
        when(recommendationPersistenceCommandRepository.replaceAllForUser(org.mockito.ArgumentMatchers.eq("user-key-7"), anyList()))
                .thenAnswer(invocation -> invocation.getArgument(1));

        recommendationPersistenceService.save(user, List.of(candidate), weight);

        verify(recommendationBookmarkStateReadService).findLatestBookmarkStateByServiceId("user-key-7");
        ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
        verify(recommendationPersistenceCommandRepository).replaceAllForUser(org.mockito.ArgumentMatchers.eq("user-key-7"), captor.capture());
        assertThat(captor.getValue()).hasSize(1);
    }

    @Test
    @DisplayName("새 추천 저장 시 기존 최신 추천의 북마크 상태를 이어받는다")
    void saveCarriesOverBookmarkState() {
        User user = User.builder().id(7L).userKey("user-key-7").build();
        WelfareService service = WelfareService.builder()
                .id(11L)
                .sourceType(WelfareService.SourceType.YOUTH)
                .sourceId("Y1")
                .title("청년 정책")
                .status(WelfareService.ServiceStatus.ACTIVE)
                .build();
        UserRecommendation latestRecommendation = UserRecommendation.builder()
                .id(99L)
                .userKey("user-key-7")
                .service(service)
                .recommendedAt(LocalDateTime.now())
                .isBookmarked(true)
                .build();
        ScoredCandidate candidate = ScoredCandidate.builder()
                .service(service)
                .ruleBaseScore(10.0)
                .ruleWeightedScore(12.0)
                .finalScore(0.73)
                .build();

        ScoreWeight weight = ScoreWeight.builder()
                .weightKey("COLD_START")
                .ruleWeight(BigDecimal.valueOf(0.8))
                .aiWeight(BigDecimal.valueOf(0.2))
                .minLogCount(0)
                .isActive(true)
                .build();

        when(recommendationBookmarkStateReadService.findLatestBookmarkStateByServiceId("user-key-7"))
                .thenReturn(java.util.Map.of(11L, true));
        when(recommendationPersistenceCommandRepository.replaceAllForUser(org.mockito.ArgumentMatchers.eq("user-key-7"), anyList()))
                .thenAnswer(invocation -> invocation.getArgument(1));

        List<UserRecommendation> saved = recommendationPersistenceService.save(user, List.of(candidate), weight);

        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).isBookmarked()).isTrue();
        assertThat(saved.get(0).getUserKey()).isEqualTo("user-key-7");
    }

}
