package com.example.welfare.notification.service;

import com.example.welfare.policy.entity.WelfareService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class DeadlineReminderContentService {

    private static final String DEFAULT_TITLE = "북마크한 정책 마감이 임박했어요";

    @Value("${app.base-url:http://localhost:5173}")
    private String appBaseUrl;

    public NotificationContent build(List<WelfareService> services, int days) {
        String deeplinkUrl = buildDeadlineDeeplink(services);
        return new NotificationContent(
                DEFAULT_TITLE,
                buildDeadlineBody(services, days),
                deeplinkUrl,
                toAbsoluteUrl(deeplinkUrl)
        );
    }

    private String buildDeadlineBody(List<WelfareService> services, int days) {
        if (services == null || services.isEmpty()) {
            return "북마크한 정책 중 마감이 임박한 항목이 있습니다.";
        }
        String titles = services.stream()
                .limit(3)
                .map(WelfareService::getTitle)
                .collect(Collectors.joining(", "));
        return "%d일 내 마감 %d건: %s".formatted(days, services.size(), titles);
    }

    private String buildDeadlineDeeplink(List<WelfareService> services) {
        if (services == null || services.isEmpty()) {
            return "/mypage?tab=3";
        }
        Long serviceId = services.get(0).getId();
        return serviceId != null ? "/policies/" + serviceId : "/mypage?tab=3";
    }

    private String toAbsoluteUrl(String deeplinkUrl) {
        String safePath = normalizeInternalPath(deeplinkUrl);
        if (appBaseUrl.endsWith("/") && safePath.startsWith("/")) {
            return appBaseUrl.substring(0, appBaseUrl.length() - 1) + safePath;
        }
        return appBaseUrl + safePath;
    }

    private String normalizeInternalPath(String deeplinkUrl) {
        if (deeplinkUrl == null || deeplinkUrl.isBlank() || !deeplinkUrl.startsWith("/") || deeplinkUrl.startsWith("//")) {
            return "/mypage?tab=3";
        }
        return deeplinkUrl;
    }
}
