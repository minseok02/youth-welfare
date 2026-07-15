package com.example.welfare.policy.service;

import com.example.welfare.policy.dto.PolicyRankingResponse;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.repository.PolicyRankingReadRepository;
import com.example.welfare.recommend.dto.RecommendationCandidateProjection;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
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
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
public class PolicyRankingService {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;
    private static final int UNIQUE_VIEW_WINDOW_DAYS = 7;
    private static final int EXPLORE_SLOT_COUNT = 2;
    private static final int EXPLORE_WINDOW_DAYS = 14;
    private static final long RANKING_CACHE_TTL_MILLIS = 30_000L;
    private static final long DEFAULT_COLD_TIMING_LOG_THRESHOLD_MS = 300L;
    private static final String RANKING_CACHE_PREFIX = "policy:ranking:v1:";
    private static final TypeReference<List<PolicyRankingResponse>> RANKING_RESPONSE_LIST_TYPE = new TypeReference<>() {
    };

    private final PolicyRankingReadRepository policyRankingReadRepository;
    private final PolicyPresentationReadService policyPresentationReadService;
    private final Clock clock;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final Duration rankingCacheTtl;
    private final long coldTimingLogThresholdMs;
    private final Map<Integer, CachedRanking> rankingCache = new ConcurrentHashMap<>();

    @Autowired
    public PolicyRankingService(PolicyRankingReadRepository policyRankingReadRepository,
                                PolicyPresentationReadService policyPresentationReadService,
                                RedisTemplate<String, String> redisTemplate,
                                ObjectMapper objectMapper,
                                @Value("${policy.cache.ranking.ttl-seconds:30}") long rankingCacheTtlSeconds,
                                @Value("${policy.ranking.cold-timing-log-threshold-ms:300}") long coldTimingLogThresholdMs) {
        this(
                policyRankingReadRepository,
                policyPresentationReadService,
                Clock.systemUTC(),
                redisTemplate,
                objectMapper,
                Duration.ofSeconds(rankingCacheTtlSeconds),
                coldTimingLogThresholdMs
        );
    }

    PolicyRankingService(PolicyRankingReadRepository policyRankingReadRepository,
                         PolicyPresentationReadService policyPresentationReadService,
                         Clock clock) {
        this(
                policyRankingReadRepository,
                policyPresentationReadService,
                clock,
                null,
                null,
                Duration.ofMillis(RANKING_CACHE_TTL_MILLIS),
                DEFAULT_COLD_TIMING_LOG_THRESHOLD_MS
        );
    }

    PolicyRankingService(PolicyRankingReadRepository policyRankingReadRepository,
                         PolicyPresentationReadService policyPresentationReadService,
                         Clock clock,
                         RedisTemplate<String, String> redisTemplate,
                         ObjectMapper objectMapper,
                         Duration rankingCacheTtl) {
        this(
                policyRankingReadRepository,
                policyPresentationReadService,
                clock,
                redisTemplate,
                objectMapper,
                rankingCacheTtl,
                DEFAULT_COLD_TIMING_LOG_THRESHOLD_MS
        );
    }

    PolicyRankingService(PolicyRankingReadRepository policyRankingReadRepository,
                         PolicyPresentationReadService policyPresentationReadService,
                         Clock clock,
                         RedisTemplate<String, String> redisTemplate,
                         ObjectMapper objectMapper,
                         Duration rankingCacheTtl,
                         long coldTimingLogThresholdMs) {
        this.policyRankingReadRepository = policyRankingReadRepository;
        this.policyPresentationReadService = policyPresentationReadService;
        this.clock = clock;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.rankingCacheTtl = rankingCacheTtl.isNegative() || rankingCacheTtl.isZero()
                ? Duration.ofMillis(RANKING_CACHE_TTL_MILLIS)
                : rankingCacheTtl;
        this.coldTimingLogThresholdMs = Math.max(0L, coldTimingLogThresholdMs);
    }

    @Transactional(readOnly = true)
    public List<PolicyRankingResponse> getRanking(int size) {
        int limit = normalizeSize(size);
        CachedRanking cachedRanking = rankingCache.get(limit);
        long nowMillis = clock.millis();
        if (cachedRanking != null && nowMillis - cachedRanking.cachedAtMillis() < rankingCacheTtl.toMillis()) {
            return cachedRanking.responses();
        }
        List<PolicyRankingResponse> redisCached = getRedisCachedRanking(limit);
        if (redisCached != null) {
            cacheRankingLocal(limit, redisCached);
            return redisCached;
        }

        long coldStartedNanos = System.nanoTime();
        RankingComputation computation = computeRanking(limit);
        long localCacheStartedNanos = System.nanoTime();
        cacheRankingLocal(limit, computation.responses());
        long localCacheWriteMs = elapsedMs(localCacheStartedNanos);
        long redisCacheStartedNanos = System.nanoTime();
        cacheRankingRedis(limit, computation.responses());
        long redisCacheWriteMs = elapsedMs(redisCacheStartedNanos);
        long coldTotalMs = elapsedMs(coldStartedNanos);
        logColdTimingIfSlow(limit, computation, localCacheWriteMs, redisCacheWriteMs, coldTotalMs);
        return computation.responses();
    }

    private List<PolicyRankingResponse> getRedisCachedRanking(int limit) {
        if (redisTemplate == null || objectMapper == null) {
            return null;
        }
        try {
            String cached = redisTemplate.opsForValue().get(redisCacheKey(limit));
            if (cached == null || cached.isBlank()) {
                return null;
            }
            return objectMapper.readValue(cached, RANKING_RESPONSE_LIST_TYPE);
        } catch (Exception e) {
            log.warn("[PolicyRankingService] Redis ranking cache read failed errorType={}", e.getClass().getSimpleName());
            return null;
        }
    }

