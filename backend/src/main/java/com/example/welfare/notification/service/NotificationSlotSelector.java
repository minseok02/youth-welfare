package com.example.welfare.notification.service;

import com.example.welfare.recommend.entity.UserRecommendation;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 알림 슬롯 후보 선택.
 * - A 슬롯: final_score 기준 상위 개인화 추천 2건
 * - B 슬롯: 수집 후 24시간 이내 + 최소 base score를 통과한 신규 정책 1건
 * - B 슬롯이 없으면 기존 top 3 A 패턴으로 fallback
 */
@Service
public class NotificationSlotSelector {

    static final int SLOT_COUNT = 3;
    static final int A_SLOT_COUNT = 2;
    static final BigDecimal MIN_B_RULE_BASE_SCORE = new BigDecimal("0.5");

    public List<UserRecommendation> selectCandidates(List<UserRecommendation> recommendationPool,
                                                     double minFinalScore) {
        if (recommendationPool == null || recommendationPool.isEmpty()) {
            return List.of();
        }

        List<UserRecommendation> aCandidates = recommendationPool.stream()
                .filter(this::hasFinalScore)
                .filter(rec -> rec.getFinalScore().doubleValue() >= minFinalScore)
                .sorted(Comparator.comparing(UserRecommendation::getFinalScore).reversed()
                        .thenComparing(UserRecommendation::getRecommendedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        List<UserRecommendation> bCandidates = recommendationPool.stream()
                .filter(this::isBSlotEligible)
                .sorted(Comparator.comparing(UserRecommendation::getRuleBaseScore, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(rec -> rec.getService().getCreatedAt(), Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        List<UserRecommendation> selected = new ArrayList<>(SLOT_COUNT);
        Set<Long> selectedServiceIds = new LinkedHashSet<>();

        appendUnique(selected, selectedServiceIds, aCandidates, A_SLOT_COUNT);
        appendUnique(selected, selectedServiceIds, bCandidates, SLOT_COUNT);
        appendUnique(selected, selectedServiceIds, aCandidates, SLOT_COUNT);

        return selected;
    }

    private boolean hasFinalScore(UserRecommendation recommendation) {
        return recommendation.getFinalScore() != null;
    }

    private boolean isBSlotEligible(UserRecommendation recommendation) {
        if (recommendation == null || recommendation.getService() == null) {
            return false;
        }
        if (recommendation.getRuleBaseScore() == null
                || recommendation.getRuleBaseScore().compareTo(MIN_B_RULE_BASE_SCORE) < 0) {
            return false;
        }
        LocalDateTime createdAt = recommendation.getService().getCreatedAt();
        return createdAt != null && createdAt.isAfter(LocalDateTime.now().minusHours(24));
    }

    private void appendUnique(List<UserRecommendation> selected,
                              Set<Long> selectedServiceIds,
                              List<UserRecommendation> candidates,
                              int targetSize) {
        if (selected.size() >= targetSize) {
            return;
        }
        for (UserRecommendation candidate : candidates) {
            if (selected.size() >= targetSize) {
                return;
            }
            Long serviceId = candidate.getService().getId();
            if (serviceId != null && !selectedServiceIds.add(serviceId)) {
                continue;
            }
            selected.add(candidate);
        }
    }
}
