package com.example.welfare.recommend.service;

import com.example.welfare.recommend.entity.UserRecommendation;
import com.example.welfare.recommend.repository.RecommendationResultReadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RecommendationResultReadService {

    private final RecommendationResultReadRepository recommendationResultReadRepository;

    @Transactional(readOnly = true)
    public List<UserRecommendation> findLatestSavedRecommendations(String userKey) {
        return recommendationResultReadRepository.findLatestSavedRecommendations(userKey);
    }

    @Transactional(readOnly = true)
    public List<UserRecommendation> findSavedRecommendationsForBatch(String userKey, LocalDateTime recommendedAt) {
        return recommendationResultReadRepository.findSavedRecommendationsForBatch(userKey, recommendedAt);
    }

    @Transactional(readOnly = true)
    public List<UserRecommendation> findTopRecommendations(String userKey, int size) {
        return recommendationResultReadRepository.findTopRecommendations(userKey, size);
    }
}
