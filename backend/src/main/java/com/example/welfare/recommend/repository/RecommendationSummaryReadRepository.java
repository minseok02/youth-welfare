package com.example.welfare.recommend.repository;

import com.example.welfare.recommend.dto.RecommendationCandidateProjection;
import com.example.welfare.recommend.entity.UserRecommendation;

import java.util.List;
import java.util.Map;

public interface RecommendationSummaryReadRepository {

    List<Long> findLatestBookmarkedServiceIds(String userKey, List<Long> serviceIds);

    Map<Long, RecommendationCandidateProjection> findCandidateProjections(List<Long> serviceIds);

    Map<Long, RecommendationCandidateProjection> findSummaryProjections(List<Long> serviceIds);

    List<UserRecommendation> findLatestBookmarkedRecommendations(String userKey);
}
