package com.example.welfare.chat.service;

import com.example.welfare.chat.gateway.ChatEmbeddingGateway;
import com.example.welfare.chat.repository.PolicyChunkVectorRepository;
import com.example.welfare.global.util.SensitiveTextRedactor;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.repository.WelfareServiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ChatSemanticSearchService {

    private final ChatEmbeddingGateway chatEmbeddingGateway;
    private final PolicyChunkVectorRepository policyChunkVectorRepository;
    private final WelfareServiceRepository welfareServiceRepository;

    @Transactional(readOnly = true)
    public List<WelfareService> findCandidates(String question,
                                               String preferredCategory,
                                               List<String> preferredTerms,
                                               int limit) {
        if (!StringUtils.hasText(question) || limit <= 0 || policyChunkVectorRepository.countEmbeddings() == 0L) {
            return List.of();
        }

        String semanticQuery = buildSemanticQuery(question, preferredTerms);
        float[] queryEmbedding = chatEmbeddingGateway.embedQuery(semanticQuery);
        List<PolicyChunkVectorRepository.SimilarPolicyChunk> similarChunks =
                policyChunkVectorRepository.findSimilarChunks(queryEmbedding, limit * 4);

        Set<Long> orderedServiceIds = new LinkedHashSet<>();
        for (PolicyChunkVectorRepository.SimilarPolicyChunk chunk : similarChunks) {
            orderedServiceIds.add(chunk.serviceId());
            if (orderedServiceIds.size() >= limit * 2L) {
                break;
            }
        }
        if (orderedServiceIds.isEmpty()) {
            return List.of();
        }

        Map<Long, WelfareService> servicesById = new LinkedHashMap<>();
        for (WelfareService service : welfareServiceRepository.findAllById(orderedServiceIds)) {
            servicesById.put(service.getId(), service);
        }

        return orderedServiceIds.stream()
                .map(servicesById::get)
                .filter(service -> service != null && service.isSearchYouthRelevant())
                .filter(service -> !StringUtils.hasText(preferredCategory)
                        || preferredCategory.equals(service.getUnifiedCategory()))
                .limit(limit)
                .toList();
    }

    private String buildSemanticQuery(String question, List<String> preferredTerms) {
        String sanitizedQuestion = SensitiveTextRedactor.redactDirectIdentifiers(question.trim());
        if (preferredTerms == null || preferredTerms.isEmpty()) {
            return sanitizedQuestion;
        }
        return sanitizedQuestion + " " + String.join(" ", preferredTerms);
    }
}
