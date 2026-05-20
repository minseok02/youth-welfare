package com.example.welfare.chat.service;

import com.example.welfare.chat.gateway.ChatEmbeddingGateway;
import com.example.welfare.chat.repository.PolicyChunkVectorRepository;
import com.example.welfare.policy.repository.WelfareServiceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChatSemanticSearchServiceTest {

    private CapturingEmbeddingGateway embeddingGateway;
    private PolicyChunkVectorRepository policyChunkVectorRepository;
    private WelfareServiceRepository welfareServiceRepository;
    private ChatSemanticSearchService chatSemanticSearchService;

    @BeforeEach
    void setUp() {
        embeddingGateway = new CapturingEmbeddingGateway();
        policyChunkVectorRepository = mock(PolicyChunkVectorRepository.class);
        welfareServiceRepository = mock(WelfareServiceRepository.class);
        when(policyChunkVectorRepository.countEmbeddings()).thenReturn(1L);
        when(policyChunkVectorRepository.findSimilarChunks(any(), anyInt())).thenReturn(List.of());
        chatSemanticSearchService = new ChatSemanticSearchService(
                embeddingGateway,
                policyChunkVectorRepository,
                welfareServiceRepository
        );
    }

    @Test
    @DisplayName("semantic query는 embedding 전송 전에 직접 식별자를 마스킹한다")
    void semanticQueryRedactsDirectIdentifiersBeforeEmbedding() {
        chatSemanticSearchService.findCandidates(
                "제 메일은 test.user@example.com 이고 전화는 010-1234-5678, 생일은 2001-04-30 입니다",
                null,
                List.of("청년", "주거"),
                3
        );

        assertThat(embeddingGateway.lastQuery).contains("[REDACTED_EMAIL]");
        assertThat(embeddingGateway.lastQuery).contains("[REDACTED_PHONE]");
        assertThat(embeddingGateway.lastQuery).contains("[REDACTED_BIRTH_DATE]");
        assertThat(embeddingGateway.lastQuery).contains("청년 주거");
        assertThat(embeddingGateway.lastQuery).doesNotContain("test.user@example.com");
        assertThat(embeddingGateway.lastQuery).doesNotContain("010-1234-5678");
        assertThat(embeddingGateway.lastQuery).doesNotContain("2001-04-30");
    }

    private static final class CapturingEmbeddingGateway implements ChatEmbeddingGateway {
        private String lastQuery;

        @Override
        public List<float[]> embedDocuments(List<String> texts) {
            return List.of();
        }

        @Override
        public float[] embedQuery(String text) {
            this.lastQuery = text;
            return new float[]{1.0f};
        }
    }
}
