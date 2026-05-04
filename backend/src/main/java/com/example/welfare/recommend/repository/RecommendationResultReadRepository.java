package com.example.welfare.recommend.repository;

import com.example.welfare.recommend.entity.UserRecommendation;

import java.util.List;

public interface RecommendationResultReadRepository {

    List<UserRecommendation> findLatestSavedRecommendations(String userKey);

    List<UserRecommendation> findTopRecommendations(String userKey, int size);
}
