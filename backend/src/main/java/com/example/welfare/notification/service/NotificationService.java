package com.example.welfare.notification.service;

import com.example.welfare.notification.gateway.NotificationGateway;
import com.example.welfare.recommend.entity.RecommendationLog;
import com.example.welfare.recommend.entity.ScoreWeight;
import com.example.welfare.recommend.entity.UserRecommendation;
import com.example.welfare.recommend.facade.RecommendationFacade;
import com.example.welfare.recommend.service.RecommendationLogService;
import com.example.welfare.recommend.service.ScoreWeightService;
import com.example.welfare.user.entity.User;
import com.example.welfare.user.entity.User.NotificationPeriod;
import com.example.welfare.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 알림 발송 서비스 — Gmail SMTP 이메일만 사용
 * - top 3 발송 (notificationYn = true 유저)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final UserRepository userRepository;
    private final RecommendationFacade recommendationFacade;
    private final RecommendationLogService logService;
    private final ScoreWeightService scoreWeightService;
    private final NotificationGateway notificationGateway;

    private static final int TOP_N = 3;

    /**
     * 매일 오전 9시 — 일간 알림 수신 동의 유저에게 추천 정책 top 3 발송
     */
    @Scheduled(cron = "0 0 9 * * *", zone = "Asia/Seoul")
    public void sendDailyNotifications() {
        List<User> targets = userRepository.findByNotificationYnTrueAndNotificationPeriod(
                NotificationPeriod.DAILY);
        log.info("[NotificationService] 일간 알림 대상: {}명", targets.size());
        targets.forEach(this::sendTopRecommendations);
    }

    /**
     * 매주 월요일 오전 9시 — 주간 알림
     */
    @Scheduled(cron = "0 0 9 * * MON", zone = "Asia/Seoul")
    public void sendWeeklyNotifications() {
        List<User> targets = userRepository.findByNotificationYnTrueAndNotificationPeriod(
                NotificationPeriod.WEEKLY);
        log.info("[NotificationService] 주간 알림 대상: {}명", targets.size());
        targets.forEach(this::sendTopRecommendations);
    }

    @Transactional
    public void sendTopRecommendations(User user) {
        try {
            List<UserRecommendation> recs = recommendationFacade.getRecommendations(user.getId(), TOP_N);
            if (recs.isEmpty()) return;

            ScoreWeight weight = scoreWeightService.getActiveWeight();
            List<RecommendationLog> logs = logService.logNotification(user, recs, weight);

            notificationGateway.send(
                    user.getEmail(),
                    "[청년복지] 맞춤 정책 추천",
                    buildEmailText(recs, logs)
            );

        } catch (Exception e) {
            log.error("[NotificationService] 알림 발송 실패 userId={}: {}", user.getId(), e.getMessage());
        }
    }

    private String buildEmailText(List<UserRecommendation> recs, List<RecommendationLog> logs) {
        StringBuilder sb = new StringBuilder("맞춤 복지 정책 추천\n\n");
        for (int i = 0; i < recs.size(); i++) {
            UserRecommendation rec = recs.get(i);
            String logId = (i < logs.size()) ? String.valueOf(logs.get(i).getId()) : "";
            sb.append(i + 1).append(". ").append(rec.getService().getTitle()).append("\n");
            sb.append("   https://youth-welfare.kr/policies/")
                    .append(rec.getService().getId())
                    .append("?log_id=").append(logId).append("\n\n");
        }
        return sb.toString();
    }
}
