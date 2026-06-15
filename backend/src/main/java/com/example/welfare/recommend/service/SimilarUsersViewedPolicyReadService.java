package com.example.welfare.recommend.service;

import com.example.welfare.policy.dto.PolicySummaryResponse;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.repository.WelfareServiceRepository;
import com.example.welfare.policy.service.PolicyPresentationReadService;
import com.example.welfare.recommend.dto.PriorityPreference;
import com.example.welfare.recommend.dto.RecommendationUserSnapshot;
import com.example.welfare.recommend.dto.SimilarUsersViewedPolicyResponse;
import com.example.welfare.recommend.repository.SimilarUsersViewedPolicyCandidate;
import com.example.welfare.recommend.repository.SimilarUsersViewedPolicyQuery;
import com.example.welfare.recommend.repository.SimilarUsersViewedPolicyReadRepository;
import com.example.welfare.user.service.UserRecommendationReadService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SimilarUsersViewedPolicyReadService {

    private static final int DEFAULT_SIZE = 6;
    private static final int MAX_SIZE = 20;
    private static final int OVERSAMPLE_MULTIPLIER = 3;
    private static final int MIN_SIMILAR_USERS = 2;
    private static final double MIN_SIMILARITY_SCORE = 3.0;
    private static final int RECENT_VIEW_WINDOW_DAYS = 30;
    private static final String REASON_LABEL = "비슷한 프로필의 사용자가 최근 확인";

    private final UserRecommendationReadService userRecommendationReadService;
    private final SimilarUsersViewedPolicyReadRepository similarUsersViewedPolicyReadRepository;
    private final WelfareServiceRepository welfareServiceRepository;
    private final PolicyPresentationReadService policyPresentationReadService;
    private final SimilarUsersViewedPolicyMetrics similarUsersViewedPolicyMetrics;

    @Transactional(readOnly = true)
    public List<SimilarUsersViewedPolicyResponse> getSimilarUsersViewedPolicies(Long userId, Integer size) {
        RecommendationUserSnapshot snapshot = userRecommendationReadService.getRecommendationSnapshot(userId);
        if (!hasSimilaritySignals(snapshot)) {
            similarUsersViewedPolicyMetrics.recordNoSignal();
            return List.of();
        }

        int resolvedSize = normalizeSize(size);
        List<SimilarUsersViewedPolicyCandidate> candidates = similarUsersViewedPolicyReadRepository.findCandidates(
                buildQuery(snapshot, resolvedSize)
        );
        if (candidates.isEmpty()) {
            similarUsersViewedPolicyMetrics.recordResult(0, 0);
            return List.of();
        }

        List<WelfareService> orderedServices = loadOrderedServices(candidates);
        if (orderedServices.isEmpty()) {
            similarUsersViewedPolicyMetrics.recordResult(candidates.size(), 0);
            return List.of();
        }

        List<SimilarUsersViewedPolicyResponse> responses = toResponses(userId, orderedServices, resolvedSize);
        similarUsersViewedPolicyMetrics.recordResult(candidates.size(), responses.size());
        return responses;
    }

    private SimilarUsersViewedPolicyQuery buildQuery(RecommendationUserSnapshot snapshot, int size) {
        return new SimilarUsersViewedPolicyQuery(
                snapshot.userKey(),
                snapshot.resolvedAge(),
                snapshot.ageBand(),
                snapshot.sido(),
                snapshot.regionCode(),
                snapshot.incomeLevel() != null ? snapshot.resolvedIncomeLevel() : null,
                snapshot.interestFields(),
                snapshot.targetTypes(),
                priorityCodes(snapshot),
                LocalDateTime.now().minusDays(RECENT_VIEW_WINDOW_DAYS),
                MIN_SIMILAR_USERS,
                MIN_SIMILARITY_SCORE,
                Math.max(size * OVERSAMPLE_MULTIPLIER, size)
        );
    }

    private List<String> priorityCodes(RecommendationUserSnapshot snapshot) {
        if (snapshot.priorities() == null || snapshot.priorities().isEmpty()) {
            return List.of();
        }
        return snapshot.priorities().stream()
                .map(PriorityPreference::code)
                .filter(StringUtils::hasText)
                .toList();
    }

    private boolean hasSimilaritySignals(RecommendationUserSnapshot snapshot) {
        return StringUtils.hasText(snapshot.regionCode())
                || StringUtils.hasText(snapshot.sido())
                || StringUtils.hasText(snapshot.ageBand())
                || snapshot.incomeLevel() != null
                || hasValues(snapshot.interestFields())
                || hasValues(snapshot.targetTypes())
                || hasValues(priorityCodes(snapshot));
    }

    private boolean hasValues(List<String> values) {
        return values != null && values.stream().anyMatch(StringUtils::hasText);
    }

    private int normalizeSize(Integer size) {
        if (size == null || size <= 0) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }

    private List<WelfareService> loadOrderedServices(List<SimilarUsersViewedPolicyCandidate> candidates) {
        List<Long> ids = candidates.stream()
                .map(SimilarUsersViewedPolicyCandidate::serviceId)
                .toList();
        Map<Long, WelfareService> serviceById = new LinkedHashMap<>();
        welfareServiceRepository.findAllById(ids)
                .forEach(service -> serviceById.put(service.getId(), service));
        return ids.stream()
                .map(serviceById::get)
                .filter(service -> service != null)
                .toList();
    }

    private List<SimilarUsersViewedPolicyResponse> toResponses(Long userId,
                                                               List<WelfareService> services,
                                                               int size) {
        Map<Long, PolicySummaryResponse> summaryById = new LinkedHashMap<>();
        policyPresentationReadService.buildSummaryResponses(userId, services)
                .forEach(summary -> summaryById.put(summary.getId(), summary));
        return services.stream()
                .map(service -> summaryById.get(service.getId()))
                .filter(summary -> summary != null)
                .limit(size)
                .map(summary -> new SimilarUsersViewedPolicyResponse(summary, REASON_LABEL))
                .toList();
    }
}
