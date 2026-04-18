package com.example.welfare.recommend.service;

import com.example.welfare.collect.validation.RawFieldValidator;
import com.example.welfare.policy.entity.ServiceTag;
import com.example.welfare.policy.entity.WelfareService;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.OptionalInt;
import java.util.Set;
import java.util.stream.Stream;

@Service
public class YouthPolicyFilter {

    private static final int YOUTH_MIN_AGE = 18;
    private static final int YOUTH_MAX_AGE = 39;
    private static final double EXPLICIT_YOUTH_BONUS = 15.0;
    private static final double FOCUSED_LIFE_STAGE_BONUS = 8.0;
    private static final double AGE_RANGE_ONLY_BONUS = 3.0;

    private static final Set<String> YOUTH_SIGNALS = Set.of(
            "청년",
            "미취업청년",
            "취업준비생",
            "사회초년생",
            "대학생",
            "대학원생",
            "청년층"
    );

    private static final Set<String> BROAD_LIFE_STAGE_SIGNALS = Set.of(
            "영유아",
            "아동",
            "청소년",
            "중장년",
            "노년",
            "임신",
            "출산"
    );

    public boolean isYouthRelevant(WelfareService service, List<ServiceTag> tags) {
        if (containsExplicitYouthSignal(service, tags)) {
            return true;
        }

        if (hasYouthFocusedLifeStage(service, tags)) {
            return true;
        }

        if (isYouthFocusedAgeRange(service.getMinAge(), service.getMaxAge())) {
            return true;
        }

        if (tags != null && !tags.isEmpty()) {
            OptionalInt min = tags.stream()
                    .filter(tag -> tag.getTagType() == ServiceTag.TagType.KEYWORD)
                    .map(ServiceTag::getTagValue)
                    .filter(value -> value.startsWith("COND_AGE_MIN_"))
                    .map(value -> value.substring("COND_AGE_MIN_".length()))
                    .mapToInt(this::safeInt)
                    .filter(value -> value > 0)
                    .max();

            OptionalInt max = tags.stream()
                    .filter(tag -> tag.getTagType() == ServiceTag.TagType.KEYWORD)
                    .map(ServiceTag::getTagValue)
                    .filter(value -> value.startsWith("COND_AGE_MAX_"))
                    .map(value -> value.substring("COND_AGE_MAX_".length()))
                    .mapToInt(this::safeInt)
                    .filter(value -> value > 0)
                    .min();

            if (isYouthFocusedAgeRange(min.isPresent() ? min.getAsInt() : null, max.isPresent() ? max.getAsInt() : null)) {
                return true;
            }
        }

        return false;
    }

    public double relevanceBonus(WelfareService service, List<ServiceTag> tags) {
        boolean explicitYouthSignal = containsExplicitYouthSignal(service, tags);
        boolean focusedLifeStage = hasYouthFocusedLifeStage(service, tags);
        boolean youthFocusedAgeRange = hasYouthFocusedAgeRange(service, tags);

        double bonus = 0.0;
        if (explicitYouthSignal) {
            bonus += EXPLICIT_YOUTH_BONUS;
        }
        if (focusedLifeStage) {
            bonus += FOCUSED_LIFE_STAGE_BONUS;
        }
        if (!explicitYouthSignal && !focusedLifeStage && youthFocusedAgeRange) {
            bonus += AGE_RANGE_ONLY_BONUS;
        }
        return bonus;
    }

    private boolean containsExplicitYouthSignal(WelfareService service, List<ServiceTag> tags) {
        if (Stream.of(
                service.getTitle(),
                service.getDescription(),
                service.getSupportContent(),
                service.getApplyMethodName()
        ).anyMatch(this::containsYouthSignal)) {
            return true;
        }

        return tags != null && tags.stream().anyMatch(tag ->
                isExplicitYouthTagType(tag.getTagType()) && containsYouthSignal(tag.getTagValue())
        );
    }

    private boolean hasYouthFocusedLifeStage(WelfareService service, List<ServiceTag> tags) {
        if (isYouthOnlyOrFocusedStage(splitTokens(service.getLifeStage()))) {
            return true;
        }

        if (tags == null || tags.isEmpty()) {
            return false;
        }

        List<String> lifeStageTags = tags.stream()
                .filter(tag -> tag.getTagType() == ServiceTag.TagType.LIFE_STAGE)
                .map(ServiceTag::getTagValue)
                .toList();

        return isYouthOnlyOrFocusedStage(lifeStageTags);
    }

    private boolean isYouthOnlyOrFocusedStage(List<String> stages) {
        if (stages.isEmpty()) {
            return false;
        }
        boolean hasYouth = stages.stream().anyMatch(this::containsYouthSignal);
        boolean hasBroadOtherStage = stages.stream()
                .anyMatch(stage -> BROAD_LIFE_STAGE_SIGNALS.stream().anyMatch(stage::contains));
        return hasYouth && !hasBroadOtherStage;
    }

    private boolean isYouthFocusedAgeRange(Integer minAge, Integer maxAge) {
        if (minAge == null && maxAge == null) {
            return false;
        }
        if (maxAge == null) {
            return false;
        }
        int effectiveMin = minAge != null ? minAge : 0;
        return effectiveMin <= YOUTH_MAX_AGE && maxAge <= YOUTH_MAX_AGE && maxAge >= YOUTH_MIN_AGE;
    }

    private boolean hasYouthFocusedAgeRange(WelfareService service, List<ServiceTag> tags) {
        if (isYouthFocusedAgeRange(service.getMinAge(), service.getMaxAge())) {
            return true;
        }

        if (tags == null || tags.isEmpty()) {
            return false;
        }

        OptionalInt min = tags.stream()
                .filter(tag -> tag.getTagType() == ServiceTag.TagType.KEYWORD)
                .map(ServiceTag::getTagValue)
                .filter(value -> value.startsWith("COND_AGE_MIN_"))
                .map(value -> value.substring("COND_AGE_MIN_".length()))
                .mapToInt(this::safeInt)
                .filter(value -> value > 0)
                .max();

        OptionalInt max = tags.stream()
                .filter(tag -> tag.getTagType() == ServiceTag.TagType.KEYWORD)
                .map(ServiceTag::getTagValue)
                .filter(value -> value.startsWith("COND_AGE_MAX_"))
                .map(value -> value.substring("COND_AGE_MAX_".length()))
                .mapToInt(this::safeInt)
                .filter(value -> value > 0)
                .min();

        return isYouthFocusedAgeRange(min.isPresent() ? min.getAsInt() : null, max.isPresent() ? max.getAsInt() : null);
    }

    private boolean containsYouthSignal(String raw) {
        String normalized = RawFieldValidator.normalize(raw);
        if (normalized == null) {
            return false;
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        return YOUTH_SIGNALS.stream().anyMatch(signal -> lower.contains(signal.toLowerCase(Locale.ROOT)));
    }

    private int safeInt(String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (Exception ignored) {
            return -1;
        }
    }

    private List<String> splitTokens(String raw) {
        String normalized = RawFieldValidator.normalize(raw);
        if (normalized == null) {
            return List.of();
        }
        return Arrays.stream(normalized.split("[,/]"))
                .map(String::trim)
                .filter(token -> !token.isBlank())
                .toList();
    }

    private boolean isExplicitYouthTagType(ServiceTag.TagType tagType) {
        return tagType == ServiceTag.TagType.TARGET_GROUP
                || tagType == ServiceTag.TagType.KEYWORD
                || tagType == ServiceTag.TagType.INTEREST_THEME;
    }
}
