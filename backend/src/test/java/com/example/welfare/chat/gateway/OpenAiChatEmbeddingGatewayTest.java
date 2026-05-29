package com.example.welfare.chat.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenAiChatEmbeddingGatewayTest {

    private OpenAiChatEmbeddingGateway gateway;

    @BeforeEach
    void setUp() {
        gateway = new OpenAiChatEmbeddingGateway(null, new ObjectMapper());
        ReflectionTestUtils.setField(gateway, "embeddingDimensions", 16);
        ReflectionTestUtils.setField(gateway, "forceLocalFallback", true);
    }

    @Test
    @DisplayName("local fallback 임베딩은 같은 입력에 대해 결정적이다")
    void embedDocumentsUsesDeterministicLocalFallback() {
        List<float[]> embeddings = gateway.embedDocuments(List.of("서울 청년 월세 지원", "서울 청년 월세 지원"));

        assertThat(embeddings).hasSize(2);
        assertThat(embeddings.get(0)).containsExactly(embeddings.get(1));
    }

    @Test
    @DisplayName("query 임베딩은 설정된 차원 수를 유지한다")
    void embedQueryReturnsConfiguredDimensions() {
        float[] embedding = gateway.embedQuery("주거비 부담 완화");

        assertThat(embedding).hasSize(16);
    }

    @Test
    @DisplayName("strict embedding refresh 경로는 local fallback 강제 상태에서 예외를 던진다")
    void embedDocumentsStrictFailsWhenOnlyLocalFallbackIsAvailable() {
        assertThatThrownBy(() -> gateway.embedDocumentsStrict(List.of("청년 주거 정책")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("strict embedding refresh");
    }
}
