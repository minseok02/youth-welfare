package com.example.welfare.recommend.repository;

import com.example.welfare.recommend.entity.RecommendationLog;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class RecommendationLogCommandRepositoryImpl implements RecommendationLogCommandRepository {

    private final RecommendationLogRepository recommendationLogRepository;

    @Override
    public void deleteUnclickedByUserKey(String userKey) {
        recommendationLogRepository.deleteUnclickedByUserKey(userKey);
    }

    @Override
    public List<RecommendationLog> saveAll(List<RecommendationLog> logs) {
        return recommendationLogRepository.saveAll(logs);
    }

    @Override
    public Optional<RecommendationLog> findById(Long logId) {
        return recommendationLogRepository.findById(logId);
    }
}
