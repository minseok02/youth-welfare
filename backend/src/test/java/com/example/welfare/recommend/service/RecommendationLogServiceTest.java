package com.example.welfare.recommend.service;

import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.recommend.entity.RecommendationLog;
import com.example.welfare.recommend.entity.ScoreWeight;
import com.example.welfare.recommend.entity.UserRecommendation;
import com.example.welfare.recommend.repository.RecommendationLogCommandRepository;
import com.example.welfare.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class RecommendationLogServiceTest {

    @Mock
    private RecommendationLogCommandRepository recommendationLogCommandRepository;

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
