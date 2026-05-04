package com.example.welfare.recommend.repository;

import com.example.welfare.recommend.entity.UserRecommendation;

import java.time.LocalDateTime;
import java.util.List;

public interface RecommendationPersistenceCommandRepository {

    List<UserRecommendation> replaceAllForUser(String userKey, List<UserRecommendation> recommendations);

    void deleteOldUnbookmarked(LocalDateTime before);
}
