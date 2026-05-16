package com.example.welfare.notification.service;

import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.example.welfare.notification.entity.Notification;
import com.example.welfare.notification.entity.UserAlert;
import com.example.welfare.notification.entity.UserAlert.UserAlertKind;
import com.example.welfare.notification.entity.UserAlert.UserAlertStatus;
import com.example.welfare.notification.repository.UserAlertRepository;
import com.example.welfare.policy.entity.WelfareService;
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
    private final RecommendationDigestContentService recommendationDigestContentService;
    private final DeadlineReminderContentService deadlineReminderContentService;

    @Transactional
    public void createRecommendationDigestAlert(Notification notification, List<UserRecommendation> recommendations) {
        if (notification == null || notification.getDispatchKey() == null || notification.getUserKey() == null) {
            return;
        }
        if (userAlertRepository.findByEventKey(notification.getDispatchKey()).isPresent()) {
            return;
        }
        NotificationContent content = recommendationDigestContentService.build(recommendations);
        userAlertRepository.save(UserAlert.builder()
                .userKey(notification.getUserKey())
                .eventKey(notification.getDispatchKey())
                .kind(UserAlertKind.RECOMMENDATION_DIGEST)
                .status(UserAlertStatus.UNREAD)
                .title(content.title())
                .body(content.body())
                .deeplinkUrl(content.deeplinkUrl())
                .metadataJson(buildMetadataJson(notification, recommendations))
                .build());
    }

    @Transactional
    public void createDeadlineReminderAlert(Notification notification, List<WelfareService> services, int days) {
        if (notification == null || notification.getDispatchKey() == null || notification.getUserKey() == null) {
            return;
        }
        if (userAlertRepository.findByEventKey(notification.getDispatchKey()).isPresent()) {
            return;
        }
        NotificationContent content = deadlineReminderContentService.build(services, days);
        userAlertRepository.save(UserAlert.builder()
                .userKey(notification.getUserKey())
                .eventKey(notification.getDispatchKey())
                .kind(UserAlertKind.DEADLINE_REMINDER)
                .status(UserAlertStatus.UNREAD)
                .title(content.title())
                .body(content.body())
                .deeplinkUrl(content.deeplinkUrl())
                .metadataJson(buildDeadlineMetadataJson(notification, services, days))
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

    private String buildMetadataJson(Notification notification, List<UserRecommendation> recommendations) {
        String serviceIds = recommendations == null ? "" : recommendations.stream()
                .map(rec -> String.valueOf(rec.getService().getId()))
                .collect(Collectors.joining(","));
        return "{\"notificationId\":%d,\"serviceIds\":[%s]}".formatted(notification.getId(), serviceIds);
    }

    private String buildDeadlineMetadataJson(Notification notification, List<WelfareService> services, int days) {
        String serviceIds = services == null ? "" : services.stream()
                .map(service -> String.valueOf(service.getId()))
                .collect(Collectors.joining(","));
        return "{\"notificationId\":%d,\"days\":%d,\"serviceIds\":[%s]}".formatted(notification.getId(), days, serviceIds);
    }
}
