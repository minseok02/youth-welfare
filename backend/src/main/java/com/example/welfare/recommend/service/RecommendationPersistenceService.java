package com.example.welfare.recommend.service;

import com.example.welfare.policy.repository.WelfareServiceRepository;
import com.example.welfare.recommend.dto.ScoredCandidate;
import com.example.welfare.recommend.entity.ScoreWeight;
import com.example.welfare.recommend.entity.UserRecommendation;
import com.example.welfare.recommend.repository.RecommendationPersistenceCommandRepository;
import com.example.welfare.user.entity.User;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 추천 결과 저장
 * recommended_at(DATETIME), rule_weight_used, ai_weight_used 반드시 함께 기록 (CLAUDE.md 원칙)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationPersistenceService {

    private final RecommendationPersistenceCommandRepository recommendationPersistenceCommandRepository;
    private final RecommendationBookmarkStateReadService recommendationBookmarkStateReadService;
    private final WelfareServiceRepository welfareServiceRepository;

    @Transactional
    public List<UserRecommendation> save(User user, List<ScoredCandidate> candidates, ScoreWeight weight) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        List<ScoredCandidate> existingServiceCandidates = filterExistingServiceCandidates(candidates);
        if (existingServiceCandidates.isEmpty()) {
            log.warn("[RecommendationPersistenceService] 저장 가능한 추천 후보가 없습니다. userKey={}", user.getUserKey());
            return List.of();
        }

        Map<Long, Boolean> bookmarkStateByServiceId =
                recommendationBookmarkStateReadService.findLatestBookmarkStateByServiceId(user.getUserKey());

        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);

        List<UserRecommendation> recommendations = existingServiceCandidates.stream()
                .map(c -> UserRecommendation.builder()
                        .userKey(user.getUserKey())
                        .service(c.getService())
                        .recommendedAt(now)
                        .ruleBaseScore(BigDecimal.valueOf(c.getRuleBaseScore()))
                        .ruleWeightedScore(BigDecimal.valueOf(c.getRuleWeightedScore()))
                        .aiScore(c.getAiScore() != null ? BigDecimal.valueOf(c.getAiScore()) : null)
                        .aiReason(c.getAiReason())
                        .aiStatus(c.getAiStatus())
                        .ruleWeightUsed(weight.getRuleWeight())
                        .aiWeightUsed(weight.getAiWeight())
                        .finalScore(BigDecimal.valueOf(c.getFinalScore()))
                        .isBookmarked(Boolean.TRUE.equals(bookmarkStateByServiceId.get(c.getService().getId())))
                        .build())
                .toList();

        return recommendationPersistenceCommandRepository.replaceAllForUser(user.getUserKey(), recommendations);
    }

    private List<ScoredCandidate> filterExistingServiceCandidates(List<ScoredCandidate> candidates) {
        LinkedHashSet<Long> requestedServiceIds = candidates.stream()
                .map(ScoredCandidate::getService)
                .filter(service -> service != null && service.getId() != null)
                .map(service -> service.getId())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (requestedServiceIds.isEmpty()) {
            return List.of();
        }

        Set<Long> existingServiceIds = Set.copyOf(welfareServiceRepository.findExistingIdsByIdIn(List.copyOf(requestedServiceIds)));
        List<Long> missingServiceIds = requestedServiceIds.stream()
                .filter(serviceId -> !existingServiceIds.contains(serviceId))
                .toList();
        if (!missingServiceIds.isEmpty()) {
            log.warn("[RecommendationPersistenceService] welfare_services missing for recommendation candidates. dropping serviceIds={}",
                    missingServiceIds);
        }

        return candidates.stream()
                .filter(candidate -> candidate.getService() != null
                        && candidate.getService().getId() != null
                        && existingServiceIds.contains(candidate.getService().getId()))
                .toList();
    }
}
