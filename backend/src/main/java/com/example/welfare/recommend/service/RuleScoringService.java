package com.example.welfare.recommend.service;

import com.example.welfare.policy.entity.ServiceTag;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.repository.ServiceTagRepository;
import com.example.welfare.recommend.dto.PriorityPreference;
import com.example.welfare.recommend.dto.RecommendationUserSnapshot;
import com.example.welfare.recommend.dto.ScoredCandidate;
import lombok.RequiredArgsConstructor;
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

    private static final double SPECIAL_TARGET_MATCH_BONUS = 12.0;
    private static final double SPECIAL_TARGET_MISMATCH_PENALTY = 8.0;

    private final ServiceTagRepository serviceTagRepository;
    private final PriorityMatcher priorityMatcher;
    private final YouthPolicyFilter youthPolicyFilter;

    public List<ScoredCandidate> score(List<WelfareService> candidates, RecommendationUserSnapshot user) {
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
                    double base = calcBaseScore(service, user, interestFields, targetTypes, tags);
                    double weighted = applyPriorityWeight(base, service, user.priorities());
                    // 특수 대상 신호가 있지만 사용자와 불일치한 경우 플래그 설정
                    boolean mismatch = !specialTargetMatches(user, targetTypes, service, tags)
                            && hasSpecialTargetSignal(service, tags);

                    return ScoredCandidate.builder()
                            .service(service)
                            .ruleBaseScore(base)
                            .ruleWeightedScore(weighted)
                            .hasSpecialTargetMismatch(mismatch)
                            .build();
                })
                .collect(Collectors.toList());
    }

    private double calcBaseScore(WelfareService service, RecommendationUserSnapshot user,
                                  Set<String> interestFields, Set<String> targetTypes, List<ServiceTag> tags) {
        double score = 0;

        // 청년 신호가 강한 정책을 우선 노출하고, 나이만 겹치는 정책은 뒤로 보낸다.
        score += youthPolicyFilter.relevanceBonus(service, tags);

        // 관심분야 일치: INTEREST_THEME 태그 ↔ 유저 INTEREST_FIELD
        if (interestThemeMatches(interestFields, tags)) score += 15;

        // 관심분야 일치: KEYWORD 태그 ↔ 유저 INTEREST_FIELD (온통청년 보완)
        if (keywordMatches(interestFields, tags)) score += 10;

        // 대상유형 일치: TARGET_GROUP 태그 ↔ 유저 취업상태·가구유형·소득분위
        if (targetGroupMatches(user, tags)) score += 10;

        if (specialTargetMatches(user, targetTypes, service, tags)) score += SPECIAL_TARGET_MATCH_BONUS;
        else if (hasSpecialTargetSignal(service, tags)) score -= SPECIAL_TARGET_MISMATCH_PENALTY;

        // 마감임박 — apply_end_date 기준 7일 이내
        if (isDeadlineSoon(service)) score += 5;

        return score;
    }

    /**
     * INTEREST_THEME 태그 ↔ 유저 관심분야 매칭 (복지로 정책)
     * 태그 없으면 건너뜀 (0점 부여하지 않음 — CLAUDE.md 원칙)
     */
    private boolean interestThemeMatches(Set<String> interestFields, List<ServiceTag> tags) {
        if (interestFields.isEmpty()) return false;
        return tags.stream()
                .filter(t -> t.getTagType() == ServiceTag.TagType.INTEREST_THEME)
                .anyMatch(t -> interestFields.stream()
                        .anyMatch(field -> t.getTagValue().contains(field) || field.contains(t.getTagValue())));
    }

    /**
     * KEYWORD 태그 ↔ 유저 관심분야 매칭 (온통청년 정책 보완)
     * plcyKywdNm에서 생성된 KEYWORD 태그와 유저 관심분야 비교
     */
    private boolean keywordMatches(Set<String> interestFields, List<ServiceTag> tags) {
        if (interestFields.isEmpty()) return false;
        return tags.stream()
                .filter(t -> t.getTagType() == ServiceTag.TagType.KEYWORD)
                .anyMatch(t -> interestFields.stream()
                        .anyMatch(field -> t.getTagValue().contains(field) || field.contains(t.getTagValue())));
    }

    /**
     * TARGET_GROUP 태그 ↔ 유저 취업상태·가구유형·소득분위 매칭 (복지로 정책)
     * 태그 없으면 건너뜀 (0점 부여하지 않음)
     *
     * 복지로 trgterIndvdlArray 실제 값 예시:
     * "미취업청년", "저소득층", "1인가구", "청년", "대학생", "취업준비생"
     */
    private boolean targetGroupMatches(RecommendationUserSnapshot user, List<ServiceTag> tags) {
        List<ServiceTag> targetTags = tags.stream()
                .filter(t -> t.getTagType() == ServiceTag.TagType.TARGET_GROUP)
                .collect(Collectors.toList());

        if (targetTags.isEmpty()) return false;

        return targetTags.stream().anyMatch(t -> {
            String val = t.getTagValue();

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
    }

    private boolean isDeadlineSoon(WelfareService service) {
        if (service.getApplyEndDate() == null) return false;
        LocalDate today = LocalDate.now();
        return !service.getApplyEndDate().isBefore(today)
                && service.getApplyEndDate().isBefore(today.plusDays(7));
    }

    private boolean specialTargetMatches(RecommendationUserSnapshot user, Set<String> targetTypes, WelfareService service, List<ServiceTag> tags) {
        return specialAudienceMatchedByTargetTypes(targetTypes, service, tags)
                || specialAudienceMatchedByUserProfile(user, service, tags);
    }

    private boolean hasSpecialTargetSignal(WelfareService service, List<ServiceTag> tags) {
        return containsAnySignal(service, tags,
                "장애", "농어촌", "농촌", "어촌", "자립준비", "보호종료",
                "가족돌봄", "다문화", "북한이탈", "한부모", "조손", "보훈",
                "현역병", "병역");
    }

    private boolean specialAudienceMatchedByTargetTypes(Set<String> targetTypes, WelfareService service, List<ServiceTag> tags) {
        if (targetTypes.isEmpty()) return false;

        if (targetTypes.stream().anyMatch(type -> containsSignal(service, tags, type))) {
            return true;
        }
        if (targetTypes.contains("자립준비청년") && containsAnySignal(service, tags, "자립준비", "보호종료")) {
            return true;
        }
        if (targetTypes.contains("농어촌") && containsAnySignal(service, tags, "농어촌", "농촌", "어촌")) {
            return true;
        }
        return false;
    }

    private boolean specialAudienceMatchedByUserProfile(RecommendationUserSnapshot user, WelfareService service, List<ServiceTag> tags) {
        if (user.incomeLevel() != null && user.incomeLevel() <= 3
                && containsAnySignal(service, tags, "저소득", "기초생활")) {
            return true;
        }
        if (user.householdType() != null) {
            String household = user.householdType();
            if (household.contains("한부모") && containsAnySignal(service, tags, "한부모")) {
                return true;
            }
            if (household.contains("조손") && containsAnySignal(service, tags, "조손")) {
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

    /**
     * 우선순위 가중치 — 복수 매칭 시 최고 배율 하나만 적용 (이중합산 방지)
     */
    private double applyPriorityWeight(double base, WelfareService service,
                                        List<PriorityPreference> priorities) {
        if (priorities.isEmpty()) return base;

        double maxWeight = priorities.stream()
                .filter(p -> priorityMatcher.matches(p, service))
                .mapToDouble(PriorityPreference::weight)
                .max()
                .orElse(1.0);

        return base * maxWeight;
    }
}
