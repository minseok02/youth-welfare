package com.example.welfare.policy.service;

import com.example.welfare.policy.dto.PolicyRankingResponse;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.repository.PolicyRankingReadRepository;
import com.example.welfare.policy.repository.ServiceViewLogRepository;
import com.example.welfare.recommend.dto.RecommendationCandidateProjection;
import com.example.welfare.recommend.repository.CanonicalRecommendationReadModelRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PolicyRankingService {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;
    private static final int UNIQUE_VIEW_WINDOW_DAYS = 7;
    private static final int EXPLORE_SLOT_COUNT = 2;
    private static final int EXPLORE_WINDOW_DAYS = 14;

    private final PolicyRankingReadRepository policyRankingReadRepository;
    private final ServiceViewLogRepository serviceViewLogRepository;
    private final CanonicalRecommendationReadModelRepository canonicalRecommendationReadModelRepository;

    @Transactional(readOnly = true)
    public List<PolicyRankingResponse> getRanking(int size) {
        int limit = normalizeSize(size);
        List<WelfareService> services = policyRankingReadRepository.findRankableServices();
        if (services.isEmpty()) return List.of();

        List<Long> serviceIds = services.stream()
                .map(WelfareService::getId)
                .toList();
        LocalDateTime uniqueCutoff = LocalDateTime.now().minusDays(UNIQUE_VIEW_WINDOW_DAYS);
        Map<Long, Long> uniqueViewsByServiceId = serviceViewLogRepository.findUniqueViewCountsSince(serviceIds, uniqueCutoff)
                .stream()
                .collect(Collectors.toMap(
                        ServiceViewLogRepository.ServiceUniqueViewCount::getServiceId,
                        row -> safeLong(row.getUniqueViewCount())
                ));
        Map<Long, RecommendationCandidateProjection> projections =
                canonicalRecommendationReadModelRepository.findByServiceIds(serviceIds);

        double maxUniqueRaw = services.stream()
                .mapToDouble(s -> log1p(uniqueViewsByServiceId.getOrDefault(s.getId(), 0L)))
                .max()
                .orElse(0.0);

        double maxViewRaw = services.stream()
                .mapToDouble(s -> log1p(s.getViewCount()))
                .max()
                .orElse(0.0);

        // source_type별 외부 조회수 최대값(정규화용)
        Map<WelfareService.SourceType, Double> maxExternalBySource = new EnumMap<>(WelfareService.SourceType.class);
        for (WelfareService.SourceType sourceType : WelfareService.SourceType.values()) {
            double max = services.stream()
                    .filter(s -> s.getSourceType() == sourceType)
                    .mapToDouble(s -> log1p(s.getApiViewCount()))
                    .max()
                    .orElse(0.0);
            maxExternalBySource.put(sourceType, max);
        }

        long totalUniqueViews = uniqueViewsByServiceId.values().stream()
                .mapToLong(this::safeLong)
                .sum();

        WeightSet baseWeights = weightSetForTraffic(totalUniqueViews);

        List<ScoredService> sortedByScore = services.stream()
                .map(service -> {
                    long uniqueViews = uniqueViewsByServiceId.getOrDefault(service.getId(), 0L);
                    double uniqueNorm = normalize(log1p(uniqueViews), maxUniqueRaw);
                    double viewNorm = normalize(log1p(service.getViewCount()), maxViewRaw);
                    double freshnessNorm = freshnessScore(service);

                    double externalNorm = 0.0;
                    boolean externalAvailable = maxExternalBySource.getOrDefault(service.getSourceType(), 0.0) > 0.0;
                    if (externalAvailable) {
                        externalNorm = normalize(
                                log1p(service.getApiViewCount()),
                                maxExternalBySource.get(service.getSourceType())
                        );
                    }

                    WeightSet applied = externalAvailable
                            ? baseWeights
                            : baseWeights.withoutExternal();

                    double score = uniqueNorm * applied.uniqueWeight()
                            + viewNorm * applied.viewWeight()
                            + externalNorm * applied.externalWeight()
                            + freshnessNorm * applied.freshnessWeight();

                    return ScoredService.builder()
                            .service(service)
                            .score(score)
                            .build();
                })
                .sorted(Comparator.comparingDouble(ScoredService::score).reversed())
                .toList();

        Map<Long, ScoredService> scoredByServiceId = sortedByScore.stream()
                .collect(Collectors.toMap(
                        scored -> scored.service().getId(),
                        Function.identity(),
                        (a, b) -> a
                ));

        return applyExplorationSlots(sortedByScore, services, scoredByServiceId, limit).stream()
                .map(scored -> PolicyRankingResponse.of(
                        scored.service(),
                        uniqueViewsByServiceId.getOrDefault(scored.service().getId(), 0L),
                        round4(scored.score()),
                        projections.get(scored.service().getId())
                ))
                .toList();
    }

    private int normalizeSize(int size) {
        if (size <= 0) return DEFAULT_SIZE;
        return Math.min(size, MAX_SIZE);
    }

    private double freshnessScore(WelfareService service) {
        LocalDateTime base = service.getCreatedAt() != null
                ? service.getCreatedAt()
                : (service.getRegisteredAt() != null ? service.getRegisteredAt() : null);
        if (base == null) return 0.0;
        long days = Math.max(0, ChronoUnit.DAYS.between(base, LocalDateTime.now()));
        return Math.exp(-days / 30.0); // 30일 반감
    }

    private WeightSet weightSetForTraffic(long totalUniqueViews) {
        // 고유 조회수가 적은 초기에는 raw view 비중을 높이고, 고유 조회수가 쌓이면 unique 비중을 높인다.
        if (totalUniqueViews < 100L) return new WeightSet(0.25, 0.50, 0.15, 0.10);
        if (totalUniqueViews < 1000L) return new WeightSet(0.45, 0.30, 0.15, 0.10);
        return new WeightSet(0.55, 0.20, 0.15, 0.10);
    }

    private double normalize(double value, double max) {
        if (max <= 0.0) return 0.0;
        return value / max;
    }

    private double log1p(Number value) {
        return Math.log1p(safeLong(value));
    }

    private long safeLong(Number value) {
        if (value == null) return 0L;
        return Math.max(0L, value.longValue());
    }

    private double round4(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }

    private List<ScoredService> applyExplorationSlots(List<ScoredService> sortedByScore,
                                                      List<WelfareService> services,
                                                      Map<Long, ScoredService> scoredByServiceId,
                                                      int limit) {
        List<ScoredService> top = new ArrayList<>(sortedByScore.stream().limit(limit).toList());
        if (limit < 10 || top.isEmpty()) return top;

        int slots = Math.min(EXPLORE_SLOT_COUNT, limit);
        Set<Long> existingIds = top.stream().map(s -> s.service().getId()).collect(Collectors.toSet());
        LocalDateTime exploreCutoff = LocalDateTime.now().minusDays(EXPLORE_WINDOW_DAYS);

        List<ScoredService> exploreCandidates = services.stream()
                .filter(this::isRecentPolicy)
                .filter(s -> policyBaseTime(s) != null && !policyBaseTime(s).isBefore(exploreCutoff))
                .filter(s -> !existingIds.contains(s.getId()))
                .sorted(Comparator.comparing(this::policyBaseTime, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(s -> scoredByServiceId.get(s.getId()))
                .filter(scored -> scored != null)
                .limit(slots)
                .toList();

        for (ScoredService candidate : exploreCandidates) {
            if (top.size() >= limit && !top.isEmpty()) {
                top.remove(top.size() - 1);
            }
            top.add(candidate);
        }
        return top;
    }

    private boolean isRecentPolicy(WelfareService service) {
        LocalDateTime base = policyBaseTime(service);
        return base != null;
    }

    private LocalDateTime policyBaseTime(WelfareService service) {
        if (service.getCreatedAt() != null) return service.getCreatedAt();
        return service.getRegisteredAt();
    }

    private record WeightSet(double uniqueWeight, double viewWeight, double externalWeight, double freshnessWeight) {
        private WeightSet withoutExternal() {
            double sum = uniqueWeight + viewWeight + freshnessWeight;
            if (sum <= 0.0) return this;
            return new WeightSet(uniqueWeight / sum, viewWeight / sum, 0.0, freshnessWeight / sum);
        }
    }

    @lombok.Builder
    private record ScoredService(WelfareService service, double score) {
    }
}
