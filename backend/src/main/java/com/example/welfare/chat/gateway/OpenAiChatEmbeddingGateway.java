package com.example.welfare.chat.gateway;

import com.example.welfare.global.util.SearchKeywordSupport;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class OpenAiChatEmbeddingGateway implements ChatEmbeddingGateway {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${openai.api-key:}")
    private String apiKey;

    @Value("${openai.embedding.model:text-embedding-3-small}")
    private String embeddingModel;

    @Value("${openai.embedding.dimensions:256}")
    private int embeddingDimensions;

    @Value("${openai.embedding.timeout:30000}")
    private long timeoutMillis;

    @Value("${openai.embedding.force-local-fallback:false}")
    private boolean forceLocalFallback;

    @Override
    public List<float[]> embedDocuments(List<String> texts) {
        return embedDocumentsWithFallbackMode(texts, true);
    }

    @Override
    public List<float[]> embedDocumentsStrict(List<String> texts) {
        return embedDocumentsWithFallbackMode(texts, false);
    }

    private List<float[]> embedDocumentsWithFallbackMode(List<String> texts, boolean allowLocalFallback) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        List<String> normalizedTexts = texts.stream()
                .map(text -> text == null ? "" : text.trim())
                .toList();

        if (shouldUseLocalFallback()) {
            if (!allowLocalFallback) {
                throw new IllegalStateException("OpenAI embeddings are unavailable for strict embedding refresh");
            }
            return normalizedTexts.stream()
                    .map(this::createLocalEmbedding)
                    .toList();
        }

        try {
            return requestEmbeddings(normalizedTexts);
        } catch (Exception e) {
            if (!allowLocalFallback) {
                throw new IllegalStateException("OpenAI embedding request failed during strict embedding refresh", e);
            }
            log.warn("[OpenAiChatEmbeddingGateway] 임베딩 호출 실패, local fallback 사용 errorType={}",
                    e.getClass().getSimpleName());
            return normalizedTexts.stream()
                    .map(this::createLocalEmbedding)
                    .toList();
        }
    }

    @Override
    public float[] embedQuery(String text) {
        List<float[]> embeddings = embedDocuments(List.of(text));
        return embeddings.isEmpty() ? createLocalEmbedding(text) : embeddings.get(0);
    }

    private boolean shouldUseLocalFallback() {
        return forceLocalFallback || !StringUtils.hasText(apiKey);
    }

    private List<float[]> requestEmbeddings(List<String> texts) throws JsonProcessingException {
        Map<String, Object> requestBody = Map.of(
                "model", embeddingModel,
                "input", texts,
                "dimensions", embeddingDimensions
        );

        String responseBody = webClient.post()
                .uri("https://api.openai.com/v1/embeddings")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .block(Duration.ofMillis(timeoutMillis));

        EmbeddingResponse response = objectMapper.readValue(responseBody, EmbeddingResponse.class);
        if (response.getData() == null || response.getData().isEmpty()) {
            throw new IllegalStateException("embedding data is empty");
        }

        List<EmbeddingItem> sortedItems = response.getData().stream()
                .sorted(Comparator.comparingInt(EmbeddingItem::getIndex))
                .toList();
        List<float[]> embeddings = new ArrayList<>(sortedItems.size());
        for (EmbeddingItem item : sortedItems) {
            embeddings.add(toFloatArray(item.getEmbedding()));
        }
        return embeddings;
    }

    private float[] createLocalEmbedding(String text) {
        List<String> tokens = SearchKeywordSupport.extractTokens(text);
        if (tokens.isEmpty() && StringUtils.hasText(text)) {
            tokens = List.of(text.trim().toLowerCase(java.util.Locale.ROOT));
        }
        float[] vector = new float[embeddingDimensions];
        if (tokens.isEmpty()) {
            vector[0] = 1.0f;
            return vector;
        }

        for (String token : tokens) {
            int baseHash = token.hashCode();
            int secondaryHash = (token + "#").hashCode();
            int positiveIndex = Math.floorMod(baseHash, embeddingDimensions);
            int signedIndex = Math.floorMod(secondaryHash, embeddingDimensions);
            vector[positiveIndex] += 1.0f;
            vector[signedIndex] += (secondaryHash & 1) == 0 ? 0.5f : -0.5f;
        }
        normalize(vector);
        return vector;
    }

    private float[] toFloatArray(List<Double> values) {
        if (values == null || values.isEmpty()) {
            throw new IllegalStateException("embedding vector is empty");
        }
        float[] result = new float[values.size()];
        for (int i = 0; i < values.size(); i++) {
            result[i] = values.get(i).floatValue();
        }
        return result;
    }

    private void normalize(float[] vector) {
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

    @Getter
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class EmbeddingResponse {
        private List<EmbeddingItem> data;

        @JsonProperty("data")
        public void setData(List<EmbeddingItem> data) {
            this.data = data;
        }
    }

    @Getter
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class EmbeddingItem {
        private int index;
        private List<Double> embedding;

        @JsonProperty("index")
        public void setIndex(int index) {
            this.index = index;
        }

        @JsonProperty("embedding")
        public void setEmbedding(List<Double> embedding) {
            this.embedding = embedding;
        }
    }
}
