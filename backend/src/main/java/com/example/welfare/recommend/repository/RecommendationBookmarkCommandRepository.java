package com.example.welfare.recommend.repository;

import com.example.welfare.recommend.entity.UserRecommendation;

import java.util.Optional;

public interface RecommendationBookmarkCommandRepository {

    Optional<UserRecommendation> findOwnedRecommendation(Long recommendationId, String userKey);

    Optional<UserRecommendation> findLatestRecommendation(String userKey, Long serviceId);

    long countBookmarked(String userKey);

    UserRecommendation save(UserRecommendation recommendation);
}
