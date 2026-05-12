package com.example.welfare.chat.repository;

import java.time.LocalDateTime;
import java.util.List;

public interface PolicyChunkVectorRepository {

    List<PolicyChunkEmbeddingTarget> findEmbeddingTargets(List<Long> serviceIds);

    void updateEmbedding(Long chunkId, float[] embedding, String embeddingModel, String embeddingTextHash, LocalDateTime updatedAt);

    List<SimilarPolicyChunk> findSimilarChunks(float[] queryEmbedding, int limit);

    long countEmbeddings();

    record PolicyChunkEmbeddingTarget(
            Long chunkId,
            Long serviceId,
            String chunkText,
            String embeddingModel,
            String embeddingTextHash
    ) {
    }

    record SimilarPolicyChunk(
            Long chunkId,
            Long serviceId,
            String chunkText,
            double similarity
    ) {
    }
}
