package com.example.welfare.recommend.service;

import com.example.welfare.policy.entity.ServiceTag;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.repository.ServiceTagRepository;
import com.example.welfare.recommend.dto.PriorityPreference;
import com.example.welfare.recommend.dto.RecommendationCandidateProjection;
import com.example.welfare.recommend.dto.RecommendationUserSnapshot;
import com.example.welfare.recommend.dto.RetrievedRecommendationCandidates;
import com.example.welfare.recommend.dto.ScoredCandidate;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import java.util.stream.Collectors;

/**
 * Rule 기반 점수 계산
 * rule_base_score → 우선순위 가중치 적용 → rule_weighted_score
 *
 * 태그는 후보 전체를 findByServiceIdIn으로 한 번에 로드해 Map으로 사용 (N+1 방지)
 */
@Service
@RequiredArgsConstructor
public class RuleScoringService {

    private static final String BENEFICIARY_SUPPORT_BUCKET = "BENEFICIARY_SUPPORT";
    private static final String SPECIAL_TARGET_RURAL = "농어촌";
    private static final String SPECIAL_TARGET_SELF_RELIANCE = "자립준비청년";
    private static final String SPECIAL_TARGET_SINGLE_PARENT = "한부모";
    private static final String SPECIAL_TARGET_GRANDPARENT = "조손";
    private static final String COMPAT_OTHER = "기타";
    private static final String EDUCATION_MAJOR = "교육";
    private static final String EDUCATION_PRIORITY_CODE = "EDUCATION";
    private static final double SPECIAL_TARGET_MATCH_BONUS = 12.0;
    private static final double SPECIAL_TARGET_MISMATCH_PENALTY = 8.0;

    private final ServiceTagRepository serviceTagRepository;
    private final PriorityMatcher priorityMatcher;
    private final YouthPolicyFilter youthPolicyFilter;

    @Value("${recommend.priority.education-canonical-bonus.enabled:false}")
    private boolean educationCanonicalBonusEnabled;

    public List<ScoredCandidate> score(List<WelfareService> candidates, RecommendationUserSnapshot user) {
        return score(candidates, Map.of(), user);
    }

    public List<ScoredCandidate> score(RetrievedRecommendationCandidates retrieved, RecommendationUserSnapshot user) {
        return score(retrieved.candidates(), retrieved.projections(), user);
    }

    private List<ScoredCandidate> score(List<WelfareService> candidates,
                                        Map<Long, RecommendationCandidateProjection> projections,
                                        RecommendationUserSnapshot user) {
        Set<String> interestFields = user.interestFields().stream().collect(Collectors.toSet());
        Set<String> targetTypes = user.targetTypes().stream().collect(Collectors.toSet());

        // 후보 전체 태그 한 번에 로드 (N+1 방지)
        List<Long> serviceIds = candidates.stream()
                .map(WelfareService::getId)
                .collect(Collectors.toList());
        Map<Long, List<ServiceTag>> tagsByServiceId = serviceTagRepository
                .findByServiceIdIn(serviceIds)
                .stream()
                .collect(Collectors.groupingBy(t -> t.getService().getId()));

        return candidates.stream()
                .map(service -> {
                    List<ServiceTag> tags = tagsByServiceId.getOrDefault(service.getId(), List.of());
                    RecommendationCandidateProjection projection = projections.get(service.getId());
                    double base = calcBaseScore(service, user, interestFields, targetTypes, tags, projection);
                    double weighted = applyPriorityWeight(base, service, projection, user.priorities());
                    // 특수 대상 신호가 있지만 사용자와 불일치한 경우 플래그 설정
                    boolean mismatch = !specialTargetMatches(user, targetTypes, service, tags, projection)
                            && hasSpecialTargetSignal(service, tags, projection);

                    return ScoredCandidate.builder()
                            .service(service)
                            .projection(projection)
                            .ruleBaseScore(base)
                            .ruleWeightedScore(weighted)
                            .hasSpecialTargetMismatch(mismatch)
                            .build();
                })
                .collect(Collectors.toList());
    }

