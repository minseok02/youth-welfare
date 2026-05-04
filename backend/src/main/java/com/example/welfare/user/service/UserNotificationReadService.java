package com.example.welfare.user.service;

import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.example.welfare.global.util.AesEncryptUtil;
import com.example.welfare.notification.dto.NotificationTarget;
import com.example.welfare.user.entity.User;
import com.example.welfare.user.repository.NotificationTargetAggregateReadModel;
import com.example.welfare.user.repository.NotificationTargetReadRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserNotificationReadService {

    private final ActiveUserReadService activeUserReadService;
    private final NotificationTargetReadRepository notificationTargetReadRepository;
    private final AesEncryptUtil aesEncryptUtil;

    @Transactional(readOnly = true)
    public List<NotificationTarget> getNotificationTargets(User.NotificationPeriod period) {
        return notificationTargetReadRepository.findNotificationTargetsByPeriod(period).stream()
                .map(this::toNotificationTarget)
                .filter(target -> StringUtils.hasText(target.email()))
                .toList();
    }

    @Transactional(readOnly = true)
    public String getNotificationEmail(Long userId) {
        String userKey = activeUserReadService.getActiveUserContext(userId).userKey();
        return getNotificationEmailByUserKey(userKey);
    }

    @Transactional(readOnly = true)
    public String getNotificationEmailByUserKey(String userKey) {
        activeUserReadService.getActiveUserByUserKey(userKey);
        String email = decryptNullable(notificationTargetReadRepository.findEncryptedEmailByUserKey(userKey)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND)));
        if (!StringUtils.hasText(email)) {
            throw new CustomException(ErrorCode.NOTIFICATION_SEND_FAILED);
        }
        return email;
    }

    private NotificationTarget toNotificationTarget(NotificationTargetAggregateReadModel row) {
        String email = decryptNullable(row.emailEnc());
        if (!StringUtils.hasText(email)) {
            log.warn("[UserNotificationReadService] 알림 대상 이메일 누락 userId={} userKey={}", row.userId(), row.userKey());
        }
        return new NotificationTarget(
                row.userId(),
                row.userKey(),
                email,
                User.NotificationPeriod.valueOf(row.notificationPeriod()),
                row.notificationMinScore(),
                row.displayCount()
        );
    }

    private String decryptNullable(String encryptedValue) {
        if (!StringUtils.hasText(encryptedValue)) {
            return null;
        }
        return aesEncryptUtil.decrypt(encryptedValue);
    }
}
