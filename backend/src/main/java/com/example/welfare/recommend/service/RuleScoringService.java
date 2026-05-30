package com.example.welfare.recommend.service;

import com.example.welfare.policy.entity.ServiceTag;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.support.CompatCategorySupport;
import com.example.welfare.recommend.dto.PriorityPreference;
import com.example.welfare.recommend.dto.RecommendationCandidateProjection;
import com.example.welfare.recommend.dto.RecommendationUserSnapshot;
import com.example.welfare.recommend.dto.RetrievedRecommendationCandidates;
import com.example.welfare.recommend.dto.ScoredCandidate;
import com.example.welfare.recommend.repository.RecommendationCandidateReadRepository;
import com.example.welfare.recommend.support.Gov24RecommendationScoringSupport;
import com.example.welfare.recommend.support.RecommendationMatchingSupport;
import com.example.welfare.recommend.support.RecommendationYouthRelevanceSupport;
import com.example.welfare.recommend.support.RecommendationRuntimeSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Rule 기반 점수 계산
 * rule_base_score → 우선순위 가중치 적용 → rule_weighted_score
 *
 * 태그는 후보 전체를 findByServiceIdIn으로 한 번에 로드해 Map으로 사용 (N+1 방지)
 */
@Service
@RequiredArgsConstructor
public class RuleScoringService {

    private static final String EDUCATION_PRIORITY_CODE = "EDUCATION";
    private static final Set<String> CATEGORY_PRIORITY_CODES = Set.of(
            "HOUSING",
            "JOB",
            "EDUCATION",
            "FINANCE",
            "CULTURE",
            "PARTICIPATION",
            "FAMILY"
    );
    private static final double INTEREST_MISMATCH_PENALTY = 6.0;
    private static final double PRIORITY_MISMATCH_PENALTY = 10.0;
    private static final double SPECIAL_TARGET_MATCH_BONUS = 12.0;
    private static final double SPECIAL_TARGET_MISMATCH_PENALTY = 8.0;

    private final RecommendationCandidateReadRepository recommendationCandidateReadRepository;
    private final PriorityMatcher priorityMatcher;
    private final RecommendationYouthRelevanceSupport recommendationYouthRelevanceSupport;

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
        Map<Long, List<ServiceTag>> tagsByServiceId = recommendationCandidateReadRepository.findTagsByServiceIds(serviceIds);

