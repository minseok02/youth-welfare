package com.example.welfare.notification.service;

import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.example.welfare.notification.entity.Notification;
import com.example.welfare.notification.entity.UserAlert;
import com.example.welfare.notification.entity.UserAlert.UserAlertKind;
import com.example.welfare.notification.entity.UserAlert.UserAlertStatus;
import com.example.welfare.notification.repository.UserAlertRepository;
import com.example.welfare.recommend.entity.UserRecommendation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserAlertCommandService {

    private final UserAlertRepository userAlertRepository;

    @Transactional
    public void createRecommendationDigestAlert(Notification notification, List<UserRecommendation> recommendations) {
        if (notification == null || notification.getDispatchKey() == null || notification.getUserKey() == null) {
            return;
        }
        if (userAlertRepository.findByEventKey(notification.getDispatchKey()).isPresent()) {
            return;
        }
        userAlertRepository.save(UserAlert.builder()
                .userKey(notification.getUserKey())
                .eventKey(notification.getDispatchKey())
                .kind(UserAlertKind.RECOMMENDATION_DIGEST)
                .status(UserAlertStatus.UNREAD)
                .title("맞춤 정책 추천이 도착했어요")
                .body(buildDigestBody(recommendations))
                .deeplinkUrl(buildDigestDeeplink(recommendations))
                .metadataJson(buildMetadataJson(notification, recommendations))
                .build());
    }

    @Transactional
    public void markRead(String userKey, Long alertId) {
        UserAlert alert = userAlertRepository.findByIdAndUserKey(alertId, userKey)
                .orElseThrow(() -> new CustomException(ErrorCode.NOTIFICATION_NOT_FOUND));
        alert.markRead();
    }

    @Transactional
    public void hide(String userKey, Long alertId) {
        UserAlert alert = userAlertRepository.findByIdAndUserKey(alertId, userKey)
                .orElseThrow(() -> new CustomException(ErrorCode.NOTIFICATION_NOT_FOUND));
        alert.hide();
    }

    private String buildDigestBody(List<UserRecommendation> recommendations) {
        if (recommendations == null || recommendations.isEmpty()) {
            return "새로운 추천 정책이 도착했습니다.";
        }
        String titles = recommendations.stream()
                .limit(3)
                .map(rec -> rec.getService().getTitle())
                .collect(Collectors.joining(", "));
        return "%d건 추천: %s".formatted(recommendations.size(), titles);
    }

    private String buildDigestDeeplink(List<UserRecommendation> recommendations) {
        if (recommendations == null || recommendations.isEmpty()) {
            return "/mypage?tab=3";
        }
        Long serviceId = recommendations.get(0).getService().getId();
        return serviceId != null ? "/policies/" + serviceId : "/mypage?tab=3";
    }

    private String buildMetadataJson(Notification notification, List<UserRecommendation> recommendations) {
        String serviceIds = recommendations == null ? "" : recommendations.stream()
                .map(rec -> String.valueOf(rec.getService().getId()))
                .collect(Collectors.joining(","));
        return "{\"notificationId\":%d,\"serviceIds\":[%s]}".formatted(notification.getId(), serviceIds);
    }
}
