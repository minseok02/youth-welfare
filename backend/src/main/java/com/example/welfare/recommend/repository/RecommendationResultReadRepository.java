package com.example.welfare.recommend.repository;

import com.example.welfare.recommend.entity.UserRecommendation;

import java.time.LocalDateTime;
import java.util.List;

public interface RecommendationResultReadRepository {

    List<UserRecommendation> findLatestRecommendationRows(String userKey);

    List<UserRecommendation> findLatestSavedRecommendations(String userKey);

    List<UserRecommendation> findSavedRecommendationsForBatch(String userKey, LocalDateTime recommendedAt);

    List<UserRecommendation> findTopRecommendations(String userKey, int size);
}