    private double calcBaseScore(WelfareService service, RecommendationUserSnapshot user,
                                  Set<String> interestFields, Set<String> targetTypes, List<ServiceTag> tags,
                                  RecommendationCandidateProjection projection) {
        double score = 0;

        // 청년 신호가 강한 정책을 우선 노출하고, 나이만 겹치는 정책은 뒤로 보낸다.
        score += audienceRelevanceBonus(service, tags, projection);

        // 관심분야 일치: INTEREST_THEME 태그 ↔ 유저 INTEREST_FIELD
        if (interestThemeMatches(interestFields, tags, projection)) score += 15;

        // 관심분야 일치: KEYWORD 태그 ↔ 유저 INTEREST_FIELD (온통청년 보완)
        if (keywordMatches(interestFields, tags, projection)) score += 10;

        // 대상유형 일치: TARGET_GROUP 태그 ↔ 유저 취업상태·가구유형·소득분위
        if (targetGroupMatches(user, tags, projection)) score += 10;

        if (specialTargetMatches(user, targetTypes, service, tags, projection)) score += SPECIAL_TARGET_MATCH_BONUS;
        else if (hasSpecialTargetSignal(service, tags, projection)) score -= SPECIAL_TARGET_MISMATCH_PENALTY;

        // 마감임박 — apply_end_date 기준 7일 이내
        if (isDeadlineSoon(service, projection)) score += 5;

        return score;
    }

    /**
     * INTEREST_THEME 태그 ↔ 유저 관심분야 매칭 (복지로 정책)
     * 태그 없으면 건너뜀 (0점 부여하지 않음 — CLAUDE.md 원칙)
     */
    private boolean interestThemeMatches(Set<String> interestFields,
                                         List<ServiceTag> tags,
                                         RecommendationCandidateProjection projection) {
        if (interestFields.isEmpty()) return false;
        boolean legacyMatch = tags.stream()
                .filter(t -> t.getTagType() == ServiceTag.TagType.INTEREST_THEME)
                .anyMatch(t -> interestFields.stream()
                        .anyMatch(field -> t.getTagValue().contains(field) || field.contains(t.getTagValue())));
        if (legacyMatch) {
            return true;
        }
        if (projection == null || projection.interestThemes().isEmpty()) {
            return false;
        }
        return projection.interestThemes().stream()
                .anyMatch(value -> matchesInterestField(interestFields, value));
    }

    /**
     * KEYWORD 태그 ↔ 유저 관심분야 매칭 (온통청년 정책 보완)
     * plcyKywdNm에서 생성된 KEYWORD 태그와 유저 관심분야 비교
     */
    private boolean keywordMatches(Set<String> interestFields,
                                   List<ServiceTag> tags,
                                   RecommendationCandidateProjection projection) {
        if (interestFields.isEmpty()) return false;
        boolean legacyMatch = tags.stream()
                .filter(t -> t.getTagType() == ServiceTag.TagType.KEYWORD)
                .anyMatch(t -> interestFields.stream()
                        .anyMatch(field -> t.getTagValue().contains(field) || field.contains(t.getTagValue())));
        if (legacyMatch) {
            return true;
        }
        if (projection == null || projection.keywordTags().isEmpty()) {
            return false;
        }
        return projection.keywordTags().stream()
                .anyMatch(value -> matchesInterestField(interestFields, value));
    }

    /**
     * TARGET_GROUP 태그 ↔ 유저 취업상태·가구유형·소득분위 매칭 (복지로 정책)
     * 태그 없으면 건너뜀 (0점 부여하지 않음)
     *
     * 복지로 trgterIndvdlArray 실제 값 예시:
     * "미취업청년", "저소득층", "1인가구", "청년", "대학생", "취업준비생"
     */
    private boolean targetGroupMatches(RecommendationUserSnapshot user,
                                       List<ServiceTag> tags,
                                       RecommendationCandidateProjection projection) {
        List<String> targetGroupValues = tags.stream()
                .filter(t -> t.getTagType() == ServiceTag.TagType.TARGET_GROUP)
                .map(ServiceTag::getTagValue)
                .collect(Collectors.toList());

        if (projection != null && !projection.targetGroupsRaw().isEmpty()) {
            targetGroupValues = Stream.concat(targetGroupValues.stream(), projection.targetGroupsRaw().stream())
                    .distinct()
                    .toList();
        }

        if (targetGroupValues.isEmpty() && !beneficiaryBucketMatches(user, projection)) return false;

        boolean legacyMatch = targetGroupValues.stream().anyMatch(val -> {
            // 취업상태 매핑
            if (user.employmentStatus() != null) {
                String emp = user.employmentStatus();
                if (val.contains("미취업") && emp.contains("미취업")) return true;
                if (val.contains("취업준비") && (emp.contains("취업준비") || emp.contains("구직"))) return true;
                if (val.contains("재직") && emp.contains("재직")) return true;
                if (val.contains("자영업") && emp.contains("자영업")) return true;
                if (val.contains("프리랜서") && emp.contains("프리랜서")) return true;
            }

            // 가구유형 매핑
            if (user.householdType() != null) {
                String household = user.householdType();
                if (val.contains("1인가구") && household.contains("1인")) return true;
                if (val.contains("한부모") && household.contains("한부모")) return true;
                if (val.contains("다자녀") && household.contains("다자녀")) return true;
            }

            // 소득분위 매핑 (저소득층 = 3분위 이하로 판단)
            if (user.incomeLevel() != null) {
                if (val.contains("저소득") && user.incomeLevel() <= 3) return true;
                if (val.contains("기초생활") && user.incomeLevel() <= 1) return true;
            }

            return false;
        });

        return legacyMatch || beneficiaryBucketMatches(user, projection);
    }

