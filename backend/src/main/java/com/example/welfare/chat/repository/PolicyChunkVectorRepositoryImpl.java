package com.example.welfare.chat.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Repository
@RequiredArgsConstructor
public class PolicyChunkVectorRepositoryImpl implements PolicyChunkVectorRepository {

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Override
    public List<PolicyChunkEmbeddingTarget> findEmbeddingTargets(List<Long> serviceIds) {
        if (serviceIds == null || serviceIds.isEmpty()) {
            return List.of();
        }

        return namedParameterJdbcTemplate.query("""
                        SELECT pc.id,
                               pc.service_id,
                               pc.chunk_text,
                               pc.embedding_model,
                               pc.embedding_text_hash
                        FROM policy_chunks pc
                        WHERE pc.service_id IN (:serviceIds)
                        ORDER BY pc.service_id ASC, pc.chunk_order ASC, pc.id ASC
                        """,
                new MapSqlParameterSource("serviceIds", serviceIds),
                (rs, rowNum) -> new PolicyChunkEmbeddingTarget(
                        rs.getLong("id"),
                        rs.getLong("service_id"),
                        rs.getString("chunk_text"),
                        rs.getString("embedding_model"),
                        rs.getString("embedding_text_hash")
                ));
    }

    @Override
    public void updateEmbedding(Long chunkId, float[] embedding, String embeddingModel, String embeddingTextHash, LocalDateTime updatedAt) {
        namedParameterJdbcTemplate.update("""
                        UPDATE policy_chunks
                        SET embedding = CAST(:embedding AS vector),
                            embedding_model = :embeddingModel,
                            embedding_text_hash = :embeddingTextHash,
                            embedding_updated_at = :embeddingUpdatedAt,
                            updated_at = :updatedAt
                        WHERE id = :id
                        """,
                new MapSqlParameterSource()
                        .addValue("id", chunkId, Types.BIGINT)
                        .addValue("embedding", toVectorLiteral(embedding), Types.VARCHAR)
                        .addValue("embeddingModel", embeddingModel, Types.VARCHAR)
                        .addValue("embeddingTextHash", embeddingTextHash, Types.VARCHAR)
                        .addValue("embeddingUpdatedAt", Timestamp.valueOf(updatedAt), Types.TIMESTAMP)
                        .addValue("updatedAt", Timestamp.valueOf(updatedAt), Types.TIMESTAMP));
    }

    @Override
    public List<SimilarPolicyChunk> findSimilarChunks(float[] queryEmbedding, int limit) {
        if (queryEmbedding == null || queryEmbedding.length == 0 || limit <= 0) {
            return List.of();
        }

        return namedParameterJdbcTemplate.query("""
                        SELECT pc.id,
                               pc.service_id,
                               pc.chunk_text,
                               1 - (pc.embedding <=> CAST(:queryEmbedding AS vector)) AS similarity
                        FROM policy_chunks pc
                        WHERE pc.embedding IS NOT NULL
                        ORDER BY pc.embedding <=> CAST(:queryEmbedding AS vector) ASC, pc.id ASC
                        LIMIT :limit
                        """,
                new MapSqlParameterSource()
                        .addValue("queryEmbedding", toVectorLiteral(queryEmbedding), Types.VARCHAR)
                        .addValue("limit", limit, Types.INTEGER),
                (rs, rowNum) -> new SimilarPolicyChunk(
                        rs.getLong("id"),
                        rs.getLong("service_id"),
                        rs.getString("chunk_text"),
                        rs.getDouble("similarity")
                ));
    }

    @Override
    public long countEmbeddings() {
        Long count = namedParameterJdbcTemplate.queryForObject("""
                        SELECT COUNT(*)
                        FROM policy_chunks
                        WHERE embedding IS NOT NULL
                        """,
                new MapSqlParameterSource(),
                Long.class);
        return count != null ? count : 0L;
    }

    private String toVectorLiteral(float[] embedding) {
        StringBuilder builder = new StringBuilder(embedding.length * 8);
        builder.append('[');
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(String.format(Locale.ROOT, "%.8f", embedding[i]));
        }
        builder.append(']');
        return builder.toString();
    }
}
