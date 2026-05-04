package com.example.welfare.recommend.repository;

import com.example.welfare.recommend.entity.RecommendationLog;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class RecommendationLogReadRepositoryImpl implements RecommendationLogReadRepository {

    private final RecommendationLogRepository recommendationLogRepository;

    @Override
    public long countAll() {
        return recommendationLogRepository.count();
    }

    @Override
    public List<RecommendationLog> findLatestByUserKeyAndServiceIds(String userKey, List<Long> serviceIds) {
        return recommendationLogRepository.findLatestByUserKeyAndServiceIds(userKey, serviceIds);
    }
}
