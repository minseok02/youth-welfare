package com.example.welfare.recommend.repository;

import com.example.welfare.recommend.entity.UserRecommendation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class RecommendationBookmarkCommandRepositoryImpl implements RecommendationBookmarkCommandRepository {

    private final UserRecommendationRepository userRecommendationRepository;

    @Override
    public Optional<UserRecommendation> findOwnedRecommendation(Long recommendationId, String userKey) {
        return userRecommendationRepository.findByIdAndUserKey(recommendationId, userKey);
    }

    @Override
    public Optional<UserRecommendation> findLatestRecommendation(String userKey, Long serviceId) {
        return userRecommendationRepository.findTopByUserKeyAndServiceIdOrderByRecommendedAtDesc(userKey, serviceId);
    }

    @Override
    public long countBookmarked(String userKey) {
        return userRecommendationRepository.countByUserKeyAndIsBookmarkedTrue(userKey);
    }

    @Override
    public UserRecommendation save(UserRecommendation recommendation) {
        return userRecommendationRepository.save(recommendation);
    }
}
