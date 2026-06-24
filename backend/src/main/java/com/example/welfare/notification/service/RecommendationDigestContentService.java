package com.example.welfare.notification.service;

import com.example.welfare.recommend.entity.UserRecommendation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class RecommendationDigestContentService {

    private static final String DEFAULT_TITLE = "맞춤 정책 추천이 도착했어요";

    @Value("${app.base-url:http://localhost:5173}")
    private String appBaseUrl;

    public NotificationContent build(List<UserRecommendation> recommendations) {
        String deeplinkUrl = buildDigestDeeplink(recommendations);
        return new NotificationContent(
                DEFAULT_TITLE,
                buildDigestBody(recommendations),
                deeplinkUrl,
                toAbsoluteUrl(deeplinkUrl)
        );
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
