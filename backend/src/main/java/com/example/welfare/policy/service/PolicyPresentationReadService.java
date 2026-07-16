package com.example.welfare.policy.service;

import com.example.welfare.policy.dto.PolicySummaryResponse;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.repository.ServiceRegionRepository;
import com.example.welfare.policy.support.PolicyRegionLabelSupport;
import com.example.welfare.recommend.dto.RecommendationCandidateProjection;
import com.example.welfare.recommend.service.RecommendationBookmarkReadService;
import com.example.welfare.recommend.service.RecommendationProjectionReadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PolicyPresentationReadService {

    private static final long SLOW_SUMMARY_PRESENTATION_THRESHOLD_MS = 50;

    private final RecommendationBookmarkReadService recommendationBookmarkReadService;
    private final RecommendationProjectionReadService recommendationProjectionReadService;
    private final ServiceRegionRepository serviceRegionRepository;

    @Transactional(readOnly = true)
    public Page<PolicySummaryResponse> buildSummaryPage(Long userId, Page<WelfareService> page) {
        long totalStartedNanos = System.nanoTime();
        List<PolicySummaryResponse> responses = buildSummaryResponses(userId, page.getContent());
        long responseBuildMs = elapsedMs(totalStartedNanos);
        long mapStartedNanos = System.nanoTime();
        Map<Long, PolicySummaryResponse> responseById = responses.stream()
                .collect(Collectors.toMap(
                        PolicySummaryResponse::getId,
                        response -> response,
                        (left, right) -> left
                ));
        Page<PolicySummaryResponse> mapped = page.map(service -> responseById.getOrDefault(service.getId(), PolicySummaryResponse.from(service, false)));
        long mapMs = elapsedMs(mapStartedNanos);
        long totalMs = elapsedMs(totalStartedNanos);
        logSummaryPageTimingIfSlow(userId, page, responseBuildMs, mapMs, totalMs);
        return mapped;
    }

    @Transactional(readOnly = true)
    public List<PolicySummaryResponse> buildSummaryResponses(Long userId, List<WelfareService> services) {
        if (services == null || services.isEmpty()) {
            return List.of();
        }

        long totalStartedNanos = System.nanoTime();
        long bookmarkStartedNanos = System.nanoTime();
        Set<Long> bookmarkedServiceIds = recommendationBookmarkReadService.findBookmarkedServiceIds(userId, services);
        long bookmarkMs = elapsedMs(bookmarkStartedNanos);
        long projectionStartedNanos = System.nanoTime();
        Map<Long, RecommendationCandidateProjection> projections = findProjections(services);
        long projectionMs = elapsedMs(projectionStartedNanos);
        long regionStartedNanos = System.nanoTime();
        Map<Long, String> regionLabelMap = buildRegionLabelMap(services);
        long regionMs = elapsedMs(regionStartedNanos);

        long dtoStartedNanos = System.nanoTime();
        List<PolicySummaryResponse> responses = services.stream()
                .map(service -> PolicySummaryResponse.from(
                        service,
                        bookmarkedServiceIds.contains(service.getId()),
                        projections.get(service.getId()),
                        regionLabelMap.get(service.getId())
                ))
                .toList();
        long dtoBuildMs = elapsedMs(dtoStartedNanos);
        long totalMs = elapsedMs(totalStartedNanos);
        logSummaryResponsesTimingIfSlow(
                userId,
                services.size(),
                bookmarkedServiceIds.size(),
                projections.size(),
                regionLabelMap.size(),
                bookmarkMs,
                projectionMs,
                regionMs,
                dtoBuildMs,
                totalMs
        );
        return responses;
    }

    @Transactional(readOnly = true)
    public PolicyDetailPresentation buildDetailPresentation(Long userId, WelfareService service) {
        boolean bookmarked = recommendationBookmarkReadService.findBookmarkedServiceIds(userId, List.of(service))
                .contains(service.getId());
        RecommendationCandidateProjection projection = recommendationProjectionReadService
                .findCandidateProjectionsByServices(List.of(service))
                .get(service.getId());
        return new PolicyDetailPresentation(bookmarked, projection);
    }

    @Transactional(readOnly = true)
    public Map<Long, RecommendationCandidateProjection> findProjections(List<WelfareService> services) {
        if (services == null || services.isEmpty()) {
            return Map.of();
        }
        return recommendationProjectionReadService.findSummaryProjectionsByServices(services);
    }

    private Map<Long, String> buildRegionLabelMap(List<WelfareService> services) {
        if (services == null || services.isEmpty()) return Map.of();
        List<Long> ids = services.stream().map(WelfareService::getId).toList();
        Map<Long, List<PolicyRegionLabelSupport.RegionCandidate>> candidatesByServiceId =
                serviceRegionRepository.findRegionLabelCandidatesByServiceIds(ids).stream()
                .collect(Collectors.groupingBy(
                        row -> ((Number) row[0]).longValue(),
                        Collectors.mapping(
                                row -> new PolicyRegionLabelSupport.RegionCandidate((String) row[1], (String) row[2]),
                                Collectors.toList()
                        )
                ));
        Map<Long, String> resolved = new LinkedHashMap<>();
        for (WelfareService service : services) {
            resolved.put(
                    service.getId(),
                    PolicyRegionLabelSupport.resolvePreferredRegionLabelFromCandidates(
                            service,
                            candidatesByServiceId.get(service.getId())
                    )
            );
        }
        return resolved;
    }

    public record PolicyDetailPresentation(
            boolean bookmarked,
            RecommendationCandidateProjection projection
    ) {
    }

    private void logSummaryPageTimingIfSlow(Long userId,
                                            Page<WelfareService> page,
                                            long responseBuildMs,
                                            long mapMs,
                                            long totalMs) {
        if (totalMs < SLOW_SUMMARY_PRESENTATION_THRESHOLD_MS) {
            return;
        }
        log.info("[PolicyListPresentationPageTiming] totalMs={} responseBuildMs={} pageMapMs={} authenticated={} resultCount={} totalElements={} page={} size={}",
                totalMs,
                responseBuildMs,
                mapMs,
                userId != null,
                page.getNumberOfElements(),
                page.getTotalElements(),
                page.getNumber(),
                page.getSize());
    }

    private void logSummaryResponsesTimingIfSlow(Long userId,
                                                 int serviceCount,
                                                 int bookmarkedCount,
                                                 int projectionCount,
                                                 int regionLabelCount,
                                                 long bookmarkMs,
                                                 long projectionMs,
                                                 long regionMs,
                                                 long dtoBuildMs,
                                                 long totalMs) {
        if (totalMs < SLOW_SUMMARY_PRESENTATION_THRESHOLD_MS) {
            return;
        }
        log.info("[PolicyListPresentationTiming] totalMs={} bookmarkMs={} projectionMs={} regionMs={} dtoBuildMs={} authenticated={} serviceCount={} bookmarkedCount={} projectionCount={} regionLabelCount={}",
                totalMs,
                bookmarkMs,
                projectionMs,
                regionMs,
                dtoBuildMs,
                userId != null,
                serviceCount,
                bookmarkedCount,
                projectionCount,
                regionLabelCount);
    }

    private long elapsedMs(long startedNanos) {
        return Math.max(0, (System.nanoTime() - startedNanos) / 1_000_000);
    }
}
