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
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Rule 기반 점수 계산
 * rule_base_score → 우선순위 가중치 적용 → rule_weighted_score
 */
@Service
@RequiredArgsConstructor
public class RuleScoringService {

    private final UserAttributeRepository userAttributeRepository;
    private final UserPriorityRepository userPriorityRepository;
    private final ServiceTagRepository serviceTagRepository;

    public List<ScoredCandidate> score(List<WelfareService> candidates, User user) {
        List<UserAttribute> attributes = userAttributeRepository.findByUserId(user.getId());
        List<UserPriority> priorities = userPriorityRepository.findByUserIdOrderByPriorityRank(user.getId());

        Set<String> interestFields = attributes.stream()
                .filter(a -> UserAttribute.AttrType.INTEREST_FIELD.name().equals(a.getAttrType()))
                .map(UserAttribute::getAttrValue)
                .collect(Collectors.toSet());

        return candidates.stream()
                .map(service -> {
                    double base = calcBaseScore(service, user, interestFields);
                    double weighted = applyPriorityWeight(base, service, priorities);

                    return ScoredCandidate.builder()
                            .service(service)
                            .ruleBaseScore(base)
                            .ruleWeightedScore(weighted)
                            .build();
                })
                .collect(Collectors.toList());
    }

    private double calcBaseScore(WelfareService service, User user, Set<String> interestFields) {
        double score = 0;

        // 청년 전용 정책 (source_type=YOUTH)
        if (service.getSourceType() == WelfareService.SourceType.YOUTH) score += 20;

        // 온라인 신청 가능
        if (Boolean.TRUE.equals(service.getIsOnlineApply())) score += 10;

        // 지역 일치
        if (regionMatches(user, service)) score += 10;

        // 관심분야 일치 (user_attributes INTEREST_FIELD ↔ service_tags INTEREST_THEME)
        if (interestMatches(interestFields, service)) score += 10;

        // 마감임박 — apply_end_date 기준 7일 이내
        if (isDeadlineSoon(service)) score += 5;

        return score;
    }

    private boolean regionMatches(User user, WelfareService service) {
        if (user.getSido() == null) return false;
        List<ServiceTag> tags = serviceTagRepository.findByServiceId(service.getId());
        // 전국 또는 지역코드 매핑은 service_regions에서 처리 — 여기서는 service_tags 기준
        return tags.stream()
                .filter(t -> t.getTagType() == ServiceTag.TagType.TARGET_GROUP)
                .anyMatch(t -> t.getTagValue().contains(user.getSido()));
    }

    private boolean interestMatches(Set<String> interestFields, WelfareService service) {
        if (interestFields.isEmpty()) return false;
        List<ServiceTag> tags = serviceTagRepository.findByServiceId(service.getId());
        return tags.stream()
                .filter(t -> t.getTagType() == ServiceTag.TagType.INTEREST_THEME)
                .anyMatch(t -> interestFields.stream()
                        .anyMatch(field -> t.getTagValue().contains(field)));
    }

    private boolean isDeadlineSoon(WelfareService service) {
        if (service.getApplyEndDate() == null) return false;
        return !service.getApplyEndDate().isBefore(LocalDate.now())
                && service.getApplyEndDate().isBefore(LocalDate.now().plusDays(7));
    }

    /**
     * 우선순위 가중치 — 복수 매칭 시 최고 배율 하나만 적용 (이중합산 방지)
     */
    private double applyPriorityWeight(double base, WelfareService service,
                                        List<UserPriority> priorities) {
        if (priorities.isEmpty()) return base;

        double maxWeight = priorities.stream()
                .filter(p -> priorityMatches(p, service))
                .mapToDouble(UserPriority::getWeight)
                .max()
                .orElse(1.0);

        return base * maxWeight;
    }

    private boolean priorityMatches(UserPriority priority, WelfareService service) {
        String code = priority.getPriorityOption().getCode();
        return switch (code) {
            case "HOUSING" -> "주거".equals(service.getUnifiedCategory());
            case "AMOUNT" -> Boolean.TRUE.equals(service.getIsOnlineApply()); // 임시 — 지원금액 컬럼 없음
            case "ONLINE" -> Boolean.TRUE.equals(service.getIsOnlineApply());
            case "YOUTH_ONLY" -> service.getSourceType() == WelfareService.SourceType.YOUTH;
            case "EDU_JOB" -> "교육·직업훈련".equals(service.getUnifiedCategory())
                    || "일자리".equals(service.getUnifiedCategory());
            case "CULTURE" -> "문화·여가".equals(service.getUnifiedCategory());
            case "DEADLINE" -> isDeadlineSoonStatic(service);
            default -> false;
        };
    }

    private boolean isDeadlineSoonStatic(WelfareService service) {
        if (service.getApplyEndDate() == null) return false;
        return !service.getApplyEndDate().isBefore(LocalDate.now())
                && service.getApplyEndDate().isBefore(LocalDate.now().plusDays(7));
    }
}
