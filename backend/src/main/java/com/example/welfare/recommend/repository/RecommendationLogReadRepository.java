package com.example.welfare.recommend.repository;

import com.example.welfare.recommend.entity.RecommendationLog;

import java.util.List;

public interface RecommendationLogReadRepository {

    long countAll();

    List<RecommendationLog> findLatestByUserKeyAndServiceIds(String userKey, List<Long> serviceIds);
}
