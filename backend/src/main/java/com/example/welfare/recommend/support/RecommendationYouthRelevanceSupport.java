package com.example.welfare.recommend.support;

import com.example.welfare.collect.validation.RawFieldValidator;
import com.example.welfare.policy.entity.ServiceTag;
import com.example.welfare.policy.entity.WelfareService;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.OptionalInt;
import java.util.Set;
import java.util.stream.Stream;

@Component
public class RecommendationYouthRelevanceSupport {

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
    private static final Set<String> LOCAL_YOUTH_BRIDGE_CATEGORIES = Set.of(
            "주거",
            "금융·생활지원",
            "교육·직업훈련",
            "일자리",
            "가족·돌봄"
    );
    private static final Set<String> LOCAL_YOUTH_BRIDGE_INTEREST_THEMES = Set.of(
            "주거",
            "생활지원",
            "보호·돌봄",
            "교육",
            "일자리"
    );
    private static final Set<String> LOCAL_YOUTH_BRIDGE_KEYWORDS = Set.of(
            "주거지원",
            "월세보증금",
            "주거급여지원",
            "금융지원",
            "생활안정자금",
            "융자",
            "바우처",
            "돌봄서비스",
            "주간활동서비스",
            "안부확인서비스",
            "맞춤형상담서비스",
            "상담서비스",
            "교육지원",
            "인프라 구축"
    );

    public boolean isYouthRelevant(WelfareService service, List<ServiceTag> tags) {
        return computeYouthRelevant(service, tags);
    }

    public double relevanceBonus(WelfareService service, List<ServiceTag> tags) {
        return computeRelevanceBonus(service, tags);
    }

    public static boolean computeYouthRelevant(WelfareService service, List<ServiceTag> tags) {
        if (containsExplicitYouthSignal(service, tags)) {
            return true;
        }
        if (hasYouthFocusedLifeStage(service, tags)) {
            return true;
        }
        if (hasStructuredLocalYouthBridge(service, tags)) {
            return true;
        }
        if (isYouthFocusedAgeRange(service.getMinAge(), service.getMaxAge())) {
            return true;
        }
        if (tags != null && !tags.isEmpty()) {
            OptionalInt min = ageBound(tags, "COND_AGE_MIN_", true);
            OptionalInt max = ageBound(tags, "COND_AGE_MAX_", false);
            if (isYouthFocusedAgeRange(min.isPresent() ? min.getAsInt() : null, max.isPresent() ? max.getAsInt() : null)) {
                return true;
            }
        }
        return false;
    }

