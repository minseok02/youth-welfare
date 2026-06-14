package com.example.welfare.recommend.support;

import com.example.welfare.policy.entity.ServiceTag;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.recommend.dto.RecommendationCandidateProjection;
import com.example.welfare.recommend.dto.RecommendationUserSnapshot;
import com.example.welfare.user.service.UserProfileStandardCodeValidator;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 추천 매칭 정책 중 beneficiary / special-target / target-group fallback 규칙을 공통화한다.
 */
public final class RecommendationMatchingSupport {

    private static final Set<String> SINGLE_PERSON_SIGNALS = Set.of("1인", "1인가구", "one_person", "single");
    private static final Set<String> SINGLE_PARENT_SIGNALS = Set.of("한부모", "single_parent");
    private static final Set<String> GRANDPARENT_SIGNALS = Set.of("조손", "grandparent");
    private static final Set<String> MULTI_CHILD_SIGNALS = Set.of("다자녀", "multi_child", "large_family");
    private static final Set<String> HOUSING_ANCHORS = Set.of(
            "주거", "주택", "월세", "전세", "보증금", "임대", "임차료", "임대료",
            "기숙사", "아파트", "다가구", "다세대", "단독주택", "연립주택", "공관"
    );
    private static final Map<String, Set<String>> HOUSE_TENURE_SIGNALS = Map.of(
            "1", Set.of("자가"),
            "2", Set.of("전세", "전월세", "전세임대", "보증금"),
            "3", Set.of("월세", "전월세", "월세보증금", "임대료", "임차료"),
            "4", Set.of("임대", "임대주택", "공공임대주택", "매입임대", "전세임대"),
            "9", Set.of("주거")
    );
    private static final Map<String, Set<String>> HOUSING_TYPE_SIGNALS = Map.of(
            "1", Set.of("단독주택"),
            "2", Set.of("다중주택"),
            "3", Set.of("다가구주택", "다가구"),
            "4", Set.of("아파트"),
            "5", Set.of("연립주택", "연립"),
            "6", Set.of("다세대주택", "다세대"),
            "7", Set.of("기숙사"),
            "99", Set.of("공관")
    );

    private RecommendationMatchingSupport() {
    }

    public static boolean targetGroupMatches(RecommendationUserSnapshot user,
                                             List<String> targetGroupValues,
                                             RecommendationCandidateProjection projection) {
        if ((targetGroupValues == null || targetGroupValues.isEmpty()) && !beneficiaryBucketMatches(user, projection)) {
            return false;
        }

        boolean legacyMatch = targetGroupValues != null && targetGroupValues.stream().anyMatch(val -> {
            if (user.employmentStatus() != null) {
                String employment = user.employmentStatus();
                if (val.contains("미취업") && employment.contains("미취업")) return true;
                if (val.contains("취업준비") && (employment.contains("취업준비") || employment.contains("구직"))) return true;
                if (val.contains("재직") && employment.contains("재직")) return true;
                if (val.contains("자영업") && employment.contains("자영업")) return true;
                if (val.contains("프리랜서") && employment.contains("프리랜서")) return true;
            }

            if (user.householdType() != null) {
                String household = normalize(user.householdType());
                if (val.contains("1인가구") && containsAnySignal(household, SINGLE_PERSON_SIGNALS)) return true;
                if (val.contains("한부모") && containsAnySignal(household, SINGLE_PARENT_SIGNALS)) return true;
                if (val.contains("다자녀") && containsAnySignal(household, MULTI_CHILD_SIGNALS)) return true;
            }

            if (user.incomeLevel() != null) {
                if (val.contains("저소득") && user.incomeLevel() <= 3) return true;
                if (val.contains("기초생활") && user.incomeLevel() <= 1) return true;
            }

            if (hasApplicableProfileCode(user.basicLivingRecipientTypeCode()) && val.contains("기초생활")) {
                return true;
            }
            if (hasApplicableProfileCode(user.disabilityGradeCode()) && val.contains("장애")) {
                return true;
            }

            return false;
        });

        return legacyMatch || beneficiaryBucketMatches(user, projection);
    }

