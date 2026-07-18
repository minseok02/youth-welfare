package com.example.welfare.chat.service;

import com.example.welfare.chat.gateway.ChatEmbeddingGateway;
import com.example.welfare.chat.repository.PolicyChunkVectorRepository;
import com.example.welfare.global.util.SensitiveTextRedactor;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.repository.WelfareServiceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Slf4j
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
        long totalStart = System.nanoTime();
        if (!StringUtils.hasText(question) || limit <= 0) {
            log.info("[ChatSemanticSearchTiming] outcome=skipped reason=invalid_input preferredCategory={} preferredTerms={} requestedLimit={} totalDurationMs={}",
                    preferredCategory,
                    preferredTerms != null ? preferredTerms.size() : 0,
                    limit,
                    elapsedMs(totalStart));
            return List.of();
        }

        long countStart = System.nanoTime();
        long embeddingCount = policyChunkVectorRepository.countEmbeddings();
        long countDurationMs = elapsedMs(countStart);
        if (embeddingCount == 0L) {
            log.info("[ChatSemanticSearchTiming] outcome=skipped reason=no_embeddings preferredCategory={} preferredTerms={} requestedLimit={} embeddingCount={} countDurationMs={} totalDurationMs={}",
                    preferredCategory,
                    preferredTerms != null ? preferredTerms.size() : 0,
                    limit,
                    embeddingCount,
                    countDurationMs,
                    elapsedMs(totalStart));
            return List.of();
        }

        String semanticQuery = buildSemanticQuery(question, preferredTerms);
        long embeddingStart = System.nanoTime();
        float[] queryEmbedding = chatEmbeddingGateway.embedQuery(semanticQuery);
        long embeddingDurationMs = elapsedMs(embeddingStart);
        long vectorStart = System.nanoTime();
        List<PolicyChunkVectorRepository.SimilarPolicyChunk> similarChunks =
                policyChunkVectorRepository.findSimilarChunks(queryEmbedding, limit * 4);
        long vectorDurationMs = elapsedMs(vectorStart);

        Set<Long> orderedServiceIds = new LinkedHashSet<>();
        for (PolicyChunkVectorRepository.SimilarPolicyChunk chunk : similarChunks) {
            orderedServiceIds.add(chunk.serviceId());
            if (orderedServiceIds.size() >= limit * 2L) {
                break;
            }
        }
        if (orderedServiceIds.isEmpty()) {
            log.info("[ChatSemanticSearchTiming] outcome=empty preferredCategory={} preferredTerms={} requestedLimit={} embeddingCount={} similarChunks={} orderedServiceIds={} countDurationMs={} embeddingDurationMs={} vectorDurationMs={} loadServicesDurationMs={} totalDurationMs={}",
                    preferredCategory,
                    preferredTerms != null ? preferredTerms.size() : 0,
                    limit,
                    embeddingCount,
                    similarChunks.size(),
                    orderedServiceIds.size(),
                    countDurationMs,
                    embeddingDurationMs,
                    vectorDurationMs,
                    0,
                    elapsedMs(totalStart));
            return List.of();
        }

        Map<Long, WelfareService> servicesById = new LinkedHashMap<>();
        long loadServicesStart = System.nanoTime();
        for (WelfareService service : welfareServiceRepository.findAllById(orderedServiceIds)) {
            servicesById.put(service.getId(), service);
        }
        long loadServicesDurationMs = elapsedMs(loadServicesStart);

        List<WelfareService> results = orderedServiceIds.stream()
                .map(servicesById::get)
                .filter(service -> service != null && service.isSearchYouthRelevant())
                .filter(service -> !StringUtils.hasText(preferredCategory)
                        || preferredCategory.equals(service.getUnifiedCategory()))
                .limit(limit)
                .toList();
        log.info("[ChatSemanticSearchTiming] outcome=success preferredCategory={} preferredTerms={} requestedLimit={} embeddingCount={} similarChunks={} orderedServiceIds={} loadedServices={} resultCount={} countDurationMs={} embeddingDurationMs={} vectorDurationMs={} loadServicesDurationMs={} totalDurationMs={}",
                preferredCategory,
                preferredTerms != null ? preferredTerms.size() : 0,
                limit,
                embeddingCount,
                similarChunks.size(),
                orderedServiceIds.size(),
                servicesById.size(),
                results.size(),
                countDurationMs,
                embeddingDurationMs,
                vectorDurationMs,
                loadServicesDurationMs,
                elapsedMs(totalStart));
        return results;
    }

    private String buildSemanticQuery(String question, List<String> preferredTerms) {
        String sanitizedQuestion = SensitiveTextRedactor.redactDirectIdentifiers(question.trim());
        if (preferredTerms == null || preferredTerms.isEmpty()) {
            return sanitizedQuestion;
        }
        return sanitizedQuestion + " " + String.join(" ", preferredTerms);
    }

    private long elapsedMs(long startNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
    }
}
