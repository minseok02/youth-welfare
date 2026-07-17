package com.example.welfare.recommend.dto;

import java.util.List;

public record RecommendationRefreshAsyncResponse(
        List<RecommendationResponse> recommendations,
        RecommendationRefreshStatusResponse status
) {
}
