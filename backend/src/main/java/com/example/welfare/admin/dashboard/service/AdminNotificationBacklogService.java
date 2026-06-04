package com.example.welfare.admin.dashboard.service;

import com.example.welfare.admin.dashboard.dto.AdminNotificationStaleHideRequest;
import com.example.welfare.admin.dashboard.dto.AdminNotificationStaleHideResponse;
import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.example.welfare.notification.entity.UserAlert;
import com.example.welfare.notification.repository.UserAlertRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AdminNotificationBacklogService {

    private final UserAlertRepository userAlertRepository;

    @Transactional
    public AdminNotificationStaleHideResponse hideStaleAlerts(AdminNotificationStaleHideRequest request) {
        UserAlert.UserAlertKind kind = normalizeKind(request.kind());
        String title = normalizeRequired(request.title(), 200);
        String deeplinkUrl = normalizeRequired(request.deeplinkUrl(), 500);
        int olderThanDays = normalizeDays(request.olderThanDays());
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime cutoff = now.minusDays(olderThanDays);

        int hiddenCount = userAlertRepository.hideUnreadByKindAndTitleAndDeeplinkUrlBefore(
                kind,
                title,
                deeplinkUrl,
                cutoff,
                now
        );

        return new AdminNotificationStaleHideResponse(
                hiddenCount,
                kind.name(),
                title,
                deeplinkUrl,
                olderThanDays
        );
    }

    private UserAlert.UserAlertKind normalizeKind(String raw) {
        String normalized = normalizeRequired(raw, 64);
        try {
            return UserAlert.UserAlertKind.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
    }

    private String normalizeRequired(String raw, int maxLength) {
        if (raw == null || raw.isBlank()) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        String normalized = raw.trim();
        if (normalized.length() > maxLength) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        return normalized;
    }

    private int normalizeDays(Integer raw) {
        if (raw == null || raw < 1 || raw > 365) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        return raw;
    }
}
