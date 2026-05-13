package com.example.welfare.chat.service;

import com.example.welfare.chat.gateway.ChatEmbeddingGateway;
import com.example.welfare.chat.repository.PolicyChunkVectorRepository;
import com.example.welfare.global.util.HashSupport;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.repository.WelfareServiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class PolicyChunkEmbeddingService {

    private static final List<WelfareService.ServiceStatus> SEARCHABLE_STATUSES = List.of(
            WelfareService.ServiceStatus.ACTIVE,
            WelfareService.ServiceStatus.UPCOMING
    );

    private final ChatGroundingService chatGroundingService;
    private final ChatEmbeddingGateway chatEmbeddingGateway;
    private final PolicyChunkVectorRepository policyChunkVectorRepository;
    private final WelfareServiceRepository welfareServiceRepository;

    @Value("${openai.embedding.model:text-embedding-3-small}")
    private String embeddingModel;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public EmbeddingRefreshResult refreshEmbeddingsForSearchablePolicies() {
        return refreshEmbeddingsForServiceIds(
                welfareServiceRepository.findIdsBySearchYouthRelevantTrueAndStatusIn(SEARCHABLE_STATUSES)
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public EmbeddingRefreshResult refreshEmbeddingsForServiceIds(List<Long> serviceIds) {
        if (serviceIds == null || serviceIds.isEmpty()) {
            return new EmbeddingRefreshResult(0, 0, 0);
        }

        chatGroundingService.syncChunksForServiceIds(serviceIds);

        List<PolicyChunkVectorRepository.PolicyChunkEmbeddingTarget> targets =
                policyChunkVectorRepository.findEmbeddingTargets(serviceIds);

        List<PolicyChunkVectorRepository.PolicyChunkEmbeddingTarget> staleTargets = new ArrayList<>();
        for (PolicyChunkVectorRepository.PolicyChunkEmbeddingTarget target : targets) {
            String currentHash = HashSupport.sha256Hex(target.chunkText());
            if (isStaleTarget(target, currentHash)) {
                staleTargets.add(target);
            }
        }
        if (staleTargets.isEmpty()) {
            return new EmbeddingRefreshResult(serviceIds.size(), targets.size(), 0);
        }

        List<float[]> embeddings = chatEmbeddingGateway.embedDocuments(
                staleTargets.stream().map(PolicyChunkVectorRepository.PolicyChunkEmbeddingTarget::chunkText).toList()
        );
        LocalDateTime now = LocalDateTime.now();
        for (int i = 0; i < staleTargets.size(); i++) {
            PolicyChunkVectorRepository.PolicyChunkEmbeddingTarget target = staleTargets.get(i);
            policyChunkVectorRepository.updateEmbedding(
                    target.chunkId(),
                    embeddings.get(i),
                    embeddingModel,
                    HashSupport.sha256Hex(target.chunkText()),
                    now
            );
        }
        return new EmbeddingRefreshResult(serviceIds.size(), targets.size(), staleTargets.size());
    }

    private boolean isStaleTarget(PolicyChunkVectorRepository.PolicyChunkEmbeddingTarget target, String currentHash) {
        if (!currentHash.equals(target.embeddingTextHash())) {
            return true;
        }
        if (!StringUtils.hasText(target.embeddingModel())) {
            return true;
        }
        return !target.embeddingModel().trim().toLowerCase(Locale.ROOT)
                .equals(embeddingModel.trim().toLowerCase(Locale.ROOT));
    }

    public record EmbeddingRefreshResult(
            int requestedServiceCount,
            int scannedChunkCount,
            int refreshedChunkCount
    ) {
    }
}
