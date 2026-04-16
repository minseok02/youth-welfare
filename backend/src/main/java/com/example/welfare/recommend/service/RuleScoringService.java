package com.example.welfare.recommend.service;

import com.example.welfare.policy.entity.ServiceTag;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.repository.ServiceTagRepository;
import com.example.welfare.recommend.dto.ScoredCandidate;
import com.example.welfare.user.entity.User;
import com.example.welfare.user.entity.UserAttribute;
import com.example.welfare.user.entity.UserPriority;
import com.example.welfare.user.repository.UserAttributeRepository;
import com.example.welfare.user.repository.UserPriorityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    private final UserAttributeRepository userAttributeRepository;
    private final UserPriorityRepository userPriorityRepository;
    private final ServiceTagRepository serviceTagRepository;
    private final PriorityMatcher priorityMatcher;

    public List<ScoredCandidate> score(List<WelfareService> candidates, User user) {
        List<UserAttribute> attributes = userAttributeRepository.findByUserId(user.getId());
        List<UserPriority> priorities = userPriorityRepository.findByUserIdOrderByPriorityRank(user.getId());

        // 관심분야 (INTEREST_FIELD)
        Set<String> interestFields = attributes.stream()
                .filter(a -> UserAttribute.AttrType.INTEREST_FIELD.name().equals(a.getAttrType()))
                .map(UserAttribute::getAttrValue)
                .collect(Collectors.toSet());

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
                    double base = calcBaseScore(service, user, interestFields, tags);
                    double weighted = applyPriorityWeight(base, service, priorities);

                    return ScoredCandidate.builder()
                            .service(service)
                            .ruleBaseScore(base)
                            .ruleWeightedScore(weighted)
                            .build();
                })
                .collect(Collectors.toList());
    }

    private double calcBaseScore(WelfareService service, User user,
                                  Set<String> interestFields, List<ServiceTag> tags) {
        double score = 0;

        // 청년 전용 정책 (source_type=YOUTH)
        if (service.getSourceType() == WelfareService.SourceType.YOUTH) score += 20;

        // 온라인 신청 가능
        if (Boolean.TRUE.equals(service.getIsOnlineApply())) score += 10;

        // 관심분야 일치: INTEREST_THEME 태그 ↔ 유저 INTEREST_FIELD
        if (interestThemeMatches(interestFields, tags)) score += 15;

        // 관심분야 일치: KEYWORD 태그 ↔ 유저 INTEREST_FIELD (온통청년 보완)
        if (keywordMatches(interestFields, tags)) score += 10;

        // 대상유형 일치: TARGET_GROUP 태그 ↔ 유저 취업상태·가구유형·소득분위
        if (targetGroupMatches(user, tags)) score += 10;

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
    private boolean targetGroupMatches(User user, List<ServiceTag> tags) {
        List<ServiceTag> targetTags = tags.stream()
                .filter(t -> t.getTagType() == ServiceTag.TagType.TARGET_GROUP)
                .collect(Collectors.toList());

        if (targetTags.isEmpty()) return false;

        return targetTags.stream().anyMatch(t -> {
            String val = t.getTagValue();

            // 취업상태 매핑
            if (user.getEmploymentStatus() != null) {
                String emp = user.getEmploymentStatus();
                if (val.contains("미취업") && emp.contains("미취업")) return true;
                if (val.contains("취업준비") && (emp.contains("취업준비") || emp.contains("구직"))) return true;
                if (val.contains("재직") && emp.contains("재직")) return true;
                if (val.contains("자영업") && emp.contains("자영업")) return true;
                if (val.contains("프리랜서") && emp.contains("프리랜서")) return true;
            }

            // 가구유형 매핑
            if (user.getHouseholdType() != null) {
                String household = user.getHouseholdType();
                if (val.contains("1인가구") && household.contains("1인")) return true;
                if (val.contains("한부모") && household.contains("한부모")) return true;
                if (val.contains("다자녀") && household.contains("다자녀")) return true;
            }

            // 소득분위 매핑 (저소득층 = 3분위 이하로 판단)
            if (user.getIncomeLevel() != null) {
                if (val.contains("저소득") && user.getIncomeLevel() <= 3) return true;
                if (val.contains("기초생활") && user.getIncomeLevel() <= 1) return true;
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

    /**
     * 우선순위 가중치 — 복수 매칭 시 최고 배율 하나만 적용 (이중합산 방지)
     */
    private double applyPriorityWeight(double base, WelfareService service,
                                        List<UserPriority> priorities) {
        if (priorities.isEmpty()) return base;

        double maxWeight = priorities.stream()
                .filter(p -> priorityMatcher.matches(p, service))
                .mapToDouble(UserPriority::getWeight)
                .max()
                .orElse(1.0);

        return base * maxWeight;
    }
}
