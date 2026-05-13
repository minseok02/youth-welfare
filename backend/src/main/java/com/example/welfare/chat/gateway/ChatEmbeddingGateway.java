package com.example.welfare.chat.gateway;

import java.util.List;

public interface ChatEmbeddingGateway {

    List<float[]> embedDocuments(List<String> texts);

    float[] embedQuery(String text);
}
