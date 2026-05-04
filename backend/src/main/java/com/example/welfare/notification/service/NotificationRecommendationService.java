package com.example.welfare.notification.service;

import com.example.welfare.notification.dto.NotificationTarget;
import com.example.welfare.notification.entity.Notification.NotificationPeriodType;
import com.example.welfare.recommend.entity.RecommendationLog;
import com.example.welfare.recommend.entity.ScoreWeight;
import com.example.welfare.recommend.entity.UserRecommendation;
import com.example.welfare.recommend.service.RecommendationAccessService;
import com.example.welfare.recommend.service.RecommendationLogService;
import com.example.welfare.recommend.service.ScoreWeightService;
import com.example.welfare.user.entity.User;
import com.example.welfare.user.entity.User.NotificationPeriod;
import com.example.welfare.user.service.ActiveUserReadService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class NotificationRecommendationService {

    private static final int TOP_N = 3;

    private final ActiveUserReadService activeUserReadService;
    private final RecommendationAccessService recommendationAccessService;
    private final RecommendationLogService recommendationLogService;
    private final ScoreWeightService scoreWeightService;
    private final NotificationSlotSelector notificationSlotSelector;

    @Transactional
    public Optional<NotificationDispatchPlan> prepareDispatch(NotificationTarget target) {
        User user = activeUserReadService.getActiveUserByUserKey(target.userKey());
        double minScore = target.notificationMinScore() != null ? target.notificationMinScore() : 0.0;
        int poolSize = Math.max(50, Math.max(TOP_N, target.displayCount()));

        List<UserRecommendation> recommendationPool =
                recommendationAccessService.getRecommendations(target.userId(), poolSize);
        List<UserRecommendation> recommendations =
                notificationSlotSelector.selectCandidates(recommendationPool, minScore);
        if (recommendations.isEmpty()) {
            return Optional.empty();
        }

        ScoreWeight weight = scoreWeightService.getActiveWeight();
        List<RecommendationLog> logs = recommendationLogService.logNotification(user, recommendations, weight);
        return Optional.of(new NotificationDispatchPlan(
                user,
                toPeriodType(target.notificationPeriod()),
                recommendations,
                logs
        ));
    }

    private NotificationPeriodType toPeriodType(NotificationPeriod period) {
        return switch (period) {
            case DAILY -> NotificationPeriodType.DAILY;
            case WEEKLY -> NotificationPeriodType.WEEKLY;
            case NONE -> NotificationPeriodType.MANUAL;
        };
    }

    public record NotificationDispatchPlan(
            User user,
            NotificationPeriodType periodType,
            List<UserRecommendation> recommendations,
            List<RecommendationLog> logs
    ) {
    }
}
