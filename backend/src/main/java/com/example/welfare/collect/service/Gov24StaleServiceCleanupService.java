package com.example.welfare.collect.service;

import com.example.welfare.collect.repository.RawApiPayloadRepository;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.repository.WelfareServiceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class Gov24StaleServiceCleanupService {

    static final int MIN_SAFE_LIVE_SERVICE_COUNT = 10_000;
    static final int MAX_SAFE_STALE_DELETE_COUNT = 100;
    static final double MAX_SAFE_STALE_DELETE_RATIO = 0.05d;

    private final WelfareServiceRepository welfareServiceRepository;
    private final RawApiPayloadRepository rawApiPayloadRepository;

    @Transactional
    public CleanupResult cleanupAgainstLiveInventory(List<String> liveSourceIds) {
        Set<String> normalizedLiveIds = normalizeSourceIds(liveSourceIds);
        List<String> existingSourceIds = welfareServiceRepository.findSourceIdsBySourceType(WelfareService.SourceType.GOV24);
        int existingCount = existingSourceIds.size();
        int liveCount = normalizedLiveIds.size();

        if (normalizedLiveIds.isEmpty()) {
            return CleanupResult.skipped(existingCount, liveCount, 0, "EMPTY_LIVE_SET");
        }
        if (liveCount < MIN_SAFE_LIVE_SERVICE_COUNT) {
            return CleanupResult.skipped(existingCount, liveCount, 0, "LIVE_COUNT_BELOW_THRESHOLD");
        }

        List<String> staleSourceIds = existingSourceIds.stream()
                .filter(sourceId -> !normalizedLiveIds.contains(sourceId))
                .toList();
        int staleCount = staleSourceIds.size();
        if (staleCount == 0) {
            return CleanupResult.deleted(existingCount, liveCount, 0, 0, List.of());
        }

        double staleRatio = existingCount == 0 ? 0d : ((double) staleCount / (double) existingCount);
        if (staleCount > MAX_SAFE_STALE_DELETE_COUNT || staleRatio > MAX_SAFE_STALE_DELETE_RATIO) {
            return CleanupResult.skipped(existingCount, liveCount, staleCount, "STALE_DELETE_GUARD");
        }

        rawApiPayloadRepository.deleteBySourceTypeAndSourceIdIn(WelfareService.SourceType.GOV24, staleSourceIds);
        int deletedCount = welfareServiceRepository.deleteBySourceTypeAndSourceIdIn(WelfareService.SourceType.GOV24, staleSourceIds);
        return CleanupResult.deleted(existingCount, liveCount, staleCount, deletedCount, staleSourceIds);
    }

    private Set<String> normalizeSourceIds(List<String> liveSourceIds) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (liveSourceIds == null) {
            return normalized;
        }
        for (String sourceId : liveSourceIds) {
            if (sourceId == null) {
                continue;
            }
            String trimmed = sourceId.trim();
            if (!trimmed.isEmpty()) {
                normalized.add(trimmed);
            }
        }
        return normalized;
    }

    public record CleanupResult(
            boolean skipped,
            String reason,
            int existingCount,
            int liveCount,
            int staleCount,
            int deletedCount,
            List<String> staleSourceIds
    ) {
        static CleanupResult skipped(int existingCount, int liveCount, int staleCount, String reason) {
            return new CleanupResult(true, reason, existingCount, liveCount, staleCount, 0, List.of());
        }

        static CleanupResult deleted(int existingCount, int liveCount, int staleCount, int deletedCount, List<String> staleSourceIds) {
            return new CleanupResult(false, "DELETED", existingCount, liveCount, staleCount, deletedCount, staleSourceIds);
        }
    }
}