        return candidates.stream()
                .map(service -> {
                    List<ServiceTag> tags = tagsByServiceId.getOrDefault(service.getId(), List.of());
                    RecommendationCandidateProjection projection = projections.get(service.getId());
                    boolean interestThemeMatch = interestThemeMatches(interestFields, tags, projection);
                    boolean keywordMatch = keywordMatches(interestFields, tags, projection);
                    boolean interestMismatch = hasInterestMismatch(
                            interestFields,
                            tags,
                            projection,
                            interestThemeMatch,
                            keywordMatch
                    );
                    Integer matchedPriorityRank = findBestMatchingPriorityRank(user.priorities(), service, projection);
                    boolean priorityMismatch = hasPriorityMismatch(
                            user.priorities(),
                            service,
                            projection,
                            matchedPriorityRank
                    );
                    double base = calcBaseScore(
                            service,
                            user,
                            targetTypes,
                            tags,
                            projection,
                            interestThemeMatch,
                            keywordMatch,
                            interestMismatch,
                            priorityMismatch
                    );
                    double weighted = applyPriorityWeight(base, service, projection, user.priorities());
                    // 특수 대상 신호가 있지만 사용자와 불일치한 경우 플래그 설정
                    boolean mismatch = !specialTargetMatches(user, targetTypes, service, tags, projection)
                            && hasSpecialTargetSignal(service, tags, projection);

                    return ScoredCandidate.builder()
                            .service(service)
                            .projection(projection)
                            .ruleBaseScore(base)
                            .ruleWeightedScore(weighted)
                            .hasInterestMismatch(interestMismatch)
                            .hasPriorityMismatch(priorityMismatch)
                            .matchedPriorityRank(matchedPriorityRank)
                            .hasSpecialTargetMismatch(mismatch)
                            .build();
                })
                .collect(Collectors.toList());
    }

    private double calcBaseScore(WelfareService service,
                                 RecommendationUserSnapshot user,
                                 Set<String> targetTypes,
                                 List<ServiceTag> tags,
                                 RecommendationCandidateProjection projection,
                                 boolean interestThemeMatch,
                                 boolean keywordMatch,
                                 boolean interestMismatch,
                                 boolean priorityMismatch) {
        double score = 0;

        // 청년 신호가 강한 정책을 우선 노출하고, 나이만 겹치는 정책은 뒤로 보낸다.
        score += audienceRelevanceBonus(service, tags, projection);

        // 관심분야 일치: INTEREST_THEME 태그 ↔ 유저 INTEREST_FIELD
        if (interestThemeMatch) score += 15;

        // 관심분야 일치: KEYWORD 태그 ↔ 유저 INTEREST_FIELD (온통청년 보완)
        if (keywordMatch) score += 10;

        // 비교 가능한 관심사 신호가 있는데도 사용자 관심분야와 어긋나면 상위 노출을 완화한다.
        if (interestMismatch) {
            score -= INTEREST_MISMATCH_PENALTY;
        }

        // 대상유형 일치: TARGET_GROUP 태그 ↔ 유저 취업상태·가구유형·소득분위
        if (targetGroupMatches(user, tags, projection)) score += 10;

        if (priorityMismatch) {
            score -= PRIORITY_MISMATCH_PENALTY;
        }

        score += Gov24RecommendationScoringSupport.softBonus(service, user, projection);

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

        return RecommendationMatchingSupport.targetGroupMatches(user, targetGroupValues, projection);
    }

    private boolean isDeadlineSoon(WelfareService service, RecommendationCandidateProjection projection) {
        if (service.getApplyEndDate() == null) return false;
        boolean withinWindow = RecommendationRuntimeSupport.isDeadlineSoon(service.getApplyEndDate(), LocalDate.now());
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
        return recommendationYouthRelevanceSupport.relevanceBonus(service, tags);
    }

    private boolean specialTargetMatches(RecommendationUserSnapshot user,
                                         Set<String> targetTypes,
                                         WelfareService service,
                                         List<ServiceTag> tags,
                                         RecommendationCandidateProjection projection) {
        return RecommendationMatchingSupport.specialTargetMatches(user, targetTypes, service, tags, projection);
    }

    private boolean hasSpecialTargetSignal(WelfareService service,
                                           List<ServiceTag> tags,
                                           RecommendationCandidateProjection projection) {
        return RecommendationMatchingSupport.hasSpecialTargetSignal(service, tags, projection);
    }

    private boolean matchesInterestField(Set<String> interestFields, String value) {
        return interestFields.stream()
                .anyMatch(field -> value.contains(field) || field.contains(value));
    }

    private boolean hasInterestMismatch(Set<String> interestFields,
                                        List<ServiceTag> tags,
                                        RecommendationCandidateProjection projection,
                                        boolean interestThemeMatch,
                                        boolean keywordMatch) {
        if (interestFields.isEmpty()) {
            return false;
        }
        if (!hasInterestSignal(tags, projection)) {
            return false;
        }
        return !interestThemeMatch && !keywordMatch;
    }

    private boolean hasInterestSignal(List<ServiceTag> tags,
                                      RecommendationCandidateProjection projection) {
        boolean hasLegacySignal = tags.stream()
                .map(ServiceTag::getTagType)
                .anyMatch(type -> type == ServiceTag.TagType.INTEREST_THEME || type == ServiceTag.TagType.KEYWORD);
        if (hasLegacySignal) {
            return true;
        }
        return projection != null
                && (!projection.interestThemes().isEmpty() || !projection.keywordTags().isEmpty());
    }

    private boolean hasPriorityMismatch(List<PriorityPreference> priorities,
                                        WelfareService service,
                                        RecommendationCandidateProjection projection,
                                        Integer matchedPriorityRank) {
        List<PriorityPreference> categoryPriorities = priorities.stream()
                .filter(priority -> CATEGORY_PRIORITY_CODES.contains(priority.code()))
                .toList();
        if (categoryPriorities.isEmpty()) {
            return false;
        }
        if (!hasCategoryPrioritySignal(service, projection)) {
            return false;
        }
        return matchedPriorityRank == null;
    }

    private boolean hasCategoryPrioritySignal(WelfareService service,
                                              RecommendationCandidateProjection projection) {
        if (projection != null) {
            if (projection.priorityBuckets().stream().anyMatch(CATEGORY_PRIORITY_CODES::contains)) {
                return true;
            }
            if (projection.compatPriorityBucket() != null) {
                return CATEGORY_PRIORITY_CODES.contains(projection.compatPriorityBucket());
            }
        }
        String legacyBucket = CompatCategorySupport.priorityBucket(service.getUnifiedCategory());
        return legacyBucket != null && CATEGORY_PRIORITY_CODES.contains(legacyBucket);
    }

    private Integer findBestMatchingPriorityRank(List<PriorityPreference> priorities,
                                                 WelfareService service,
                                                 RecommendationCandidateProjection projection) {
        return priorities.stream()
                .filter(priority -> CATEGORY_PRIORITY_CODES.contains(priority.code()))
                .filter(priority -> priorityMatcher.matches(priority, service, projection)
                        || matchesEducationCanonicalPriorityExperiment(priority, projection))
                .map(PriorityPreference::rank)
                .min(Integer::compareTo)
                .orElse(null);
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
        return projection.educationPriorityBoostEligible();
    }
}
