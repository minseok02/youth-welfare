package com.example.welfare.recommend.repository;

import com.example.welfare.recommend.dto.RecommendationCandidateProjection;
import com.example.welfare.recommend.entity.UserRecommendation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class RecommendationSummaryReadRepositoryImpl implements RecommendationSummaryReadRepository {

    private final UserRecommendationRepository userRecommendationRepository;
    private final CanonicalRecommendationReadModelRepository canonicalRecommendationReadModelRepository;

    @Override
    public List<Long> findLatestBookmarkedServiceIds(String userKey, List<Long> serviceIds) {
        return userRecommendationRepository.findLatestBookmarkedServiceIdsByUserKey(userKey, serviceIds);
    }

    @Override
    public Map<Long, RecommendationCandidateProjection> findCandidateProjections(List<Long> serviceIds) {
        return canonicalRecommendationReadModelRepository.findByServiceIds(serviceIds);
    }

    @Override
    public Map<Long, RecommendationCandidateProjection> findSummaryProjections(List<Long> serviceIds) {
        return canonicalRecommendationReadModelRepository.findSummaryByServiceIds(serviceIds);
    }

    @Override
    public List<UserRecommendation> findLatestBookmarkedRecommendations(String userKey) {
        return userRecommendationRepository.findLatestBookmarkedByUserKey(userKey);
    }
}
