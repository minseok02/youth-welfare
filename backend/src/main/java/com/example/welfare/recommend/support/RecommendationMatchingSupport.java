package com.example.welfare.recommend.support;

import com.example.welfare.policy.entity.ServiceTag;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.recommend.dto.RecommendationCandidateProjection;
import com.example.welfare.recommend.dto.RecommendationUserSnapshot;

import java.util.List;
import java.util.Set;

/**
 * 추천 매칭 정책 중 beneficiary / special-target / target-group fallback 규칙을 공통화한다.
 */
public final class RecommendationMatchingSupport {

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
                String household = user.householdType();
                if (val.contains("1인가구") && household.contains("1인")) return true;
                if (val.contains("한부모") && household.contains("한부모")) return true;
                if (val.contains("다자녀") && household.contains("다자녀")) return true;
            }

            if (user.incomeLevel() != null) {
                if (val.contains("저소득") && user.incomeLevel() <= 3) return true;
                if (val.contains("기초생활") && user.incomeLevel() <= 1) return true;
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

        if (projection.beneficiaryTerms().contains("기초생활수급자") && user.incomeLevel() <= 1) {
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
            String household = user.householdType();
            if (projection != null
                    && household.contains(RecommendationProjectionHeuristicSupport.SPECIAL_TARGET_SINGLE_PARENT)
                    && projection.specialTargetBuckets().contains(RecommendationProjectionHeuristicSupport.SPECIAL_TARGET_SINGLE_PARENT)) {
                return true;
            }
            if (projection != null
                    && household.contains(RecommendationProjectionHeuristicSupport.SPECIAL_TARGET_GRANDPARENT)
                    && projection.specialTargetBuckets().contains(RecommendationProjectionHeuristicSupport.SPECIAL_TARGET_GRANDPARENT)) {
                return true;
            }
            if (household.contains(RecommendationProjectionHeuristicSupport.SPECIAL_TARGET_SINGLE_PARENT)
                    && RecommendationRuntimeSupport.containsAnySignal(service, tags, RecommendationProjectionHeuristicSupport.SPECIAL_TARGET_SINGLE_PARENT)) {
                return true;
            }
            if (household.contains(RecommendationProjectionHeuristicSupport.SPECIAL_TARGET_GRANDPARENT)
                    && RecommendationRuntimeSupport.containsAnySignal(service, tags, RecommendationProjectionHeuristicSupport.SPECIAL_TARGET_GRANDPARENT)) {
                return true;
            }
        }
        return false;
    }
}
