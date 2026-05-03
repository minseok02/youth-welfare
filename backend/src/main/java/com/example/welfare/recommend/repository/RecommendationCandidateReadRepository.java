package com.example.welfare.recommend.repository;

import com.example.welfare.policy.entity.WelfareService;

import java.util.List;

public interface RecommendationCandidateReadRepository {

    List<WelfareService> findBaseCandidates(RecommendationCandidateReadCondition condition);

    List<WelfareService> findLatestCandidates(RecommendationCandidateReadCondition condition);
}
