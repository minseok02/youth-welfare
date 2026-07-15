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
import java.util.PriorityQueue;
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
    private static final int DEFAULT_CANDIDATE_TARGET = 4000;
    private static final String CANDIDATE_MODE_POPULAR_RECENT_UNION = "popular_recent_union";
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
    private final boolean candidateModeEnabled;
    private final String candidateMode;
    private final int candidateTarget;
    private final Map<RankingCacheKey, CachedRanking> rankingCache = new ConcurrentHashMap<>();

    @Autowired
    public PolicyRankingService(PolicyRankingReadRepository policyRankingReadRepository,
                                PolicyPresentationReadService policyPresentationReadService,
                                RedisTemplate<String, String> redisTemplate,
                                ObjectMapper objectMapper,
                                @Value("${policy.cache.ranking.ttl-seconds:30}") long rankingCacheTtlSeconds,
                                @Value("${policy.ranking.cold-timing-log-threshold-ms:300}") long coldTimingLogThresholdMs,
                                @Value("${policy.ranking.candidate.enabled:false}") boolean candidateModeEnabled,
                                @Value("${policy.ranking.candidate.mode:popular_recent_union}") String candidateMode,
                                @Value("${policy.ranking.candidate.target:4000}") int candidateTarget) {
        this(
                policyRankingReadRepository,
                policyPresentationReadService,
                Clock.systemUTC(),
                redisTemplate,
                objectMapper,
                Duration.ofSeconds(rankingCacheTtlSeconds),
                coldTimingLogThresholdMs,
                candidateModeEnabled,
                candidateMode,
                candidateTarget
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
                DEFAULT_COLD_TIMING_LOG_THRESHOLD_MS,
                false,
                CANDIDATE_MODE_POPULAR_RECENT_UNION,
                DEFAULT_CANDIDATE_TARGET
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
                DEFAULT_COLD_TIMING_LOG_THRESHOLD_MS,
                false,
                CANDIDATE_MODE_POPULAR_RECENT_UNION,
                DEFAULT_CANDIDATE_TARGET
        );
    }

    PolicyRankingService(PolicyRankingReadRepository policyRankingReadRepository,
                         PolicyPresentationReadService policyPresentationReadService,
                         Clock clock,
                         RedisTemplate<String, String> redisTemplate,
                         ObjectMapper objectMapper,
                         Duration rankingCacheTtl,
                         boolean candidateModeEnabled,
                         String candidateMode,
                         int candidateTarget) {
        this(
                policyRankingReadRepository,
                policyPresentationReadService,
                clock,
                redisTemplate,
                objectMapper,
                rankingCacheTtl,
                DEFAULT_COLD_TIMING_LOG_THRESHOLD_MS,
                candidateModeEnabled,
                candidateMode,
                candidateTarget
        );
    }

    PolicyRankingService(PolicyRankingReadRepository policyRankingReadRepository,
                         PolicyPresentationReadService policyPresentationReadService,
                         Clock clock,
                         RedisTemplate<String, String> redisTemplate,
                         ObjectMapper objectMapper,
                         Duration rankingCacheTtl,
                         long coldTimingLogThresholdMs,
                         boolean candidateModeEnabled,
                         String candidateMode,
                         int candidateTarget) {
        this.policyRankingReadRepository = policyRankingReadRepository;
        this.policyPresentationReadService = policyPresentationReadService;
        this.clock = clock;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.rankingCacheTtl = rankingCacheTtl.isNegative() || rankingCacheTtl.isZero()
                ? Duration.ofMillis(RANKING_CACHE_TTL_MILLIS)
                : rankingCacheTtl;
        this.coldTimingLogThresholdMs = Math.max(0L, coldTimingLogThresholdMs);
        this.candidateModeEnabled = candidateModeEnabled;
        this.candidateMode = normalizeCandidateMode(candidateMode);
        this.candidateTarget = Math.max(1, candidateTarget);
    }

    @Transactional(readOnly = true)
    public List<PolicyRankingResponse> getRanking(int size) {
        int limit = normalizeSize(size);
        RankingCacheKey cacheKey = rankingCacheKey(limit);
        CachedRanking cachedRanking = rankingCache.get(cacheKey);
        long nowMillis = clock.millis();
        if (cachedRanking != null && nowMillis - cachedRanking.cachedAtMillis() < rankingCacheTtl.toMillis()) {
            return cachedRanking.responses();
        }
        List<PolicyRankingResponse> redisCached = getRedisCachedRanking(cacheKey);
        if (redisCached != null) {
            cacheRankingLocal(cacheKey, redisCached);
            return redisCached;
        }

        long coldStartedNanos = System.nanoTime();
        RankingComputation computation = computeRanking(limit);
        long localCacheStartedNanos = System.nanoTime();
        cacheRankingLocal(cacheKey, computation.responses());
        long localCacheWriteMs = elapsedMs(localCacheStartedNanos);
        long redisCacheStartedNanos = System.nanoTime();
        cacheRankingRedis(cacheKey, computation.responses());
        long redisCacheWriteMs = elapsedMs(redisCacheStartedNanos);
        long coldTotalMs = elapsedMs(coldStartedNanos);
        logColdTimingIfSlow(limit, computation, localCacheWriteMs, redisCacheWriteMs, coldTotalMs);
        return computation.responses();
    }

    private List<PolicyRankingResponse> getRedisCachedRanking(RankingCacheKey cacheKey) {
        if (redisTemplate == null || objectMapper == null) {
            return null;
        }
        try {
            String cached = redisTemplate.opsForValue().get(redisCacheKey(cacheKey));
            if (cached == null || cached.isBlank()) {
                return null;
            }
            return objectMapper.readValue(cached, RANKING_RESPONSE_LIST_TYPE);
        } catch (Exception e) {
            log.warn("[PolicyRankingService] Redis ranking cache read failed errorType={}", e.getClass().getSimpleName());
            return null;
        }
    }

    private void cacheRankingLocal(RankingCacheKey cacheKey, List<PolicyRankingResponse> responses) {
        rankingCache.put(cacheKey, new CachedRanking(clock.millis(), responses));
    }

    private void cacheRankingRedis(RankingCacheKey cacheKey, List<PolicyRankingResponse> responses) {
        if (redisTemplate == null || objectMapper == null) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(
                    redisCacheKey(cacheKey),
                    objectMapper.writeValueAsString(responses),
                    rankingCacheTtl
            );
        } catch (Exception e) {
            log.warn("[PolicyRankingService] Redis ranking cache write failed errorType={}", e.getClass().getSimpleName());
        }
    }

    private RankingCacheKey rankingCacheKey(int limit) {
        CandidateConfig candidateConfig = candidateConfig();
        return new RankingCacheKey(limit, candidateConfig.cacheVariant());
    }

    private String redisCacheKey(RankingCacheKey cacheKey) {
        return RANKING_CACHE_PREFIX + cacheKey.variant() + ":" + cacheKey.limit();
    }

    private CandidateConfig candidateConfig() {
        boolean active = candidateModeEnabled && CANDIDATE_MODE_POPULAR_RECENT_UNION.equals(candidateMode);
        return new CandidateConfig(active, candidateMode, candidateTarget);
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
        RankingNormalizationStats normalizationStats = new RankingNormalizationStats(
                maxUniqueRaw,
                maxViewRaw,
                maxExternalBySource
        );

        long totalUniqueViews = uniqueViewsByServiceId.values().stream()
                .mapToLong(this::safeLong)
                .sum();

        WeightSet baseWeights = weightSetForTraffic(totalUniqueViews);
        CandidateConfig candidateConfig = candidateConfig();
        long candidateSelectionStartedNanos = System.nanoTime();
        List<PolicyRankingReadRepository.RankableServiceSnapshot> scoringSnapshots = selectScoringSnapshots(
                snapshots,
                uniqueViewsByServiceId,
                normalizationStats,
                now,
                candidateConfig
        );
        long candidateSelectionMs = elapsedMs(candidateSelectionStartedNanos);

        long scoringStartedNanos = System.nanoTime();
        List<ScoredSnapshot> sortedByScore = scoringSnapshots.stream()
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
        List<ScoredSnapshot> selected = applyExplorationSlots(sortedByScore, scoringSnapshots, scoredByServiceId, limit);
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
                scoringSnapshots.size(),
                uniqueViewsByServiceId.size(),
                selected.size(),
                selectedServices.size(),
                projections.size(),
                candidateConfig.active(),
                candidateConfig.mode(),
                candidateConfig.target(),
                rankableSnapshotMs,
                uniqueViewMs,
                candidateSelectionMs,
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
        log.info("[PolicyRankingColdTiming] totalMs={} limit={} resultCount={} snapshotCount={} scoringCandidateCount={} uniqueViewServiceCount={} selectedCount={} selectedServiceCount={} projectionCount={} candidateModeEnabled={} candidateMode={} candidateTarget={} rankableSnapshotMs={} uniqueViewMs={} candidateSelectionMs={} scoringSortMs={} explorationMs={} selectedServiceLoadMs={} projectionLoadMs={} dtoBuildMs={} localCacheWriteMs={} redisCacheWriteMs={}",
                coldTotalMs,
                limit,
                computation.responses().size(),
                computation.snapshotCount(),
                computation.scoringCandidateCount(),
                computation.uniqueViewServiceCount(),
                computation.selectedCount(),
                computation.selectedServiceCount(),
                computation.projectionCount(),
                computation.candidateModeEnabled(),
                computation.candidateMode(),
                computation.candidateTarget(),
                computation.rankableSnapshotMs(),
                computation.uniqueViewMs(),
                computation.candidateSelectionMs(),
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

    private String normalizeCandidateMode(String value) {
        if (value == null || value.isBlank()) {
            return CANDIDATE_MODE_POPULAR_RECENT_UNION;
        }
        return value.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private List<PolicyRankingReadRepository.RankableServiceSnapshot> selectScoringSnapshots(
            List<PolicyRankingReadRepository.RankableServiceSnapshot> snapshots,
            Map<Long, Long> uniqueViewsByServiceId,
            RankingNormalizationStats normalizationStats,
            LocalDateTime now,
            CandidateConfig candidateConfig
    ) {
        if (!candidateConfig.active() || snapshots.size() <= candidateConfig.target()) {
            return snapshots;
        }
        return popularRecentUnionCandidates(snapshots, uniqueViewsByServiceId, normalizationStats, now, candidateConfig.target());
    }

    private List<PolicyRankingReadRepository.RankableServiceSnapshot> popularRecentUnionCandidates(
            List<PolicyRankingReadRepository.RankableServiceSnapshot> snapshots,
            Map<Long, Long> uniqueViewsByServiceId,
            RankingNormalizationStats normalizationStats,
            LocalDateTime now,
            int target
    ) {
        int popularCount = Math.max(1, Math.round(target * 0.55f));
        int recentCount = Math.max(1, Math.round(target * 0.25f));
        int quotaCount = Math.max(1, Math.round(target * 0.20f));

        List<RoughRankedSnapshot> roughEntries = snapshots.stream()
                .map(snapshot -> new RoughRankedSnapshot(
                        snapshot,
                        roughPreRankScore(snapshot, uniqueViewsByServiceId, normalizationStats, now)
                ))
                .toList();
        List<PolicyRankingReadRepository.RankableServiceSnapshot> rankedByRough = topRoughSnapshots(roughEntries, target);
        List<PolicyRankingReadRepository.RankableServiceSnapshot> rankedByRecent = topRecentSnapshots(snapshots, recentCount);

        List<PolicyRankingReadRepository.RankableServiceSnapshot> selected = new ArrayList<>();
        selected.addAll(rankedByRough.stream().limit(popularCount).toList());
        selected.addAll(rankedByRecent);
        selected.addAll(sourceQuotaCandidates(snapshots, roughEntries, rankedByRough, quotaCount));

        Map<Long, PolicyRankingReadRepository.RankableServiceSnapshot> byId = snapshots.stream()
                .collect(Collectors.toMap(
                        PolicyRankingReadRepository.RankableServiceSnapshot::getId,
                        Function.identity(),
                        (a, b) -> a
                ));
        for (Long serviceId : uniqueViewsByServiceId.keySet()) {
            PolicyRankingReadRepository.RankableServiceSnapshot snapshot = byId.get(serviceId);
            if (snapshot != null) {
                selected.add(snapshot);
            }
        }

        List<PolicyRankingReadRepository.RankableServiceSnapshot> uniqueSelected = orderedUniqueSnapshots(selected);
        if (uniqueSelected.size() < target) {
            uniqueSelected = fillToTarget(uniqueSelected, rankedByRough, target);
        }
        return uniqueSelected;
    }

    private List<PolicyRankingReadRepository.RankableServiceSnapshot> sourceQuotaCandidates(
            List<PolicyRankingReadRepository.RankableServiceSnapshot> snapshots,
            List<RoughRankedSnapshot> roughEntries,
            List<PolicyRankingReadRepository.RankableServiceSnapshot> rankedByRough,
            int target
    ) {
        Map<WelfareService.SourceType, List<RoughRankedSnapshot>> bySource = new EnumMap<>(WelfareService.SourceType.class);
        for (RoughRankedSnapshot entry : roughEntries) {
            bySource.computeIfAbsent(entry.snapshot().getSourceType(), ignored -> new ArrayList<>()).add(entry);
        }

        int total = snapshots.size();
        int minPerSource = Math.max(25, target / 20);
        List<PolicyRankingReadRepository.RankableServiceSnapshot> selected = new ArrayList<>();
        for (WelfareService.SourceType sourceType : WelfareService.SourceType.values()) {
            List<RoughRankedSnapshot> rows = bySource.get(sourceType);
            if (rows == null || rows.isEmpty()) {
                continue;
            }
            int proportional = Math.round(target * (rows.size() / (float) total));
            int quota = Math.min(rows.size(), Math.max(minPerSource, proportional));
            selected.addAll(topRoughSnapshots(rows, quota));
        }

        List<PolicyRankingReadRepository.RankableServiceSnapshot> uniqueSelected = orderedUniqueSnapshots(selected);
        if (uniqueSelected.size() < target) {
            uniqueSelected = fillToTarget(uniqueSelected, rankedByRough, target);
        }
        return uniqueSelected;
    }

    private List<PolicyRankingReadRepository.RankableServiceSnapshot> topRoughSnapshots(
            List<RoughRankedSnapshot> entries,
            int limit
    ) {
        if (limit <= 0 || entries.isEmpty()) {
            return List.of();
        }
        PriorityQueue<RoughRankedSnapshot> heap = new PriorityQueue<>(this::compareRoughWorstFirst);
        for (RoughRankedSnapshot entry : entries) {
            if (heap.size() < limit) {
                heap.offer(entry);
            } else if (isRoughBetter(entry, heap.peek())) {
                heap.poll();
                heap.offer(entry);
            }
        }
        return heap.stream()
                .sorted(this::compareRoughBestFirst)
                .map(RoughRankedSnapshot::snapshot)
                .toList();
    }

    private int compareRoughWorstFirst(RoughRankedSnapshot left, RoughRankedSnapshot right) {
        int byScore = Double.compare(left.score(), right.score());
        if (byScore != 0) {
            return byScore;
        }
        return Long.compare(safeServiceId(right.snapshot()), safeServiceId(left.snapshot()));
    }

    private int compareRoughBestFirst(RoughRankedSnapshot left, RoughRankedSnapshot right) {
        int byScore = Double.compare(right.score(), left.score());
        if (byScore != 0) {
            return byScore;
        }
        return Long.compare(safeServiceId(left.snapshot()), safeServiceId(right.snapshot()));
    }

    private boolean isRoughBetter(RoughRankedSnapshot candidate, RoughRankedSnapshot currentWorst) {
        return compareRoughBestFirst(candidate, currentWorst) < 0;
    }

    private List<PolicyRankingReadRepository.RankableServiceSnapshot> topRecentSnapshots(
            List<PolicyRankingReadRepository.RankableServiceSnapshot> snapshots,
            int limit
    ) {
        if (limit <= 0 || snapshots.isEmpty()) {
            return List.of();
        }
        PriorityQueue<RecentRankedSnapshot> heap = new PriorityQueue<>(this::compareRecentWorstFirst);
        for (PolicyRankingReadRepository.RankableServiceSnapshot snapshot : snapshots) {
            RecentRankedSnapshot entry = new RecentRankedSnapshot(snapshot, policyBaseTime(snapshot));
            if (heap.size() < limit) {
                heap.offer(entry);
            } else if (isRecentBetter(entry, heap.peek())) {
                heap.poll();
                heap.offer(entry);
            }
        }
        return heap.stream()
                .sorted(this::compareRecentBestFirst)
                .map(RecentRankedSnapshot::snapshot)
                .toList();
    }

    private int compareRecentWorstFirst(RecentRankedSnapshot left, RecentRankedSnapshot right) {
        int byTime = compareBaseTimeWorstFirst(left.baseTime(), right.baseTime());
        if (byTime != 0) {
            return byTime;
        }
        return Long.compare(safeServiceId(right.snapshot()), safeServiceId(left.snapshot()));
    }

    private int compareRecentBestFirst(RecentRankedSnapshot left, RecentRankedSnapshot right) {
        int byTime = compareBaseTimeBestFirst(left.baseTime(), right.baseTime());
        if (byTime != 0) {
            return byTime;
        }
        return Long.compare(safeServiceId(left.snapshot()), safeServiceId(right.snapshot()));
    }

    private int compareBaseTimeWorstFirst(LocalDateTime left, LocalDateTime right) {
        if (left == null && right == null) {
            return 0;
        }
        if (left == null) {
            return -1;
        }
        if (right == null) {
            return 1;
        }
        return left.compareTo(right);
    }

    private int compareBaseTimeBestFirst(LocalDateTime left, LocalDateTime right) {
        if (left == null && right == null) {
            return 0;
        }
        if (left == null) {
            return 1;
        }
        if (right == null) {
            return -1;
        }
        return right.compareTo(left);
    }

    private boolean isRecentBetter(RecentRankedSnapshot candidate, RecentRankedSnapshot currentWorst) {
        return compareRecentBestFirst(candidate, currentWorst) < 0;
    }

    private long safeServiceId(PolicyRankingReadRepository.RankableServiceSnapshot snapshot) {
        Long id = snapshot.getId();
        return id == null ? Long.MAX_VALUE : id;
    }

    private List<PolicyRankingReadRepository.RankableServiceSnapshot> orderedUniqueSnapshots(
            List<PolicyRankingReadRepository.RankableServiceSnapshot> snapshots
    ) {
        Map<Long, PolicyRankingReadRepository.RankableServiceSnapshot> byId = new LinkedHashMap<>();
        for (PolicyRankingReadRepository.RankableServiceSnapshot snapshot : snapshots) {
            byId.putIfAbsent(snapshot.getId(), snapshot);
        }
        return new ArrayList<>(byId.values());
    }

    private List<PolicyRankingReadRepository.RankableServiceSnapshot> fillToTarget(
            List<PolicyRankingReadRepository.RankableServiceSnapshot> selected,
            List<PolicyRankingReadRepository.RankableServiceSnapshot> rankedByRough,
            int target
    ) {
        List<PolicyRankingReadRepository.RankableServiceSnapshot> result = new ArrayList<>(selected);
        Set<Long> selectedIds = result.stream()
                .map(PolicyRankingReadRepository.RankableServiceSnapshot::getId)
                .collect(Collectors.toSet());
        for (PolicyRankingReadRepository.RankableServiceSnapshot snapshot : rankedByRough) {
            if (result.size() >= target) {
                break;
            }
            if (selectedIds.add(snapshot.getId())) {
                result.add(snapshot);
            }
        }
        return result;
    }

    private double roughPreRankScore(PolicyRankingReadRepository.RankableServiceSnapshot snapshot,
                                     Map<Long, Long> uniqueViewsByServiceId,
                                     RankingNormalizationStats normalizationStats,
                                     LocalDateTime now) {
        double viewNorm = normalize(log1p(snapshot.getViewCount()), normalizationStats.maxViewRaw());
        double externalNorm = normalize(
                log1p(snapshot.getApiViewCount()),
                normalizationStats.maxExternalBySource().getOrDefault(snapshot.getSourceType(), 0.0)
        );
        double uniqueNorm = normalize(
                log1p(uniqueViewsByServiceId.getOrDefault(snapshot.getId(), 0L)),
                normalizationStats.maxUniqueRaw()
        );
        double freshnessNorm = freshnessScore(snapshot, now);
        return viewNorm * 0.42
                + externalNorm * 0.25
                + freshnessNorm * 0.23
                + uniqueNorm * 0.10;
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

    private record RankingCacheKey(int limit, String variant) {
    }

    private record CandidateConfig(boolean active, String mode, int target) {
        private String cacheVariant() {
            if (!active) {
                return "full";
            }
            return mode + ":" + target;
        }
    }

    private record RankingNormalizationStats(double maxUniqueRaw,
                                             double maxViewRaw,
                                             Map<WelfareService.SourceType, Double> maxExternalBySource) {
    }

    private record RoughRankedSnapshot(PolicyRankingReadRepository.RankableServiceSnapshot snapshot, double score) {
    }

    private record RecentRankedSnapshot(PolicyRankingReadRepository.RankableServiceSnapshot snapshot,
                                        LocalDateTime baseTime) {
    }

    private record RankingComputation(List<PolicyRankingResponse> responses,
                                      int snapshotCount,
                                      int scoringCandidateCount,
                                      int uniqueViewServiceCount,
                                      int selectedCount,
                                      int selectedServiceCount,
                                      int projectionCount,
                                      boolean candidateModeEnabled,
                                      String candidateMode,
                                      int candidateTarget,
                                      long rankableSnapshotMs,
                                      long uniqueViewMs,
                                      long candidateSelectionMs,
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
                    0,
                    false,
                    CANDIDATE_MODE_POPULAR_RECENT_UNION,
                    DEFAULT_CANDIDATE_TARGET,
                    rankableSnapshotMs,
                    0L,
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
