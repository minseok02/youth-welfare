package com.example.welfare.notification.service;

import com.example.welfare.global.util.JwtUtil;
import com.example.welfare.recommend.entity.RecommendationLog;
import com.example.welfare.recommend.entity.UserRecommendation;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationMessageService {

    private final JwtUtil jwtUtil;

    @Value("${app.base-url:https://youth-welfare.kr}")
    private String appBaseUrl;

    public String buildRecommendationMessage(
            String userKey,
            Long userId,
            List<UserRecommendation> recommendations,
            List<RecommendationLog> logs
    ) {
        StringBuilder sb = new StringBuilder("맞춤 복지 정책 추천\n\n");
        for (int i = 0; i < recommendations.size(); i++) {
            UserRecommendation recommendation = recommendations.get(i);
            String logId = (i < logs.size()) ? String.valueOf(logs.get(i).getId()) : "";
            sb.append(i + 1).append(". ").append(recommendation.getService().getTitle()).append("\n");
            if (recommendation.getAiReason() != null && !recommendation.getAiReason().isBlank()) {
                sb.append("   추천 이유: ").append(recommendation.getAiReason()).append("\n");
            }
            sb.append("   ").append(appBaseUrl).append("/policies/")
                    .append(recommendation.getService().getId())
                    .append("?log_id=").append(logId).append("\n\n");
        }
        if (userKey != null && userId != null) {
            sb.append("수신 거부: ").append(appBaseUrl).append("/api/notifications/unsubscribe?token=")
                    .append(jwtUtil.generateNotificationToken(userKey, userId))
                    .append("\n");
        }
        return sb.toString();
    }
}
