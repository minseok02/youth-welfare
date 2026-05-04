package com.example.welfare.recommend.service;

import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.recommend.entity.RecommendationLog;
import com.example.welfare.recommend.entity.ScoreWeight;
import com.example.welfare.recommend.entity.UserRecommendation;
import com.example.welfare.recommend.repository.RecommendationLogCommandRepository;
import com.example.welfare.recommend.repository.RecommendationLogReadRepository;
import com.example.welfare.user.entity.User;
import com.example.welfare.user.service.UserKeyLookupService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class RecommendationLogServiceTest {

    @Mock
    private RecommendationLogCommandRepository recommendationLogCommandRepository;
    @Mock
    private RecommendationLogReadRepository recommendationLogReadRepository;
    @Mock
    private UserKeyLookupService userKeyLookupService;

    @InjectMocks
    private RecommendationLogService recommendationLogService;

    @Test
    @DisplayName("refreshLogs는 기존 미클릭 로그를 지우고 새 로그를 저장한다")
    void refreshLogsDeletesUnclickedBeforeSave() {
        User user = User.builder().userKey("user-key-1").build();
        WelfareService service = WelfareService.builder().id(10L).title("policy").build();
        UserRecommendation recommendation = UserRecommendation.builder()
                .service(service)
                .finalScore(BigDecimal.valueOf(0.8))
                .build();
        ScoreWeight weight = ScoreWeight.builder()
                .ruleWeight(BigDecimal.valueOf(0.7))
                .aiWeight(BigDecimal.valueOf(0.3))
                .build();
        given(recommendationLogCommandRepository.saveAll(anyList()))
                .willAnswer(invocation -> invocation.getArgument(0));

        recommendationLogService.refreshLogs(user, List.of(recommendation), weight);

        then(recommendationLogCommandRepository).should().deleteUnclickedByUserKey("user-key-1");
        then(recommendationLogCommandRepository).should().saveAll(anyList());
    }

    @Test
    @DisplayName("findLatestLogIdMap은 userKey별 최신 로그 id 맵을 반환한다")
    void findLatestLogIdMapReturnsMap() {
        RecommendationLog log = RecommendationLog.builder()
                .id(100L)
                .service(WelfareService.builder().id(10L).build())
                .build();
        given(userKeyLookupService.findNullable(7L)).willReturn("user-key-7");
        given(recommendationLogReadRepository.findLatestByUserKeyAndServiceIds("user-key-7", List.of(10L)))
                .willReturn(List.of(log));

        Map<Long, Long> actual = recommendationLogService.findLatestLogIdMap(7L, List.of(10L));

        assertThat(actual).containsEntry(10L, 100L);
    }

    @Test
    @DisplayName("findLatestLogIdMap은 비로그인 userKey가 없으면 빈 맵을 반환한다")
    void findLatestLogIdMapReturnsEmptyWhenNoUserKey() {
        given(userKeyLookupService.findNullable(7L)).willReturn(null);

        assertThat(recommendationLogService.findLatestLogIdMap(7L, List.of(10L))).isEmpty();
        then(recommendationLogReadRepository).should(never()).findLatestByUserKeyAndServiceIds("user-key-7", List.of(10L));
    }

    @Test
    @DisplayName("markClicked는 로그를 찾아 클릭 처리한다")
    void markClickedUpdatesLog() {
        RecommendationLog log = RecommendationLog.builder().id(100L).isClicked(false).build();
        given(recommendationLogCommandRepository.findById(100L)).willReturn(Optional.of(log));

        recommendationLogService.markClicked(100L);

        assertThat(log.isClicked()).isTrue();
    }

    @Test
    @DisplayName("markClicked는 로그가 없으면 예외를 던진다")
    void markClickedThrowsWhenMissing() {
        given(recommendationLogCommandRepository.findById(100L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> recommendationLogService.markClicked(100L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RECOMMENDATION_NOT_FOUND);
    }

    @Test
    @DisplayName("logNotification은 fallback 여부와 weight를 포함해 로그를 저장한다")
    void logNotificationPersistsComposedLogs() {
        User user = User.builder().userKey("user-key-1").build();
        WelfareService service = WelfareService.builder().id(10L).title("policy").build();
        UserRecommendation recommendation = UserRecommendation.builder()
                .service(service)
                .finalScore(BigDecimal.valueOf(0.8))
                .aiScore(null)
                .build();
        ScoreWeight weight = ScoreWeight.builder()
                .ruleWeight(BigDecimal.valueOf(0.7))
                .aiWeight(BigDecimal.valueOf(0.3))
                .build();
        given(recommendationLogCommandRepository.saveAll(anyList()))
                .willAnswer(invocation -> invocation.getArgument(0));

        recommendationLogService.logNotification(user, List.of(recommendation), weight);

        ArgumentCaptor<List<RecommendationLog>> captor = ArgumentCaptor.forClass(List.class);
        then(recommendationLogCommandRepository).should().saveAll(captor.capture());
        RecommendationLog saved = captor.getValue().get(0);
        assertThat(saved.getUserKey()).isEqualTo("user-key-1");
        assertThat(saved.isFallback()).isTrue();
        assertThat(saved.getRuleWeightUsed()).isEqualByComparingTo("0.7");
        assertThat(saved.getAiWeightUsed()).isEqualByComparingTo("0.3");
    }
}
