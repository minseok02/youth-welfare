package com.example.welfare.recommend.repository;

import com.example.welfare.policy.entity.ServiceTag;
import com.example.welfare.policy.entity.WelfareService;

import java.util.List;
import java.util.Map;

public interface RecommendationCandidateReadRepository {

    List<WelfareService> findBaseCandidates(RecommendationCandidateReadCondition condition);

    List<WelfareService> findLatestCandidates(RecommendationCandidateReadCondition condition);

    Map<Long, List<ServiceTag>> findTagsByServiceIds(List<Long> serviceIds);
}
