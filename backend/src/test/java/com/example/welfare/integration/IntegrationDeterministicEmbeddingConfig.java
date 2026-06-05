package com.example.welfare.integration;

import com.example.welfare.chat.gateway.ChatEmbeddingGateway;
import com.example.welfare.global.util.SearchKeywordSupport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;

@Configuration
@Profile("integration")
class IntegrationDeterministicEmbeddingConfig {

    @Bean
    @Primary
    ChatEmbeddingGateway integrationChatEmbeddingGateway(
            @Value("${openai.embedding.dimensions:256}") int embeddingDimensions
    ) {
        return new ChatEmbeddingGateway() {
            @Override
            public List<float[]> embedDocuments(List<String> texts) {
                return embed(texts, embeddingDimensions);
            }

            @Override
            public List<float[]> embedDocumentsStrict(List<String> texts) {
                return embed(texts, embeddingDimensions);
            }

            @Override
            public float[] embedQuery(String text) {
                List<float[]> embeddings = embed(List.of(text), embeddingDimensions);
                return embeddings.isEmpty() ? new float[embeddingDimensions] : embeddings.get(0);
            }
        };
    }

    private static List<float[]> embed(List<String> texts, int dimensions) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        int safeDimensions = Math.max(1, dimensions);
        return texts.stream()
                .map(text -> createEmbedding(text, safeDimensions))
                .toList();
    }

    private static float[] createEmbedding(String text, int dimensions) {
        List<String> tokens = SearchKeywordSupport.extractTokens(text);
        if (tokens.isEmpty() && StringUtils.hasText(text)) {
            tokens = List.of(text.trim().toLowerCase(Locale.ROOT));
        }

        float[] vector = new float[dimensions];
        if (tokens.isEmpty()) {
            vector[0] = 1.0f;
            return vector;
        }

        for (String token : tokens) {
            int baseHash = token.hashCode();
            int secondaryHash = (token + "#").hashCode();
            vector[Math.floorMod(baseHash, dimensions)] += 1.0f;
            vector[Math.floorMod(secondaryHash, dimensions)] += (secondaryHash & 1) == 0 ? 0.5f : -0.5f;
        }
        normalize(vector);
        return vector;
    }

    private static void normalize(float[] vector) {
        double magnitude = 0d;
        for (float value : vector) {
            magnitude += value * value;
        }
        if (magnitude == 0d) {
            vector[0] = 1.0f;
            return;
        }
        float scale = (float) (1d / Math.sqrt(magnitude));
        for (int i = 0; i < vector.length; i++) {
            vector[i] *= scale;
        }
    }
}