    public static boolean beneficiaryBucketMatches(RecommendationUserSnapshot user,
                                                   RecommendationCandidateProjection projection) {
        if (projection == null || projection.targetGroupBuckets().isEmpty()) {
            return false;
        }
        if (!projection.targetGroupBuckets().contains(RecommendationProjectionHeuristicSupport.BENEFICIARY_SUPPORT_BUCKET)) {
            return false;
        }
        if (user.incomeLevel() == null) {
            return false;
        }

        if (projection.beneficiaryTerms().contains("기초생활수급자")
                && (hasApplicableProfileCode(user.basicLivingRecipientTypeCode()) || user.incomeLevel() <= 1)) {
            return true;
        }
        return projection.beneficiaryTerms().contains("차상위계층") && user.incomeLevel() <= 3;
    }

    public static boolean specialTargetMatches(RecommendationUserSnapshot user,
                                               Set<String> targetTypes,
                                               WelfareService service,
                                               List<ServiceTag> tags,
                                               RecommendationCandidateProjection projection) {
        return specialAudienceMatchedByTargetTypes(targetTypes, service, tags, projection)
                || specialAudienceMatchedByUserProfile(user, service, tags, projection);
    }

    public static boolean housingProfileMatches(RecommendationUserSnapshot user,
                                                WelfareService service,
                                                List<ServiceTag> tags,
                                                RecommendationCandidateProjection projection) {
        if ((user.houseTenureCode() == null || user.houseTenureCode().isBlank())
                && (user.housingTypeCode() == null || user.housingTypeCode().isBlank())) {
            return false;
        }

        String haystack = buildHousingHaystack(service, tags, projection);
        if (haystack.isBlank() || HOUSING_ANCHORS.stream().noneMatch(haystack::contains)) {
            return false;
        }

        Set<String> houseTenureSignals = user.houseTenureCode() == null
                ? null
                : HOUSE_TENURE_SIGNALS.get(user.houseTenureCode());
        Set<String> housingTypeSignals = user.housingTypeCode() == null
                ? null
                : HOUSING_TYPE_SIGNALS.get(user.housingTypeCode());

        return hasMappedSignal(haystack, houseTenureSignals)
                || hasMappedSignal(haystack, housingTypeSignals);
    }

    public static boolean hasSpecialTargetSignal(WelfareService service,
                                                 List<ServiceTag> tags,
                                                 RecommendationCandidateProjection projection) {
        if (projection != null && !projection.specialTargetBuckets().isEmpty()) {
            return true;
        }
        return RecommendationRuntimeSupport.containsAnySignal(service, tags,
                "장애", "농어촌", "농촌", "어촌", "자립준비", "보호종료",
                "가족돌봄", "다문화", "북한이탈", "한부모", "조손", "보훈",
                "현역병", "병역");
    }

    private static boolean specialAudienceMatchedByTargetTypes(Set<String> targetTypes,
                                                               WelfareService service,
                                                               List<ServiceTag> tags,
                                                               RecommendationCandidateProjection projection) {
        if (targetTypes == null || targetTypes.isEmpty()) {
            return false;
        }

        if (projection != null && !projection.specialTargetBuckets().isEmpty()) {
            return targetTypes.stream().anyMatch(projection.specialTargetBuckets()::contains);
        }

        if (targetTypes.stream().anyMatch(type -> RecommendationRuntimeSupport.containsSignal(service, tags, type))) {
            return true;
        }
        if (targetTypes.contains(RecommendationProjectionHeuristicSupport.SPECIAL_TARGET_SELF_RELIANCE)
                && RecommendationRuntimeSupport.containsAnySignal(service, tags, "자립준비", "보호종료")) {
            return true;
        }
        if (targetTypes.contains(RecommendationProjectionHeuristicSupport.SPECIAL_TARGET_RURAL)
                && RecommendationRuntimeSupport.containsAnySignal(service, tags, "농어촌", "농촌", "어촌")) {
            return true;
        }
        return false;
    }