    public static double computeRelevanceBonus(WelfareService service, List<ServiceTag> tags) {
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

    private static boolean containsExplicitYouthSignal(WelfareService service, List<ServiceTag> tags) {
        if (Stream.of(
                service.getTitle(),
                service.getDescription(),
                service.getSupportContent(),
                service.getApplyMethodName()
        ).anyMatch(RecommendationYouthRelevanceSupport::containsYouthSignal)) {
            return true;
        }

        return tags != null && tags.stream().anyMatch(tag ->
                isExplicitYouthTagType(tag.getTagType()) && containsYouthSignal(tag.getTagValue())
        );
    }

    private static boolean hasYouthFocusedLifeStage(WelfareService service, List<ServiceTag> tags) {
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

    private static boolean hasStructuredLocalYouthBridge(WelfareService service, List<ServiceTag> tags) {
        if (service == null || service.getSourceType() != WelfareService.SourceType.BOKJIRO_LOCAL) {
            return false;
        }
        if (!hasAnyYouthLifeStage(service, tags)) {
            return false;
        }
        if (matchesAnyNormalized(service.getUnifiedCategory(), LOCAL_YOUTH_BRIDGE_CATEGORIES)) {
            return true;
        }
        if (matchesAnyTagValue(tags, ServiceTag.TagType.INTEREST_THEME, LOCAL_YOUTH_BRIDGE_INTEREST_THEMES)) {
            return true;
        }
        return matchesAnyTagValue(tags, ServiceTag.TagType.KEYWORD, LOCAL_YOUTH_BRIDGE_KEYWORDS);
    }

    private static boolean hasAnyYouthLifeStage(WelfareService service, List<ServiceTag> tags) {
        if (splitTokens(service.getLifeStage()).stream().anyMatch(RecommendationYouthRelevanceSupport::containsYouthSignal)) {
            return true;
        }
        if (tags == null || tags.isEmpty()) {
            return false;
        }
        return tags.stream()
                .filter(tag -> tag.getTagType() == ServiceTag.TagType.LIFE_STAGE)
                .map(ServiceTag::getTagValue)
                .anyMatch(RecommendationYouthRelevanceSupport::containsYouthSignal);
    }

    private static boolean isYouthOnlyOrFocusedStage(List<String> stages) {
        if (stages.isEmpty()) {
            return false;
        }
        boolean hasYouth = stages.stream().anyMatch(RecommendationYouthRelevanceSupport::containsYouthSignal);
        boolean hasBroadOtherStage = stages.stream()
                .anyMatch(stage -> BROAD_LIFE_STAGE_SIGNALS.stream().anyMatch(stage::contains));
        return hasYouth && !hasBroadOtherStage;
    }

    private static boolean isYouthFocusedAgeRange(Integer minAge, Integer maxAge) {
        if (minAge == null && maxAge == null) {
            return false;
        }
        if (maxAge == null) {
            return false;
        }
        int effectiveMin = minAge != null ? minAge : 0;
        return effectiveMin <= YOUTH_MAX_AGE && maxAge <= YOUTH_MAX_AGE && maxAge >= YOUTH_MIN_AGE;
    }

    private static boolean hasYouthFocusedAgeRange(WelfareService service, List<ServiceTag> tags) {
        if (isYouthFocusedAgeRange(service.getMinAge(), service.getMaxAge())) {
            return true;
        }

        if (tags == null || tags.isEmpty()) {
            return false;
        }

        OptionalInt min = ageBound(tags, "COND_AGE_MIN_", true);
        OptionalInt max = ageBound(tags, "COND_AGE_MAX_", false);
        return isYouthFocusedAgeRange(min.isPresent() ? min.getAsInt() : null, max.isPresent() ? max.getAsInt() : null);
    }

    private static OptionalInt ageBound(List<ServiceTag> tags, String prefix, boolean pickMax) {
        var stream = tags.stream()
                .filter(tag -> tag.getTagType() == ServiceTag.TagType.KEYWORD)
                .map(ServiceTag::getTagValue)
                .filter(value -> value.startsWith(prefix))
                .map(value -> value.substring(prefix.length()))
                .mapToInt(RecommendationYouthRelevanceSupport::safeInt)
                .filter(value -> value > 0);
        return pickMax ? stream.max() : stream.min();
    }

    private static boolean containsYouthSignal(String raw) {
        String normalized = RawFieldValidator.normalize(raw);
        if (normalized == null) {
            return false;
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        return YOUTH_SIGNALS.stream().anyMatch(signal -> lower.contains(signal.toLowerCase(Locale.ROOT)));
    }

    private static int safeInt(String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (Exception ignored) {
            return -1;
        }
    }

    private static List<String> splitTokens(String raw) {
        String normalized = RawFieldValidator.normalize(raw);
        if (normalized == null) {
            return List.of();
        }
        return Arrays.stream(normalized.split("[,/]"))
                .map(String::trim)
                .filter(token -> !token.isBlank())
                .toList();
    }

    private static boolean isExplicitYouthTagType(ServiceTag.TagType tagType) {
        return tagType == ServiceTag.TagType.TARGET_GROUP
                || tagType == ServiceTag.TagType.KEYWORD
                || tagType == ServiceTag.TagType.INTEREST_THEME;
    }

    private static boolean matchesAnyTagValue(List<ServiceTag> tags,
                                              ServiceTag.TagType tagType,
                                              Set<String> expectedValues) {
        if (tags == null || tags.isEmpty()) {
            return false;
        }
        return tags.stream()
                .filter(tag -> tag.getTagType() == tagType)
                .map(ServiceTag::getTagValue)
                .anyMatch(value -> matchesAnyNormalized(value, expectedValues));
    }

    private static boolean matchesAnyNormalized(String raw, Set<String> expectedValues) {
        String normalized = RawFieldValidator.normalize(raw);
        if (normalized == null) {
            return false;
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        return expectedValues.stream()
                .map(value -> value.toLowerCase(Locale.ROOT))
                .anyMatch(lower::contains);
    }
}
