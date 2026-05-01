package com.example.welfare.recommend.support;

import com.example.welfare.policy.entity.ServiceTag;
import com.example.welfare.policy.entity.WelfareService;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * recommendation runtime 경계에서 공통으로 쓰는 deadline/signal helper를 모은다.
 */
public final class RecommendationRuntimeSupport {

    private RecommendationRuntimeSupport() {
    }

    public static boolean isDeadlineSoon(LocalDate applyEndDate, LocalDate today) {
        if (applyEndDate == null || today == null) {
            return false;
        }
        return !applyEndDate.isBefore(today)
                && applyEndDate.isBefore(today.plusDays(7));
    }

    public static boolean containsAnySignal(WelfareService service, List<ServiceTag> tags, String... signals) {
        if (signals == null) {
            return false;
        }
        for (String signal : signals) {
            if (containsSignal(service, tags, signal)) {
                return true;
            }
        }
        return false;
    }

    public static boolean containsSignal(WelfareService service, List<ServiceTag> tags, String signal) {
        String normalizedSignal = normalize(signal);
        if (normalizedSignal == null) {
            return false;
        }

        boolean inFields = Stream.of(
                        service == null ? null : service.getTitle(),
                        service == null ? null : service.getDescription(),
                        service == null ? null : service.getSupportContent(),
                        service == null ? null : service.getLifeStage())
                .map(RecommendationRuntimeSupport::normalize)
                .filter(value -> value != null && !value.isBlank())
                .anyMatch(value -> value.contains(normalizedSignal));
        if (inFields) {
            return true;
        }

        if (tags == null || tags.isEmpty()) {
            return false;
        }
        return tags.stream()
                .map(ServiceTag::getTagValue)
                .map(RecommendationRuntimeSupport::normalize)
                .filter(value -> value != null && !value.isBlank())
                .anyMatch(value -> value.contains(normalizedSignal));
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isBlank()) {
            return null;
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }
}
