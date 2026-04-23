package com.example.welfare.recommend.service;

import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.recommend.dto.ScoredCandidate;
import com.example.welfare.recommend.entity.ScoreWeight;
import com.example.welfare.recommend.entity.UserRecommendation;
import com.example.welfare.recommend.repository.UserRecommendationRepository;
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
    private UserRecommendationRepository userRecommendationRepository;

    @InjectMocks
    private RecommendationPersistenceService recommendationPersistenceService;

    @Test
    @DisplayName("저장 전 사용자 비북마크 추천을 정리하고 새 추천을 저장한다")
    void saveDeletesPreviousUnbookmarkedRecommendations() {
        User user = User.builder().id(7L).build();
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
                .build();
        candidate.setAiScore(88.0);
        candidate.setAiReason("reason");
        candidate.setFinalScore(0.73);

        ScoreWeight weight = ScoreWeight.builder()
                .weightKey("COLD_START")
                .ruleWeight(BigDecimal.valueOf(0.8))
                .aiWeight(BigDecimal.valueOf(0.2))
                .minLogCount(0)
                .isActive(true)
                .build();

        when(userRecommendationRepository.findLatestByUserId(7L)).thenReturn(List.of());
        when(userRecommendationRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        recommendationPersistenceService.save(user, List.of(candidate), weight);

        verify(userRecommendationRepository).findLatestByUserId(7L);
        verify(userRecommendationRepository).deleteUnbookmarkedByUserId(7L);
        ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
        verify(userRecommendationRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
    }

    @Test
    @DisplayName("새 추천 저장 시 기존 최신 추천의 북마크 상태를 이어받는다")
    void saveCarriesOverBookmarkState() {
        User user = User.builder().id(7L).build();
        WelfareService service = WelfareService.builder()
                .id(11L)
                .sourceType(WelfareService.SourceType.YOUTH)
                .sourceId("Y1")
                .title("청년 정책")
                .status(WelfareService.ServiceStatus.ACTIVE)
                .build();
        UserRecommendation latestRecommendation = UserRecommendation.builder()
                .id(99L)
                .user(user)
                .service(service)
                .recommendedAt(LocalDateTime.now())
                .isBookmarked(true)
                .build();
        ScoredCandidate candidate = ScoredCandidate.builder()
                .service(service)
                .ruleBaseScore(10.0)
                .ruleWeightedScore(12.0)
                .build();
        candidate.setFinalScore(0.73);

        ScoreWeight weight = ScoreWeight.builder()
                .weightKey("COLD_START")
                .ruleWeight(BigDecimal.valueOf(0.8))
                .aiWeight(BigDecimal.valueOf(0.2))
                .minLogCount(0)
                .isActive(true)
                .build();

        when(userRecommendationRepository.findLatestByUserId(7L)).thenReturn(List.of(latestRecommendation));
        when(userRecommendationRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        List<UserRecommendation> saved = recommendationPersistenceService.save(user, List.of(candidate), weight);

        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).isBookmarked()).isTrue();
    }
}
