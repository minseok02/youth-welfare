package com.example.welfare.recommend.service;

import com.example.welfare.recommend.dto.ScoredCandidate;
import com.example.welfare.recommend.entity.ScoreWeight;
import com.example.welfare.recommend.entity.UserRecommendation;
import com.example.welfare.recommend.repository.UserRecommendationRepository;
import com.example.welfare.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 추천 결과 저장
 * recommended_at(DATETIME), rule_weight_used, ai_weight_used 반드시 함께 기록 (CLAUDE.md 원칙)
 */
@Service
@RequiredArgsConstructor
public class RecommendationPersistenceService {

    private final UserRecommendationRepository userRecommendationRepository;

    @Transactional
    public List<UserRecommendation> save(User user, List<ScoredCandidate> candidates, ScoreWeight weight) {
        List<UserRecommendation> latestRecommendations = userRecommendationRepository.findLatestByUserId(user.getId());
        LocalDateTime now = nextRecommendedAt(latestRecommendations);
        Map<Long, Boolean> bookmarkStateByServiceId = latestRecommendations.stream()
                .collect(Collectors.toMap(
                        rec -> rec.getService().getId(),
                        UserRecommendation::isBookmarked,
                        (left, right) -> right
                ));
        userRecommendationRepository.deleteUnbookmarkedByUserId(user.getId());

        List<UserRecommendation> recommendations = candidates.stream()
                .map(c -> UserRecommendation.builder()
                        .user(user)
                        .service(c.getService())
                        .recommendedAt(now)
                        .ruleBaseScore(BigDecimal.valueOf(c.getRuleBaseScore()))
                        .ruleWeightedScore(BigDecimal.valueOf(c.getRuleWeightedScore()))
                        .aiScore(c.getAiScore() != null ? BigDecimal.valueOf(c.getAiScore()) : null)
                        .aiReason(c.getAiReason())
                        .ruleWeightUsed(weight.getRuleWeight())
                        .aiWeightUsed(weight.getAiWeight())
                        .finalScore(BigDecimal.valueOf(c.getFinalScore()))
                        .isBookmarked(Boolean.TRUE.equals(bookmarkStateByServiceId.get(c.getService().getId())))
                        .build())
                .toList();

        return userRecommendationRepository.saveAll(recommendations);
    }

    private LocalDateTime nextRecommendedAt(List<UserRecommendation> latestRecommendations) {
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        return latestRecommendations.stream()
                .map(UserRecommendation::getRecommendedAt)
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .filter(latest -> !latest.isBefore(now))
                .map(latest -> latest.plusSeconds(1))
                .orElse(now);
    }
}
