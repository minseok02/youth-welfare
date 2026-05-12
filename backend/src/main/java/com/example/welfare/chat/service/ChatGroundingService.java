package com.example.welfare.chat.service;

import com.example.welfare.chat.dto.ChatPolicyCandidate;
import com.example.welfare.policy.entity.PolicyChunk;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.repository.PolicyChunkRepository;
import com.example.welfare.policy.repository.WelfareServiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ChatGroundingService {

    private final PolicyChunkRepository policyChunkRepository;
    private final WelfareServiceRepository welfareServiceRepository;

    @Transactional
    public Map<Long, String> loadEvidenceMap(List<ChatPolicyCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return Map.of();
        }

        List<Long> serviceIds = candidates.stream()
                .map(ChatPolicyCandidate::getServiceId)
                .filter(id -> id != null)
                .distinct()
                .toList();

        syncChunksForServiceIds(serviceIds);

        Map<Long, String> evidenceByServiceId = new LinkedHashMap<>();
        for (PolicyChunk chunk : policyChunkRepository.findByServiceIdInOrderByServiceIdAscChunkOrderAsc(serviceIds)) {
            evidenceByServiceId.putIfAbsent(chunk.getService().getId(), trimToLength(chunk.getChunkText(), 120));
        }
        return Map.copyOf(evidenceByServiceId);
    }

    @Transactional
    public void syncChunksForServiceIds(List<Long> serviceIds) {
        if (serviceIds == null || serviceIds.isEmpty()) {
            return;
        }

        Map<Long, WelfareService> serviceMap = new LinkedHashMap<>();
        for (WelfareService service : welfareServiceRepository.findAllById(serviceIds)) {
            serviceMap.put(service.getId(), service);
        }

        Map<String, PolicyChunk> existingByScope = new HashMap<>();
        List<PolicyChunk> existingChunks = policyChunkRepository.findByServiceIdIn(serviceIds);
        for (PolicyChunk chunk : existingChunks) {
            existingByScope.put(scopeKey(chunk.getService().getId(), chunk.getChunkType(), chunk.getChunkOrder()), chunk);
        }

        List<PolicyChunk> chunksToSave = new ArrayList<>();
        List<PolicyChunk> chunksToDelete = new ArrayList<>(existingChunks);
        boolean hasDirtyUpdates = false;

        for (Long serviceId : serviceIds) {
            WelfareService service = serviceMap.get(serviceId);
            if (service == null) {
                continue;
            }

            for (PolicyChunk desiredChunk : buildChunks(service)) {
                String scopeKey = scopeKey(serviceId, desiredChunk.getChunkType(), desiredChunk.getChunkOrder());
                PolicyChunk existingChunk = existingByScope.get(scopeKey);
                if (existingChunk == null) {
                    chunksToSave.add(desiredChunk);
                    continue;
                }
                chunksToDelete.remove(existingChunk);
                if (!desiredChunk.getChunkText().equals(existingChunk.getChunkText())) {
                    existingChunk.updateChunkText(desiredChunk.getChunkText());
                    hasDirtyUpdates = true;
                }
            }
        }

        if (!chunksToDelete.isEmpty()) {
            policyChunkRepository.deleteAllInBatch(chunksToDelete);
        }
        if (!chunksToSave.isEmpty()) {
            policyChunkRepository.saveAll(chunksToSave);
        }
        if (hasDirtyUpdates || !chunksToDelete.isEmpty() || !chunksToSave.isEmpty()) {
            policyChunkRepository.flush();
        }
    }

    private List<PolicyChunk> buildChunks(WelfareService service) {
        List<PolicyChunk> chunks = new ArrayList<>();
        addChunk(chunks, service, "SUPPORT_CONTENT", 0, service.getSupportContent());
        addChunk(chunks, service, "DESCRIPTION", 1, service.getDescription());
        addChunk(chunks, service, "APPLY_METHOD", 2, service.getApplyMethodName());
        addChunk(chunks, service, "TITLE", 3, service.getTitle());
        return chunks;
    }

    private void addChunk(List<PolicyChunk> chunks,
                          WelfareService service,
                          String chunkType,
                          int chunkOrder,
                          String chunkText) {
        if (!StringUtils.hasText(chunkText)) {
            return;
        }
        chunks.add(PolicyChunk.builder()
                .service(service)
                .chunkType(chunkType)
                .chunkOrder(chunkOrder)
                .chunkText(chunkText.trim())
                .build());
    }

    private String trimToLength(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private String scopeKey(Long serviceId, String chunkType, Integer chunkOrder) {
        return serviceId + "|" + chunkType + "|" + chunkOrder;
    }
}
