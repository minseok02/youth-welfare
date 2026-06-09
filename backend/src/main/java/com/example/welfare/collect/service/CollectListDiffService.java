package com.example.welfare.collect.service;

import com.example.welfare.collect.repository.CollectListSnapshotRepository;
import com.example.welfare.policy.entity.WelfareService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CollectListDiffService {

    private final CollectListSnapshotRepository collectListSnapshotRepository;
    private final ObjectMapper objectMapper;

    public CollectListDiff recordSnapshot(CollectSource source, CollectResult listResult) {
        if (!source.isListSource()) {
            throw new IllegalArgumentException("list diff snapshot is only supported for list source=" + source);
        }

        WelfareService.SourceType sourceType = source.toWelfareSourceType();
        List<CollectListSnapshotRepository.ListItemFingerprint> currentItems =
                collectListSnapshotRepository.fetchCurrentItems(sourceType);
        Map<String, String> current = toFingerprintMap(currentItems);

        var previousSnapshot = collectListSnapshotRepository.findLatestSnapshot(sourceType);
        if (previousSnapshot.isEmpty()) {
            String metadataJson = metadataJson(source, listResult, null, true, List.of(), List.of(), List.of());
            long snapshotId = collectListSnapshotRepository.saveSnapshot(
                    new CollectListSnapshotRepository.SaveSnapshotCommand(
                            sourceType,
                            source.jobName(),
                            currentItems.size(),
                            0,
                            0,
                            0,
                            metadataJson
                    ),
                    currentItems
            );
            log.info("[CollectListDiffService] baseline snapshot created source={} snapshotId={} total={}",
                    source.jobName(), snapshotId, currentItems.size());
            return new CollectListDiff(source, snapshotId, null, true, currentItems.size(), 0, 0, 0, List.of(), List.of(), List.of());
        }

        Map<String, String> previous = collectListSnapshotRepository.fetchSnapshotItems(previousSnapshot.get().id());
        List<String> newSourceIds = new ArrayList<>();
        List<String> changedSourceIds = new ArrayList<>();
        List<String> missingSourceIds = new ArrayList<>();

        for (Map.Entry<String, String> entry : current.entrySet()) {
            String previousHash = previous.get(entry.getKey());
            if (previousHash == null) {
                newSourceIds.add(entry.getKey());
            } else if (!previousHash.equals(entry.getValue())) {
                changedSourceIds.add(entry.getKey());
            }
        }
        for (String previousSourceId : previous.keySet()) {
            if (!current.containsKey(previousSourceId)) {
                missingSourceIds.add(previousSourceId);
            }
        }

        String metadataJson = metadataJson(
                source,
                listResult,
                previousSnapshot.get().id(),
                false,
                newSourceIds,
                changedSourceIds,
                missingSourceIds
        );
        long snapshotId = collectListSnapshotRepository.saveSnapshot(
                new CollectListSnapshotRepository.SaveSnapshotCommand(
                        sourceType,
                        source.jobName(),
                        currentItems.size(),
                        newSourceIds.size(),
                        changedSourceIds.size(),
                        missingSourceIds.size(),
                        metadataJson
                ),
                currentItems
        );

        log.info("[CollectListDiffService] snapshot diff source={} snapshotId={} previousSnapshotId={} total={} new={} changed={} missing={}",
                source.jobName(),
                snapshotId,
                previousSnapshot.get().id(),
                currentItems.size(),
                newSourceIds.size(),
                changedSourceIds.size(),
                missingSourceIds.size());
        return new CollectListDiff(
                source,
                snapshotId,
                previousSnapshot.get().id(),
                false,
                currentItems.size(),
                newSourceIds.size(),
                changedSourceIds.size(),
                missingSourceIds.size(),
                newSourceIds,
                changedSourceIds,
                missingSourceIds
        );
    }

    private Map<String, String> toFingerprintMap(List<CollectListSnapshotRepository.ListItemFingerprint> items) {
        Map<String, String> map = new LinkedHashMap<>();
        for (CollectListSnapshotRepository.ListItemFingerprint item : items) {
            map.put(item.sourceId(), item.fingerprintHash());
        }
        return map;
    }

    private String metadataJson(CollectSource source,
                                CollectResult listResult,
                                Long previousSnapshotId,
                                boolean baseline,
                                List<String> newSourceIds,
                                List<String> changedSourceIds,
                                List<String> missingSourceIds) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("source", source.jobName());
        metadata.put("baseline", baseline);
        metadata.put("previousSnapshotId", previousSnapshotId);
        metadata.put("listRequestedCount", listResult.requestedCount());
        metadata.put("listSavedCount", listResult.savedCount());
        metadata.put("listSkippedCount", listResult.skippedCount());
        metadata.put("listFilteredCount", listResult.filteredCount());
        metadata.put("listFailedCount", listResult.failedCount());
        metadata.put("newSample", newSourceIds.stream().limit(20).toList());
        metadata.put("changedSample", changedSourceIds.stream().limit(20).toList());
        metadata.put("missingSample", missingSourceIds.stream().limit(20).toList());
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("collect list diff metadata serialization failed", e);
        }
    }

    public record CollectListDiff(
            CollectSource source,
            long snapshotId,
            Long previousSnapshotId,
            boolean baseline,
            int totalCount,
            int newCount,
            int changedCount,
            int missingCount,
            List<String> newSourceIds,
            List<String> changedSourceIds,
            List<String> missingSourceIds
    ) {
        public int changedOrNewCount() {
            return newCount + changedCount;
        }

        public List<String> prioritizedDetailCandidates(int limit) {
            if (limit <= 0) {
                return List.of();
            }
            List<String> candidates = new ArrayList<>(limit);
            for (String sourceId : newSourceIds) {
                if (candidates.size() >= limit) {
                    return List.copyOf(candidates);
                }
                candidates.add(sourceId);
            }
            for (String sourceId : changedSourceIds) {
                if (candidates.size() >= limit) {
                    return List.copyOf(candidates);
                }
                candidates.add(sourceId);
            }
            return List.copyOf(candidates);
        }
    }
}