    private void cacheRankingLocal(int limit, List<PolicyRankingResponse> responses) {
        rankingCache.put(limit, new CachedRanking(clock.millis(), responses));
    }

    private void cacheRankingRedis(int limit, List<PolicyRankingResponse> responses) {
        if (redisTemplate == null || objectMapper == null) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(
                    redisCacheKey(limit),
                    objectMapper.writeValueAsString(responses),
                    rankingCacheTtl
            );
        } catch (Exception e) {
            log.warn("[PolicyRankingService] Redis ranking cache write failed errorType={}", e.getClass().getSimpleName());
        }
    }

    private String redisCacheKey(int limit) {
        return RANKING_CACHE_PREFIX + limit;
    }

    private RankingComputation computeRanking(int limit) {
        long rankableStartedNanos = System.nanoTime();
        List<PolicyRankingReadRepository.RankableServiceSnapshot> snapshots = policyRankingReadRepository.findRankableSnapshots();
        long rankableSnapshotMs = elapsedMs(rankableStartedNanos);
        if (snapshots.isEmpty()) {
            return RankingComputation.empty(rankableSnapshotMs);
        }

        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime uniqueCutoff = now.minusDays(UNIQUE_VIEW_WINDOW_DAYS);
        long uniqueViewStartedNanos = System.nanoTime();
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
        long uniqueViewMs = elapsedMs(uniqueViewStartedNanos);

        long scoringStartedNanos = System.nanoTime();
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
        long scoringSortMs = elapsedMs(scoringStartedNanos);

        long explorationStartedNanos = System.nanoTime();
        List<ScoredSnapshot> selected = applyExplorationSlots(sortedByScore, snapshots, scoredByServiceId, limit);
        List<Long> selectedIds = selected.stream()
                .map(scored -> scored.snapshot().getId())
                .toList();
        long explorationMs = elapsedMs(explorationStartedNanos);

        long selectedServiceStartedNanos = System.nanoTime();
        Map<Long, WelfareService> selectedServicesById = new LinkedHashMap<>();
        for (WelfareService service : policyRankingReadRepository.findServicesByIds(selectedIds)) {
            selectedServicesById.put(service.getId(), service);
        }
        long selectedServiceLoadMs = elapsedMs(selectedServiceStartedNanos);

        List<WelfareService> selectedServices = selectedIds.stream()
                .map(selectedServicesById::get)
                .filter(java.util.Objects::nonNull)
                .toList();

        long projectionStartedNanos = System.nanoTime();
        Map<Long, RecommendationCandidateProjection> projections = policyPresentationReadService.findProjections(selectedServices);
        long projectionLoadMs = elapsedMs(projectionStartedNanos);

        long dtoStartedNanos = System.nanoTime();
        List<PolicyRankingResponse> responses = selected.stream()
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
        long dtoBuildMs = elapsedMs(dtoStartedNanos);

        return new RankingComputation(
                responses,
                snapshots.size(),
                uniqueViewsByServiceId.size(),
                selected.size(),
                selectedServices.size(),
                projections.size(),
                rankableSnapshotMs,
                uniqueViewMs,
                scoringSortMs,
                explorationMs,
                selectedServiceLoadMs,
                projectionLoadMs,
                dtoBuildMs
        );
    }

    private void logColdTimingIfSlow(int limit,
                                     RankingComputation computation,
                                     long localCacheWriteMs,
                                     long redisCacheWriteMs,
                                     long coldTotalMs) {
        if (coldTotalMs < coldTimingLogThresholdMs) {
            return;
        }
        log.info("[PolicyRankingColdTiming] totalMs={} limit={} resultCount={} snapshotCount={} uniqueViewServiceCount={} selectedCount={} selectedServiceCount={} projectionCount={} rankableSnapshotMs={} uniqueViewMs={} scoringSortMs={} explorationMs={} selectedServiceLoadMs={} projectionLoadMs={} dtoBuildMs={} localCacheWriteMs={} redisCacheWriteMs={}",
                coldTotalMs,
                limit,
                computation.responses().size(),
                computation.snapshotCount(),
                computation.uniqueViewServiceCount(),
                computation.selectedCount(),
                computation.selectedServiceCount(),
                computation.projectionCount(),
                computation.rankableSnapshotMs(),
                computation.uniqueViewMs(),
                computation.scoringSortMs(),
                computation.explorationMs(),
                computation.selectedServiceLoadMs(),
                computation.projectionLoadMs(),
                computation.dtoBuildMs(),
                localCacheWriteMs,
                redisCacheWriteMs);
    }

    private long elapsedMs(long startedNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);
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

    private record RankingComputation(List<PolicyRankingResponse> responses,
                                      int snapshotCount,
                                      int uniqueViewServiceCount,
                                      int selectedCount,
                                      int selectedServiceCount,
                                      int projectionCount,
                                      long rankableSnapshotMs,
                                      long uniqueViewMs,
                                      long scoringSortMs,
                                      long explorationMs,
                                      long selectedServiceLoadMs,
                                      long projectionLoadMs,
                                      long dtoBuildMs) {
        private static RankingComputation empty(long rankableSnapshotMs) {
            return new RankingComputation(
                    List.of(),
                    0,
                    0,
                    0,
                    0,
                    0,
                    rankableSnapshotMs,
                    0L,
                    0L,
                    0L,
                    0L,
                    0L,
                    0L
            );
        }
    }
}
