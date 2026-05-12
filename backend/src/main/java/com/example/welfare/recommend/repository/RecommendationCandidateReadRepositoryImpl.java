package com.example.welfare.recommend.repository;

import com.example.welfare.policy.entity.ServiceTag;
import com.example.welfare.policy.repository.PolicyTagReadRepository;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.service.PolicyExplorationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class RecommendationCandidateReadRepositoryImpl implements RecommendationCandidateReadRepository {

    private final PolicyExplorationService policyExplorationService;
    private final PolicyTagReadRepository policyTagReadRepository;

    @Override
    public List<WelfareService> findBaseCandidates(RecommendationCandidateReadCondition condition) {
        return policyExplorationService.findRecommendationBaseCandidates(condition);
    }

    @Override
    public List<WelfareService> findLatestCandidates(RecommendationCandidateReadCondition condition) {
        return policyExplorationService.findRecommendationLatestCandidates(condition);
    }

    @Override
    public Map<Long, List<ServiceTag>> findTagsByServiceIds(List<Long> serviceIds) {
        return policyTagReadRepository.findByServiceIds(serviceIds);
    }
}
