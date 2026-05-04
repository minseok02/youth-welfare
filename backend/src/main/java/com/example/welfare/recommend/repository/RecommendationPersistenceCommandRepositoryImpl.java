package com.example.welfare.recommend.repository;

import com.example.welfare.recommend.entity.UserRecommendation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class RecommendationPersistenceCommandRepositoryImpl implements RecommendationPersistenceCommandRepository {

    private final UserRecommendationRepository userRecommendationRepository;

    @Override
    public List<UserRecommendation> findLatestByUserKey(String userKey) {
        return userRecommendationRepository.findLatestByUserKey(userKey);
    }

    @Override
    public List<UserRecommendation> replaceAllForUser(String userKey, List<UserRecommendation> recommendations) {
        userRecommendationRepository.deleteAllByUserKey(userKey);
        return userRecommendationRepository.saveAll(recommendations);
    }

    @Override
    public void deleteOldUnbookmarked(LocalDateTime before) {
        userRecommendationRepository.deleteOldUnbookmarked(before);
    }
}
