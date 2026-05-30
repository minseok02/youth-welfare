package com.example.welfare.policy.service;

import com.example.welfare.policy.dto.PolicyRankingResponse;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.repository.PolicyRankingReadRepository;
import com.example.welfare.recommend.dto.RecommendationCandidateProjection;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PolicyRankingService {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;
    private static final int UNIQUE_VIEW_WINDOW_DAYS = 7;
    private static final int EXPLORE_SLOT_COUNT = 2;
    private static final int EXPLORE_WINDOW_DAYS = 14;
    private static final long RANKING_CACHE_TTL_MILLIS = 30_000L;

    private final PolicyRankingReadRepository policyRankingReadRepository;
    private final PolicyPresentationReadService policyPresentationReadService;
    private final Clock clock;
    private final Map<Integer, CachedRanking> rankingCache = new ConcurrentHashMap<>();

    @Autowired
    public PolicyRankingService(PolicyRankingReadRepository policyRankingReadRepository,
                                PolicyPresentationReadService policyPresentationReadService) {
        this(policyRankingReadRepository, policyPresentationReadService, Clock.systemUTC());
    }

    PolicyRankingService(PolicyRankingReadRepository policyRankingReadRepository,
                         PolicyPresentationReadService policyPresentationReadService,
                         Clock clock) {
        this.policyRankingReadRepository = policyRankingReadRepository;
        this.policyPresentationReadService = policyPresentationReadService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<PolicyRankingResponse> getRanking(int size) {
        int limit = normalizeSize(size);
        CachedRanking cachedRanking = rankingCache.get(limit);
        long nowMillis = clock.millis();
        if (cachedRanking != null && nowMillis - cachedRanking.cachedAtMillis() < RANKING_CACHE_TTL_MILLIS) {
            return cachedRanking.responses();
        }

        List<PolicyRankingResponse> computed = computeRanking(limit);
        rankingCache.put(limit, new CachedRanking(nowMillis, computed));
        return computed;
    }

    private List<PolicyRankingResponse> computeRanking(int limit) {
        List<PolicyRankingReadRepository.RankableServiceSnapshot> snapshots = policyRankingReadRepository.findRankableSnapshots();
        if (snapshots.isEmpty()) return List.of();

        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime uniqueCutoff = now.minusDays(UNIQUE_VIEW_WINDOW_DAYS);
        Map<Long, Long> uniqueViewsByServiceId = policyRankingReadRepository.findUniqueViewCountsSinceForStatuses(
                        List.of(
                                WelfareService.ServiceStatus.ACTIVE,
                                WelfareService.ServiceStatus.UPCOMING
                        ),
                        uniqueCutoff
                )
                .stream()
                .collect(Collectors.toMap(
                        PolicyRankingReadRepository.ServiceUniqueViewCount::getServiceId,
                        row -> safeLong(row.getUniqueViewCount())
                ));
        double maxUniqueRaw = snapshots.stream()
                .mapToDouble(s -> log1p(uniqueViewsByServiceId.getOrDefault(s.getId(), 0L)))
                .max()
                .orElse(0.0);

        double maxViewRaw = snapshots.stream()
                .mapToDouble(s -> log1p(s.getViewCount()))
                .max()
                .orElse(0.0);

        // source_type별 외부 조회수 최대값(정규화용)
        Map<WelfareService.SourceType, Double> maxExternalBySource = new EnumMap<>(WelfareService.SourceType.class);
        for (WelfareService.SourceType sourceType : WelfareService.SourceType.values()) {
            double max = snapshots.stream()
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

        List<ScoredSnapshot> sortedByScore = snapshots.stream()
                .map(service -> {
                    long uniqueViews = uniqueViewsByServiceId.getOrDefault(service.getId(), 0L);
                    double uniqueNorm = normalize(log1p(uniqueViews), maxUniqueRaw);
                    double viewNorm = normalize(log1p(service.getViewCount()), maxViewRaw);
                    double freshnessNorm = freshnessScore(service, now);

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

                    return ScoredSnapshot.builder()
                            .snapshot(service)
                            .score(score)
                            .build();
                })
                .sorted(Comparator.comparingDouble(ScoredSnapshot::score).reversed())
                .toList();

        Map<Long, ScoredSnapshot> scoredByServiceId = sortedByScore.stream()
                .collect(Collectors.toMap(
                        scored -> scored.snapshot().getId(),
                        Function.identity(),
                        (a, b) -> a
                ));

        List<ScoredSnapshot> selected = applyExplorationSlots(sortedByScore, snapshots, scoredByServiceId, limit);
        List<Long> selectedIds = selected.stream()
                .map(scored -> scored.snapshot().getId())
                .toList();
        Map<Long, WelfareService> selectedServicesById = new LinkedHashMap<>();
        for (WelfareService service : policyRankingReadRepository.findServicesByIds(selectedIds)) {
            selectedServicesById.put(service.getId(), service);
        }

        List<WelfareService> selectedServices = selectedIds.stream()
                .map(selectedServicesById::get)
                .filter(java.util.Objects::nonNull)
                .toList();

        Map<Long, RecommendationCandidateProjection> projections = policyPresentationReadService.findProjections(selectedServices);

        return selected.stream()
                .map(scored -> {
                    WelfareService service = selectedServicesById.get(scored.snapshot().getId());
                    if (service == null) {
                        return null;
                    }
                    return PolicyRankingResponse.of(
                        service,
                        uniqueViewsByServiceId.getOrDefault(scored.snapshot().getId(), 0L),
                        round4(scored.score()),
                        projections.get(scored.snapshot().getId())
                    );
                })
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private int normalizeSize(int size) {
        if (size <= 0) return DEFAULT_SIZE;
        return Math.min(size, MAX_SIZE);
    }

    private double freshnessScore(PolicyRankingReadRepository.RankableServiceSnapshot service, LocalDateTime now) {
        LocalDateTime base = policyBaseTime(service);
        if (base == null) return 0.0;
        long days = Math.max(0, ChronoUnit.DAYS.between(base, now));
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

    private List<ScoredSnapshot> applyExplorationSlots(List<ScoredSnapshot> sortedByScore,
                                                      List<PolicyRankingReadRepository.RankableServiceSnapshot> services,
                                                      Map<Long, ScoredSnapshot> scoredByServiceId,
                                                      int limit) {
        List<ScoredSnapshot> top = new ArrayList<>(sortedByScore.stream().limit(limit).toList());
        if (limit < 10 || top.isEmpty()) return top;

        int slots = Math.min(EXPLORE_SLOT_COUNT, limit);
        Set<Long> existingIds = top.stream().map(s -> s.snapshot().getId()).collect(Collectors.toSet());
        LocalDateTime exploreCutoff = LocalDateTime.now(clock).minusDays(EXPLORE_WINDOW_DAYS);

        List<ScoredSnapshot> exploreCandidates = services.stream()
                .filter(this::isRecentPolicy)
                .filter(s -> policyBaseTime(s) != null && !policyBaseTime(s).isBefore(exploreCutoff))
                .filter(s -> !existingIds.contains(s.getId()))
                .sorted(Comparator.comparing(this::policyBaseTime, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(s -> scoredByServiceId.get(s.getId()))
                .filter(scored -> scored != null)
                .limit(slots)
                .toList();

        for (ScoredSnapshot candidate : exploreCandidates) {
            if (top.size() >= limit && !top.isEmpty()) {
                top.remove(top.size() - 1);
            }
            top.add(candidate);
        }
        return top;
    }

    private boolean isRecentPolicy(PolicyRankingReadRepository.RankableServiceSnapshot service) {
        LocalDateTime base = policyBaseTime(service);
        return base != null;
    }

    private LocalDateTime policyBaseTime(PolicyRankingReadRepository.RankableServiceSnapshot service) {
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
    private record ScoredSnapshot(PolicyRankingReadRepository.RankableServiceSnapshot snapshot, double score) {
    }

    private record CachedRanking(long cachedAtMillis, List<PolicyRankingResponse> responses) {
    }
}