    private boolean beneficiaryBucketMatches(RecommendationUserSnapshot user,
                                             RecommendationCandidateProjection projection) {
        if (projection == null || projection.targetGroupBuckets().isEmpty()) {
            return false;
        }
        if (!projection.targetGroupBuckets().contains(BENEFICIARY_SUPPORT_BUCKET)) {
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

    private boolean isDeadlineSoon(WelfareService service, RecommendationCandidateProjection projection) {
        if (service.getApplyEndDate() == null) return false;
        LocalDate today = LocalDate.now();
        boolean withinWindow = !service.getApplyEndDate().isBefore(today)
                && service.getApplyEndDate().isBefore(today.plusDays(7));
        if (!withinWindow) {
            return false;
        }

        // canonical fact key 연결은 이번 단계에서 helper 경계만 먼저 고정하고,
        // bonus 규칙은 legacy apply_end_date 의미를 그대로 유지한다.
        return projection == null || projection.factKeys().isEmpty() || hasDeadlineFactKey(projection);
    }

    private boolean hasDeadlineFactKey(RecommendationCandidateProjection projection) {
        return projection.factKeys().stream().anyMatch(key -> key.endsWith("APPLY_END_DATE"));
    }

    private double audienceRelevanceBonus(WelfareService service,
                                          List<ServiceTag> tags,
                                          RecommendationCandidateProjection projection) {
        if (projection != null && projection.audienceRelevanceBonus() > 0) {
            return projection.audienceRelevanceBonus();
        }
        return youthPolicyFilter.relevanceBonus(service, tags);
    }

    private boolean specialTargetMatches(RecommendationUserSnapshot user,
                                         Set<String> targetTypes,
                                         WelfareService service,
                                         List<ServiceTag> tags,
                                         RecommendationCandidateProjection projection) {
        return specialAudienceMatchedByTargetTypes(targetTypes, service, tags, projection)
                || specialAudienceMatchedByUserProfile(user, service, tags, projection);
    }

    private boolean hasSpecialTargetSignal(WelfareService service,
                                           List<ServiceTag> tags,
                                           RecommendationCandidateProjection projection) {
        if (projection != null && !projection.specialTargetBuckets().isEmpty()) {
            return true;
        }
        return containsAnySignal(service, tags,
                "장애", "농어촌", "농촌", "어촌", "자립준비", "보호종료",
                "가족돌봄", "다문화", "북한이탈", "한부모", "조손", "보훈",
                "현역병", "병역");
    }

    private boolean specialAudienceMatchedByTargetTypes(Set<String> targetTypes,
                                                        WelfareService service,
                                                        List<ServiceTag> tags,
                                                        RecommendationCandidateProjection projection) {
        if (targetTypes.isEmpty()) return false;

        if (projection != null && !projection.specialTargetBuckets().isEmpty()) {
            if (targetTypes.stream().anyMatch(projection.specialTargetBuckets()::contains)) {
                return true;
            }
            if (targetTypes.contains(SPECIAL_TARGET_SELF_RELIANCE)
                    && projection.specialTargetBuckets().contains(SPECIAL_TARGET_SELF_RELIANCE)) {
                return true;
            }
            return targetTypes.contains(SPECIAL_TARGET_RURAL)
                    && projection.specialTargetBuckets().contains(SPECIAL_TARGET_RURAL);
        }

        if (targetTypes.stream().anyMatch(type -> containsSignal(service, tags, type))) {
            return true;
        }
        if (targetTypes.contains(SPECIAL_TARGET_SELF_RELIANCE) && containsAnySignal(service, tags, "자립준비", "보호종료")) {
            return true;
        }
        if (targetTypes.contains(SPECIAL_TARGET_RURAL) && containsAnySignal(service, tags, "농어촌", "농촌", "어촌")) {
            return true;
        }
        return false;
    }

    private boolean specialAudienceMatchedByUserProfile(RecommendationUserSnapshot user,
                                                        WelfareService service,
                                                        List<ServiceTag> tags,
                                                        RecommendationCandidateProjection projection) {
        if (user.incomeLevel() != null && user.incomeLevel() <= 3
                && containsAnySignal(service, tags, "저소득", "기초생활")) {
            return true;
        }
        if (user.householdType() != null) {
            String household = user.householdType();
            if (projection != null && household.contains(SPECIAL_TARGET_SINGLE_PARENT)
                    && projection.specialTargetBuckets().contains(SPECIAL_TARGET_SINGLE_PARENT)) {
                return true;
            }
            if (projection != null && household.contains(SPECIAL_TARGET_GRANDPARENT)
                    && projection.specialTargetBuckets().contains(SPECIAL_TARGET_GRANDPARENT)) {
                return true;
            }
            if (household.contains(SPECIAL_TARGET_SINGLE_PARENT) && containsAnySignal(service, tags, SPECIAL_TARGET_SINGLE_PARENT)) {
                return true;
            }
            if (household.contains(SPECIAL_TARGET_GRANDPARENT) && containsAnySignal(service, tags, SPECIAL_TARGET_GRANDPARENT)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsAnySignal(WelfareService service, List<ServiceTag> tags, String... signals) {
        for (String signal : signals) {
            if (containsSignal(service, tags, signal)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsSignal(WelfareService service, List<ServiceTag> tags, String signal) {
        String normalizedSignal = normalize(signal);
        if (normalizedSignal == null) return false;

        boolean inFields = Stream.of(
                        service.getTitle(),
                        service.getDescription(),
                        service.getSupportContent(),
                        service.getLifeStage())
                .map(this::normalize)
                .filter(value -> value != null && !value.isBlank())
                .anyMatch(value -> value.contains(normalizedSignal));
        if (inFields) {
            return true;
        }

        return tags.stream()
                .map(ServiceTag::getTagValue)
                .map(this::normalize)
                .filter(value -> value != null && !value.isBlank())
                .anyMatch(value -> value.contains(normalizedSignal));
    }

    private String normalize(String value) {
        if (value == null) return null;
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private boolean matchesInterestField(Set<String> interestFields, String value) {
        return interestFields.stream()
                .anyMatch(field -> value.contains(field) || field.contains(value));
    }

    /**
     * 우선순위 가중치 — 복수 매칭 시 최고 배율 하나만 적용 (이중합산 방지)
     */
    private double applyPriorityWeight(double base,
                                       WelfareService service,
                                       RecommendationCandidateProjection projection,
                                       List<PriorityPreference> priorities) {
        if (priorities.isEmpty()) return base;

        double maxWeight = priorities.stream()
                .filter(p -> priorityMatcher.matches(p, service, projection)
                        || matchesEducationCanonicalPriorityExperiment(p, projection))
                .mapToDouble(PriorityPreference::weight)
                .max()
                .orElse(1.0);

        return base * maxWeight;
    }

    private boolean matchesEducationCanonicalPriorityExperiment(PriorityPreference priority,
                                                                RecommendationCandidateProjection projection) {
        if (!educationCanonicalBonusEnabled || priority == null || projection == null) {
            return false;
        }
        if (!EDUCATION_PRIORITY_CODE.equals(priority.code())) {
            return false;
        }
        return projection.educationPriorityBoostEligible()
                || (COMPAT_OTHER.equals(projection.unifiedCategoryCompat())
                && EDUCATION_MAJOR.equals(projection.youthMajorLabel()));
    }
}