    private static boolean specialAudienceMatchedByUserProfile(RecommendationUserSnapshot user,
                                                               WelfareService service,
                                                               List<ServiceTag> tags,
                                                               RecommendationCandidateProjection projection) {
        if (user.incomeLevel() != null && user.incomeLevel() <= 3
                && RecommendationRuntimeSupport.containsAnySignal(service, tags, "저소득", "기초생활")) {
            return true;
        }
        if (user.householdType() != null) {
            String household = normalize(user.householdType());
            if (projection != null
                    && containsAnySignal(household, SINGLE_PARENT_SIGNALS)
                    && projection.specialTargetBuckets().contains(RecommendationProjectionHeuristicSupport.SPECIAL_TARGET_SINGLE_PARENT)) {
                return true;
            }
            if (projection != null
                    && containsAnySignal(household, GRANDPARENT_SIGNALS)
                    && projection.specialTargetBuckets().contains(RecommendationProjectionHeuristicSupport.SPECIAL_TARGET_GRANDPARENT)) {
                return true;
            }
            if (containsAnySignal(household, SINGLE_PARENT_SIGNALS)
                    && RecommendationRuntimeSupport.containsAnySignal(service, tags, RecommendationProjectionHeuristicSupport.SPECIAL_TARGET_SINGLE_PARENT)) {
                return true;
            }
            if (containsAnySignal(household, GRANDPARENT_SIGNALS)
                    && RecommendationRuntimeSupport.containsAnySignal(service, tags, RecommendationProjectionHeuristicSupport.SPECIAL_TARGET_GRANDPARENT)) {
                return true;
            }
        }
        if (hasApplicableProfileCode(user.disabilityGradeCode())) {
            if (projection != null
                    && projection.specialTargetBuckets().contains(RecommendationProjectionHeuristicSupport.SPECIAL_TARGET_DISABILITY)) {
                return true;
            }
            if (RecommendationRuntimeSupport.containsAnySignal(service, tags,
                    RecommendationProjectionHeuristicSupport.SPECIAL_TARGET_DISABILITY,
                    "장애인")) {
                return true;
            }
        }
        return false;
    }

    /**
     * 표준코드 보유 여부. null/빈 값은 미응답, "해당하지 않음"(NOT_APPLICABLE) sentinel은 명시적 미보유이므로
     * 둘 다 매칭에서 미보유로 본다. 실제 코드값이 있을 때만 보유로 판정한다.
     */
    private static boolean hasApplicableProfileCode(String code) {
        return code != null
                && !code.isBlank()
                && !UserProfileStandardCodeValidator.NOT_APPLICABLE_CODE.equals(code);
    }

    private static boolean containsAnySignal(String normalizedHousehold, Set<String> signals) {
        if (normalizedHousehold == null || normalizedHousehold.isBlank()) {
            return false;
        }
        return signals.stream().anyMatch(normalizedHousehold::contains);
    }

    private static boolean hasMappedSignal(String haystack, Set<String> signals) {
        return signals != null && signals.stream()
                .map(RecommendationMatchingSupport::normalize)
                .filter(value -> value != null && !value.isBlank())
                .anyMatch(haystack::contains);
    }

    private static String buildHousingHaystack(WelfareService service,
                                               List<ServiceTag> tags,
                                               RecommendationCandidateProjection projection) {
        LinkedHashSet<String> texts = new LinkedHashSet<>();
        if (service != null) {
            texts.add(service.getTitle());
            texts.add(service.getDescription());
            texts.add(service.getSupportContent());
            texts.add(service.getApplyMethodName());
            texts.add(service.getUnifiedCategory());
        }
        if (projection != null) {
            texts.add(projection.title());
            texts.add(projection.summary());
            texts.add(projection.gov24ServiceFieldLabel());
            texts.addAll(projection.keywordTags());
            texts.addAll(projection.interestThemes());
            texts.addAll(projection.targetGroupsRaw());
        }
        if (tags != null && !tags.isEmpty()) {
            tags.stream()
                    .map(ServiceTag::getTagValue)
                    .forEach(texts::add);
        }

        return texts.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(RecommendationMatchingSupport::normalize)
                .reduce("", (left, right) -> left + " " + right)
                .trim();
    }

    private static String normalize(String raw) {
        return raw == null ? null : raw.trim().toLowerCase(Locale.ROOT).replace(" ", "");
    }
}
