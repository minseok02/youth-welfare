package com.example.welfare.recommend.repository;

import com.example.welfare.recommend.entity.UserRecommendation;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class RecommendationResultReadRepositoryImpl implements RecommendationResultReadRepository {

    private final UserRecommendationRepository userRecommendationRepository;

    @Override
    public List<UserRecommendation> findLatestRecommendationRows(String userKey) {
        return userRecommendationRepository.findLatestByUserKey(userKey);
    }

    @Override
    public List<UserRecommendation> findLatestSavedRecommendations(String userKey) {
        return userRecommendationRepository.findLatestBatchByUserKeyOrderByFinalScoreDesc(userKey);
    }

    @Override
    public List<UserRecommendation> findSavedRecommendationsForBatch(String userKey, java.time.LocalDateTime recommendedAt) {
        return userRecommendationRepository.findByUserKeyAndRecommendedAtOrderByFinalScoreDesc(userKey, recommendedAt);
    }

    @Override
    public List<UserRecommendation> findTopRecommendations(String userKey, int size) {
        return userRecommendationRepository.findTopByUserKey(userKey, PageRequest.of(0, size));
    }
}
