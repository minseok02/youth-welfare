package com.example.welfare.recommend.repository;

import com.example.welfare.recommend.service.RecommendationRunLogCommand;

import java.time.LocalDateTime;

public interface RecommendationRunLogCommandRepository {

    void save(RecommendationRunLogCommand command);

    int deleteOlderThan(LocalDateTime before);
}
