package com.example.welfare.chat.gateway;

import java.util.List;

public interface ChatEmbeddingGateway {

    List<float[]> embedDocuments(List<String> texts);

    default List<float[]> embedDocumentsStrict(List<String> texts) {
        return embedDocuments(texts);
    }

    float[] embedQuery(String text);
}
