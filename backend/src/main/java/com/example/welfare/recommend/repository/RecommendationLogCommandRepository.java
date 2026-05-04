package com.example.welfare.recommend.repository;

import com.example.welfare.recommend.entity.RecommendationLog;

import java.util.List;
import java.util.Optional;

public interface RecommendationLogCommandRepository {

    void deleteUnclickedByUserKey(String userKey);

    List<RecommendationLog> saveAll(List<RecommendationLog> logs);

    Optional<RecommendationLog> findById(Long logId);
}
