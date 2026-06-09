package com.example.welfare.collect.repository;

import com.example.welfare.policy.entity.WelfareService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface CollectListSnapshotRepository {

    List<ListItemFingerprint> fetchCurrentItems(WelfareService.SourceType sourceType);

    Optional<ListSnapshot> findLatestSnapshot(WelfareService.SourceType sourceType);

    Map<String, String> fetchSnapshotItems(long snapshotId);

    long saveSnapshot(SaveSnapshotCommand command, List<ListItemFingerprint> items);

    record ListItemFingerprint(String sourceId, String fingerprintHash) {
    }

    record ListSnapshot(
            long id,
            WelfareService.SourceType sourceType,
            String jobName,
            LocalDateTime collectedAt,
            int totalCount,
            int newCount,
            int changedCount,
            int missingCount
    ) {
    }

    record SaveSnapshotCommand(
            WelfareService.SourceType sourceType,
            String jobName,
            int totalCount,
            int newCount,
            int changedCount,
            int missingCount,
            String metadataJson
    ) {
    }
}
