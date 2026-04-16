package com.example.welfare.policy.service;

import com.example.welfare.policy.dto.PolicyRankingResponse;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.repository.WelfareServiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PolicyRankingService {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    private final WelfareServiceRepository welfareServiceRepository;

    @Transactional(readOnly = true)
    public List<PolicyRankingResponse> getRanking(int size) {
        int limit = normalizeSize(size);
        List<WelfareService> services = welfareServiceRepository.findByStatusIn(
                List.of(WelfareService.ServiceStatus.ACTIVE, WelfareService.ServiceStatus.UPCOMING));
        if (services.isEmpty()) return List.of();

        double maxLocalRaw = services.stream()
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

        long totalLocalViews = services.stream()
                .mapToLong(s -> safeLong(s.getViewCount()))
                .sum();

        WeightSet baseWeights = weightSetForTraffic(totalLocalViews);

        return services.stream()
                .map(service -> {
                    double localNorm = normalize(log1p(service.getViewCount()), maxLocalRaw);
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

                    double score = localNorm * applied.localWeight()
                            + externalNorm * applied.externalWeight()
                            + freshnessNorm * applied.freshnessWeight();

                    return ScoredService.builder()
                            .service(service)
                            .score(score)
                            .build();
                })
                .sorted(Comparator.comparingDouble(ScoredService::score).reversed())
                .limit(limit)
                .map(scored -> PolicyRankingResponse.of(scored.service(), round4(scored.score())))
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

    private WeightSet weightSetForTraffic(long totalLocalViews) {
        if (totalLocalViews < 100L) return new WeightSet(0.2, 0.7, 0.1);
        if (totalLocalViews < 1000L) return new WeightSet(0.5, 0.4, 0.1);
        return new WeightSet(0.8, 0.1, 0.1);
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

    private record WeightSet(double localWeight, double externalWeight, double freshnessWeight) {
        private WeightSet withoutExternal() {
            double sum = localWeight + freshnessWeight;
            if (sum <= 0.0) return this;
            return new WeightSet(localWeight / sum, 0.0, freshnessWeight / sum);
        }
    }

    @lombok.Builder
    private record ScoredService(WelfareService service, double score) {
    }
}
