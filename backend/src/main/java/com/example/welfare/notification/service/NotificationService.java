package com.example.welfare.notification.service;

import com.example.welfare.global.util.AesEncryptUtil;
import com.example.welfare.notification.gateway.EmailClient;
import com.example.welfare.notification.gateway.KakaoAlimtalkClient;
import com.example.welfare.recommend.entity.RecommendationLog;
import com.example.welfare.recommend.entity.ScoreWeight;
import com.example.welfare.recommend.entity.UserRecommendation;
import com.example.welfare.recommend.facade.RecommendationFacade;
import com.example.welfare.recommend.repository.ScoreWeightRepository;
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
import java.util.Map;

/**
 * 알림 발송 서비스
 * - 1차: top 3 발송 (notificationYn = true 유저)
 * - 카카오 알림톡 → 실패 시 Gmail SMTP 폴백
 * - 개인정보: 전화번호 AES 복호화 후 발송, 메모리에만 존재
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final UserRepository userRepository;
    private final RecommendationFacade recommendationFacade;
    private final RecommendationLogService logService;
    private final ScoreWeightService scoreWeightService;
    private final KakaoAlimtalkClient kakaoClient;
    private final EmailClient emailClient;
    private final AesEncryptUtil aesEncryptUtil;

    private static final int TOP_N = 3;

    /**
     * 매일 오전 9시 — 알림 수신 동의 유저에게 추천 정책 top 3 발송
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

            String notificationText = buildNotificationText(recs, logs);

            // 카카오 알림톡 시도
            boolean kakaoSuccess = false;
            if (user.getPhoneEnc() != null) {
                String phone = aesEncryptUtil.decrypt(user.getPhoneEnc());
                kakaoSuccess = trySendKakao(phone, recs, logs);
            }

            // 폴백: 이메일
            if (!kakaoSuccess) {
                emailClient.send(
                        user.getEmail(),
                        "[청년복지] 맞춤 정책 추천",
                        notificationText
                );
            }

        } catch (Exception e) {
            log.error("[NotificationService] 알림 발송 실패 userId={}: {}", user.getId(), e.getMessage());
        }
    }

    private boolean trySendKakao(String phone, List<UserRecommendation> recs,
                                   List<RecommendationLog> logs) {
        try {
            String policyNames = recs.stream()
                    .map(r -> "• " + r.getService().getTitle())
                    .reduce("", (a, b) -> a + "\n" + b);

            // 로그 ID를 첫 번째 추천에 연결
            String firstLogId = logs.isEmpty() ? "" : String.valueOf(logs.get(0).getId());

            kakaoClient.send(phone, "YW_RECOMMEND_001", Map.of(
                    "#{정책목록}", policyNames,
                    "#{링크}", "https://youth-welfare.kr/recommendations?log_id=" + firstLogId
            ));
            return true;
        } catch (Exception e) {
            log.warn("[NotificationService] 카카오 실패, 이메일 폴백: {}", e.getMessage());
            return false;
        }
    }

    private String buildNotificationText(List<UserRecommendation> recs, List<RecommendationLog> logs) {
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
